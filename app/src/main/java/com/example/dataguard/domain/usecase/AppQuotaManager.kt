package com.example.dataguard.domain.usecase

import android.content.Context
import com.example.dataguard.data.entity.AppLimitEntity
import com.example.dataguard.data.entity.QuotaBreachEventEntity
import com.example.dataguard.data.entity.QuotaEnforcementMode
import com.example.dataguard.data.repository.DataRepository
import com.example.dataguard.domain.model.AppQuotaCalculation
import com.example.dataguard.domain.model.AppQuotaCycleCalculator
import com.example.dataguard.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * Manages per-application data quota enforcement, cycle evaluation, threshold notifications,
 * event recording, and firewall reconciliation.
 */
class AppQuotaManager(
    private val context: Context,
    private val repository: DataRepository,
    private val networkStatsHelper: NetworkStatsHelper,
    private val blockAppDataUseCase: BlockAppDataUseCase,
    private val notificationHelper: NotificationHelper
) {

    /**
     * Evaluates quota status for a given app without triggering external side effects.
     */
    fun evaluateQuota(
        limit: AppLimitEntity,
        now: Long = System.currentTimeMillis()
    ): AppQuotaCalculation {
        val (cycleStart, cycleEnd) = AppQuotaCycleCalculator.calculateCycleWindow(limit, now)
        val usedBytes = networkStatsHelper.queryUidBytes(limit.uid, cycleStart, now)
        val remainingBytes = maxOf(0L, limit.limitBytes - usedBytes)
        val percentage = if (limit.limitBytes > 0) {
            (usedBytes.toFloat() / limit.limitBytes.toFloat() * 100f)
        } else {
            0f
        }
        val isWarningReached = limit.isEnabled && limit.limitBytes > 0 && percentage >= limit.warningThresholdPercent
        val isLimitReached = limit.isEnabled && limit.limitBytes > 0 && usedBytes >= limit.limitBytes
        val resetDateFormatted = AppQuotaCycleCalculator.formatResetDate(cycleEnd, now)
        val cycleDescription = AppQuotaCycleCalculator.getCycleDescription(limit)

        return AppQuotaCalculation(
            limit = limit,
            cycleStartTime = cycleStart,
            cycleEndTime = cycleEnd,
            usedBytes = usedBytes,
            remainingBytes = remainingBytes,
            percentage = percentage,
            isWarningReached = isWarningReached,
            isLimitReached = isLimitReached,
            resetDateFormatted = resetDateFormatted,
            cycleDescription = cycleDescription
        )
    }

    /**
     * Evaluates the quota and executes threshold warnings, breach recording, and firewall blocks.
     */
    suspend fun checkAndEnforceAppLimit(
        limit: AppLimitEntity,
        now: Long = System.currentTimeMillis()
    ): AppQuotaCalculation = withContext(Dispatchers.IO) {
        val calculation = evaluateQuota(limit, now)

        if (!limit.isEnabled) {
            if (limit.isLimitReached) {
                repository.updateAppLimit(limit.copy(isLimitReached = false))
                // If it was blocked due to quota, unblock it
                blockAppDataUseCase(limit.uid, limit.packageName, limit.appName, block = false)
            }
            return@withContext calculation
        }

        // Automatic cycle reset check: if a new cycle started, reset limit reached state
        if (limit.isLimitReached && calculation.usedBytes < limit.limitBytes) {
            repository.updateAppLimit(limit.copy(isLimitReached = false))
            if (limit.enforcementMode == QuotaEnforcementMode.BLOCK_AT_LIMIT.name) {
                blockAppDataUseCase(limit.uid, limit.packageName, limit.appName, block = false)
            }
        }

        if (calculation.isLimitReached) {
            val shouldBlock = limit.enforcementMode == QuotaEnforcementMode.BLOCK_AT_LIMIT.name
            if (shouldBlock) {
                // Prepare firewall layer to enforce the block
                blockAppDataUseCase(limit.uid, limit.packageName, limit.appName, block = true)
            }

            // Notify and record event only once per cycle
            val notYetNotifiedThisCycle = limit.lastLimitNotifiedAt < calculation.cycleStartTime
            if (notYetNotifiedThisCycle || !limit.isLimitReached) {
                repository.insertQuotaBreachEvent(
                    QuotaBreachEventEntity(
                        uid = limit.uid,
                        packageName = limit.packageName,
                        appName = limit.appName,
                        eventType = "LIMIT_REACHED",
                        usedBytes = calculation.usedBytes,
                        limitBytes = limit.limitBytes,
                        enforcementAction = if (shouldBlock) "BLOCKED" else "WARN_ONLY",
                        timestamp = now
                    )
                )

                notificationHelper.showAppQuotaExceededNotification(
                    appName = limit.appName.ifBlank { limit.packageName },
                    packageName = limit.packageName,
                    usedBytes = calculation.usedBytes,
                    limitBytes = limit.limitBytes,
                    actionTaken = if (shouldBlock) "Blocked by Firewall" else "Warning Alert"
                )

                val updatedLimit = limit.copy(
                    isLimitReached = true,
                    lastLimitNotifiedAt = now
                )
                repository.updateAppLimit(updatedLimit)
                return@withContext calculation.copy(limit = updatedLimit, isLimitReached = true)
            }
        } else if (calculation.isWarningReached) {
            val notYetWarnedThisCycle = limit.lastWarningNotifiedAt < calculation.cycleStartTime
            if (notYetWarnedThisCycle) {
                repository.insertQuotaBreachEvent(
                    QuotaBreachEventEntity(
                        uid = limit.uid,
                        packageName = limit.packageName,
                        appName = limit.appName,
                        eventType = "WARNING",
                        usedBytes = calculation.usedBytes,
                        limitBytes = limit.limitBytes,
                        enforcementAction = limit.enforcementMode,
                        timestamp = now
                    )
                )

                notificationHelper.showAppQuotaWarningNotification(
                    appName = limit.appName.ifBlank { limit.packageName },
                    packageName = limit.packageName,
                    usedBytes = calculation.usedBytes,
                    limitBytes = limit.limitBytes,
                    thresholdPercent = limit.warningThresholdPercent
                )

                val updatedLimit = limit.copy(lastWarningNotifiedAt = now)
                repository.updateAppLimit(updatedLimit)
                return@withContext calculation.copy(limit = updatedLimit)
            }
        }

        calculation
    }

    /**
     * Checks all configured app limits across the device.
     */
    suspend fun checkAndEnforceAllLimits(): List<AppQuotaCalculation> = withContext(Dispatchers.IO) {
        val limits = repository.getAllAppLimits().firstOrNull() ?: emptyList()
        val results = mutableListOf<AppQuotaCalculation>()
        val now = System.currentTimeMillis()

        for (limit in limits) {
            if (limit.isEnabled && limit.limitBytes > 0) {
                val calc = checkAndEnforceAppLimit(limit, now)
                results.add(calc)
            }
        }
        results
    }

    /**
     * Resets the current billing/quota cycle for a specific app to the current moment,
     * resetting breach flags and unblocking if necessary.
     */
    suspend fun resetCycleUsage(uid: Int) = withContext(Dispatchers.IO) {
        val limit = repository.getAppLimit(uid) ?: return@withContext
        val now = System.currentTimeMillis()

        val updated = limit.copy(
            cycleStartTimestamp = now,
            isLimitReached = false,
            lastWarningNotifiedAt = 0L,
            lastLimitNotifiedAt = 0L
        )
        repository.updateAppLimit(updated)

        // If it was blocked by firewall due to limit reached, unblock it
        blockAppDataUseCase(limit.uid, limit.packageName, limit.appName, block = false)
    }

    /**
     * Saves or updates a quota and executes immediate evaluation.
     */
    suspend fun saveOrUpdateLimit(limit: AppLimitEntity): AppQuotaCalculation = withContext(Dispatchers.IO) {
        repository.insertAppLimit(limit)
        checkAndEnforceAppLimit(limit)
    }

    /**
     * Deletes a quota and unblocks app if previously blocked by quota.
     */
    suspend fun deleteLimit(uid: Int, packageName: String, appName: String) = withContext(Dispatchers.IO) {
        repository.deleteAppLimitByUid(uid)
        blockAppDataUseCase(uid, packageName, appName, block = false)
    }
}
