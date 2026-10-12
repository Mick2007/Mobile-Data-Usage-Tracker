package com.example.dataguard.domain.plan

import android.content.Context
import com.example.dataguard.data.entity.DataPlanEntity
import com.example.dataguard.data.entity.NotificationAlertCategory
import com.example.dataguard.data.entity.NotificationHistoryEntity
import com.example.dataguard.data.entity.NotificationPreferencesEntity
import com.example.dataguard.data.entity.PlanTrafficScope
import com.example.dataguard.data.repository.DataRepository
import com.example.dataguard.domain.model.TimeRangeSelection
import com.example.dataguard.domain.usecase.NetworkStatsHelper
import com.example.dataguard.utils.DataFormatter
import com.example.dataguard.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates evaluation of the carrier data plan, alerting engine (50%, 75%, 90%, 100%),
 * anomaly & spike detection, newly observed traffic, and anti-spam notification dispatch.
 */
class DataPlanAlertManager(
    private val context: Context,
    private val repository: DataRepository,
    private val networkStatsHelper: NetworkStatsHelper,
    private val notificationHelper: NotificationHelper
) {

    // In-memory throttling map to prevent repetitive spam: alertKey -> lastNotifiedTimestamp
    private val alertThrottleMap = ConcurrentHashMap<String, Long>()

    // Set of seen apps & domains to detect newly observed traffic
    private val knownAppPackages = ConcurrentHashMap.newKeySet<String>()
    private val knownDomains = ConcurrentHashMap.newKeySet<String>()

    /**
     * Evaluates data plan status and triggers threshold alerts if configured.
     */
    suspend fun evaluatePlanAndAlerts(now: Long = System.currentTimeMillis()): DataPlanStatus = withContext(Dispatchers.IO) {
        val plan = repository.getDataPlan() ?: DataPlanEntity().also {
            repository.saveOrUpdateDataPlan(it)
        }
        val prefs = repository.getNotificationPreferences() ?: NotificationPreferencesEntity().also {
            repository.saveNotificationPreferences(it)
        }

        val cycleWindow = DataPlanCycleCalculator.calculateCycleWindow(plan, now)

        // Query telemetry in the cycle window
        val telemetry = networkStatsHelper.queryNetworkTelemetry(
            TimeRangeSelection(
                startTime = cycleWindow.cycleStartTime,
                endTime = now,
                type = com.example.dataguard.domain.model.TimeRangeType.CUSTOM
            )
        )

        val excluded = plan.excludedPackagesList
        val appList = telemetry.appList.filter { it.packageName !in excluded }

        val usedBytes = if (plan.trafficScope == PlanTrafficScope.MOBILE_ONLY.name) {
            if (excluded.isNotEmpty()) appList.sumOf { it.mobileTotalBytes } else telemetry.totalMobileBytes
        } else {
            if (excluded.isNotEmpty()) appList.sumOf { it.totalBytes } else telemetry.totalBytes
        }

        val totalEffective = plan.totalEffectiveAllowanceBytes
        val remainingBytes = maxOf(0L, totalEffective - usedBytes)
        val usagePercentage = if (totalEffective > 0) {
            (usedBytes.toFloat() / totalEffective.toFloat() * 100f).coerceIn(0f, 100f)
        } else 0f
        val isExceeded = totalEffective > 0 && usedBytes >= totalEffective

        // Detect threshold reached (50, 75, 90, 100)
        val thresholds = plan.warningThresholdsList
        var reachedThreshold = 0
        for (th in thresholds) {
            if (usagePercentage >= th) {
                reachedThreshold = th
            }
        }

        // Check if new threshold breached in this cycle and preferences permit
        if (prefs.isEnabledMaster && reachedThreshold > plan.lastNotifiedThreshold) {
            val shouldAlert = when (reachedThreshold) {
                50 -> prefs.alertOn50Percent
                75 -> prefs.alertOn75Percent
                90 -> prefs.alertOn90Percent
                100 -> prefs.alertOn100Percent
                else -> true
            }

            if (shouldAlert) {
                val throttleKey = "plan_threshold_$reachedThreshold"
                if (canSendNotification(throttleKey, prefs.minAlertIntervalMinutes, now)) {
                    notificationHelper.showPlanThresholdAlert(
                        planName = plan.planName,
                        thresholdPercent = reachedThreshold,
                        usedBytes = usedBytes,
                        totalBytes = totalEffective
                    )
                    repository.insertNotificationHistory(
                        NotificationHistoryEntity(
                            title = "Plan Alert: ${plan.planName} at $reachedThreshold%",
                            message = "Consumed ${DataFormatter.formatBytes(usedBytes)} of ${DataFormatter.formatBytes(totalEffective)} ($reachedThreshold%)",
                            category = NotificationAlertCategory.PLAN_THRESHOLD.name,
                            targetKey = "plan",
                            bytesObserved = usedBytes,
                            thresholdPercent = reachedThreshold,
                            actionTaken = if (reachedThreshold == 100) "Limit Exceeded" else "Threshold Notice",
                            timestamp = now
                        )
                    )
                    recordNotificationSent(throttleKey, now)
                }
            }

            // Update plan state
            repository.saveOrUpdateDataPlan(plan.copy(lastNotifiedThreshold = reachedThreshold))
        }

        DataPlanStatus(
            plan = plan,
            cycleWindow = cycleWindow,
            usedBytes = usedBytes,
            remainingBytes = remainingBytes,
            usagePercentage = usagePercentage,
            isLimitExceeded = isExceeded,
            currentThresholdReached = reachedThreshold,
            activeExcludedApps = excluded
        )
    }

    /**
     * Checks if an app exceeds the configured X MB threshold.
     */
    suspend fun checkAppExceedsMb(appName: String, packageName: String, bytesUsed: Long, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val prefs = repository.getNotificationPreferences() ?: return@withContext
        if (!prefs.isEnabledMaster || !prefs.alertAppExceedsMb) return@withContext

        val thresholdBytes = prefs.appExceedsThresholdMb * 1024L * 1024L
        if (bytesUsed >= thresholdBytes) {
            val throttleKey = "app_exceeds_$packageName"
            if (canSendNotification(throttleKey, prefs.minAlertIntervalMinutes, now)) {
                notificationHelper.showAppExceedsMbAlert(appName, packageName, bytesUsed, prefs.appExceedsThresholdMb)
                repository.insertNotificationHistory(
                    NotificationHistoryEntity(
                        title = "High Usage: $appName",
                        message = "$appName consumed ${DataFormatter.formatBytes(bytesUsed)}, exceeding ${prefs.appExceedsThresholdMb} MB limit",
                        category = NotificationAlertCategory.APP_EXCEEDS_QUOTA.name,
                        targetKey = packageName,
                        bytesObserved = bytesUsed,
                        actionTaken = "Usage Alert",
                        timestamp = now
                    )
                )
                recordNotificationSent(throttleKey, now)
            }
        }
    }

    /**
     * Checks if a domain exceeds the configured X MB threshold.
     */
    suspend fun checkDomainExceedsMb(domain: String, bytesUsed: Long, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val prefs = repository.getNotificationPreferences() ?: return@withContext
        if (!prefs.isEnabledMaster || !prefs.alertDomainExceedsMb) return@withContext

        val thresholdBytes = prefs.domainExceedsThresholdMb * 1024L * 1024L
        if (bytesUsed >= thresholdBytes) {
            val throttleKey = "domain_exceeds_$domain"
            if (canSendNotification(throttleKey, prefs.minAlertIntervalMinutes, now)) {
                notificationHelper.showDomainExceedsMbAlert(domain, bytesUsed, prefs.domainExceedsThresholdMb)
                repository.insertNotificationHistory(
                    NotificationHistoryEntity(
                        title = "High Domain Traffic: $domain",
                        message = "Domain $domain transferred ${DataFormatter.formatBytes(bytesUsed)} (exceeds ${prefs.domainExceedsThresholdMb} MB)",
                        category = NotificationAlertCategory.DOMAIN_EXCEEDS_QUOTA.name,
                        targetKey = domain,
                        bytesObserved = bytesUsed,
                        actionTaken = "Domain Traffic Alert",
                        timestamp = now
                    )
                )
                recordNotificationSent(throttleKey, now)
            }
        }
    }

    /**
     * Checks if tethering/hotspot exceeds the configured X MB threshold.
     */
    suspend fun checkHotspotExceedsMb(totalBytes: Long, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val prefs = repository.getNotificationPreferences() ?: return@withContext
        if (!prefs.isEnabledMaster || !prefs.alertHotspotExceedsMb) return@withContext

        val thresholdBytes = prefs.hotspotExceedsThresholdMb * 1024L * 1024L
        if (totalBytes >= thresholdBytes) {
            val throttleKey = "hotspot_exceeds"
            if (canSendNotification(throttleKey, prefs.minAlertIntervalMinutes, now)) {
                notificationHelper.showHotspotExceedsMbAlert(totalBytes, prefs.hotspotExceedsThresholdMb)
                repository.insertNotificationHistory(
                    NotificationHistoryEntity(
                        title = "Hotspot High Usage",
                        message = "Tethering transferred ${DataFormatter.formatBytes(totalBytes)} (limit: ${prefs.hotspotExceedsThresholdMb} MB)",
                        category = NotificationAlertCategory.HOTSPOT_EXCEEDS_QUOTA.name,
                        targetKey = "hotspot",
                        bytesObserved = totalBytes,
                        actionTaken = "Tethering Alert",
                        timestamp = now
                    )
                )
                recordNotificationSent(throttleKey, now)
            }
        }
    }

    /**
     * Checks for unusual spikes in network transmission.
     */
    suspend fun checkDataSpike(speedBytesPerSec: Long, appName: String? = null, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val prefs = repository.getNotificationPreferences() ?: return@withContext
        if (!prefs.isEnabledMaster || !prefs.alertUnusualSpike) return@withContext

        val speedKbps = (speedBytesPerSec * 8L) / 1024L
        if (speedKbps >= prefs.spikeSpeedThresholdKbps) {
            val throttleKey = "data_spike"
            if (canSendNotification(throttleKey, prefs.minAlertIntervalMinutes, now)) {
                notificationHelper.showDataSpikeAlert(speedKbps, appName)
                repository.insertNotificationHistory(
                    NotificationHistoryEntity(
                        title = "Unusual Network Spike",
                        message = "High throughput detected: ${String.format(java.util.Locale.US, "%.1f MB/s", speedKbps / 1024.0)}",
                        category = NotificationAlertCategory.DATA_SPIKE.name,
                        targetKey = appName ?: "network",
                        bytesObserved = speedBytesPerSec,
                        actionTaken = "Spike Notice",
                        timestamp = now
                    )
                )
                recordNotificationSent(throttleKey, now)
            }
        }
    }

    /**
     * Alerts when newly observed application network activity is seen.
     */
    suspend fun notifyNewAppObserved(appName: String, packageName: String, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        if (!knownAppPackages.add(packageName)) return@withContext // already known
        val prefs = repository.getNotificationPreferences() ?: return@withContext
        if (!prefs.isEnabledMaster || !prefs.alertNewlyObservedApp) return@withContext

        val throttleKey = "new_app_$packageName"
        if (canSendNotification(throttleKey, prefs.minAlertIntervalMinutes, now)) {
            notificationHelper.showNewAppObservedAlert(appName, packageName)
            repository.insertNotificationHistory(
                NotificationHistoryEntity(
                    title = "New App Activity: $appName",
                    message = "First network communication observed from $appName ($packageName)",
                    category = NotificationAlertCategory.NEW_APP_NETWORK_ACTIVITY.name,
                    targetKey = packageName,
                    actionTaken = "Observed",
                    timestamp = now
                )
            )
            recordNotificationSent(throttleKey, now)
        }
    }

    /**
     * Alerts when newly observed domain is contacted.
     */
    suspend fun notifyNewDomainObserved(domain: String, appName: String, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        if (!knownDomains.add(domain)) return@withContext // already known
        val prefs = repository.getNotificationPreferences() ?: return@withContext
        if (!prefs.isEnabledMaster || !prefs.alertNewlyObservedDomain) return@withContext

        val throttleKey = "new_domain_$domain"
        if (canSendNotification(throttleKey, prefs.minAlertIntervalMinutes, now)) {
            notificationHelper.showNewDomainObservedAlert(domain, appName)
            repository.insertNotificationHistory(
                NotificationHistoryEntity(
                    title = "New Host: $domain",
                    message = "$appName connected to previously unvisited host $domain",
                    category = NotificationAlertCategory.NEW_DOMAIN_CONTACTED.name,
                    targetKey = domain,
                    actionTaken = "Host Observed",
                    timestamp = now
                )
            )
            recordNotificationSent(throttleKey, now)
        }
    }

    /**
     * Alerts on firewall block event.
     */
    suspend fun recordFirewallBlock(appName: String, targetDomainOrReason: String, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val prefs = repository.getNotificationPreferences() ?: return@withContext
        if (!prefs.isEnabledMaster || !prefs.alertFirewallBlockEvent) return@withContext

        val throttleKey = "firewall_block_${appName}_$targetDomainOrReason"
        if (canSendNotification(throttleKey, 5, now)) { // 5-minute throttle for same blocked target
            notificationHelper.showFirewallBlockAlert(appName, targetDomainOrReason)
            repository.insertNotificationHistory(
                NotificationHistoryEntity(
                    title = "Firewall Block: $appName",
                    message = "Blocked connection attempt to: $targetDomainOrReason",
                    category = NotificationAlertCategory.FIREWALL_BLOCK.name,
                    targetKey = appName,
                    actionTaken = "BLOCKED",
                    timestamp = now
                )
            )
            recordNotificationSent(throttleKey, now)
        }
    }

    private fun canSendNotification(key: String, throttleMinutes: Int, now: Long): Boolean {
        val lastTime = alertThrottleMap[key] ?: return true
        val throttleMillis = throttleMinutes * 60 * 1000L
        return (now - lastTime) >= throttleMillis
    }

    private fun recordNotificationSent(key: String, now: Long) {
        alertThrottleMap[key] = now
    }
}
