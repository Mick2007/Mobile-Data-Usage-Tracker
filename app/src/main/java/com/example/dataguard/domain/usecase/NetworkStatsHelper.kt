package com.example.dataguard.domain.usecase

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import com.example.dataguard.data.entity.AppDataUsage
import com.example.dataguard.data.entity.AppNetworkUsageRecordEntity
import com.example.dataguard.domain.model.TimeRangeSelection
import com.example.dataguard.utils.PermissionHelper

data class DeviceNetworkTelemetry(
    val mobileTxBytes: Long,
    val mobileRxBytes: Long,
    val wifiTxBytes: Long,
    val wifiRxBytes: Long,
    val appList: List<AppDataUsage>,
    val startTime: Long,
    val endTime: Long,
    val isFromNetworkStatsManager: Boolean
) {
    val totalMobileBytes: Long get() = mobileTxBytes + mobileRxBytes
    val totalWifiBytes: Long get() = wifiTxBytes + wifiRxBytes
    val totalRxBytes: Long get() = mobileRxBytes + wifiRxBytes
    val totalTxBytes: Long get() = mobileTxBytes + wifiTxBytes
    val totalBytes: Long get() = totalMobileBytes + totalWifiBytes
}

class NetworkStatsHelper(
    private val context: Context,
    private val permissionHelper: PermissionHelper
) {
    private val networkStatsManager =
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
    private val packageManager = context.packageManager

    /**
     * Queries accurate historical bandwidth usage via NetworkStatsManager across
     * both Cellular (TYPE_MOBILE) and Wi-Fi (TYPE_WIFI) interfaces.
     *
     * Falls back to TrafficStats if Usage Access permission is not granted.
     */
    fun queryNetworkTelemetry(timeRange: TimeRangeSelection): DeviceNetworkTelemetry {
        val hasPermission = permissionHelper.hasPackageUsageStatsPermission()

        if (hasPermission && networkStatsManager != null) {
            try {
                return queryFromNetworkStatsManager(timeRange.startTime, timeRange.endTime)
            } catch (se: SecurityException) {
                // Permission revoked in settings
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Graceful fallback when NetworkStatsManager is unavailable
        return queryFallbackFromTrafficStats(timeRange.startTime, timeRange.endTime)
    }

    /**
     * Directly queries device-level summary for a specific interface and window.
     * Returns Pair(txBytes, rxBytes).
     */
    fun queryDeviceSummary(networkType: Int, startTime: Long, endTime: Long): Pair<Long, Long> {
        val nsm = networkStatsManager ?: return Pair(0L, 0L)
        if (!permissionHelper.hasPackageUsageStatsPermission()) return Pair(0L, 0L)

        val validStart = minOf(startTime, endTime)
        val validEnd = maxOf(startTime, endTime).let { if (it <= validStart) validStart + 1000L else it }

        return try {
            val bucket = nsm.querySummaryForDevice(networkType, null, validStart, validEnd)
            Pair(bucket.txBytes.coerceAtLeast(0L), bucket.rxBytes.coerceAtLeast(0L))
        } catch (e: Exception) {
            Pair(0L, 0L)
        }
    }

    /**
     * Queries total combined data consumption (mobile + wifi) in bytes for a specific app UID
     * across a given time window for quota evaluation.
     */
    fun queryUidBytes(uid: Int, startTime: Long, endTime: Long): Long {
        val nsm = networkStatsManager
        val hasPermission = permissionHelper.hasPackageUsageStatsPermission()
        if (!hasPermission || nsm == null) {
            val tsTx = android.net.TrafficStats.getUidTxBytes(uid)
            val tsRx = android.net.TrafficStats.getUidRxBytes(uid)
            val total = (if (tsTx > 0) tsTx else 0L) + (if (tsRx > 0) tsRx else 0L)
            return total
        }

        val validStart = minOf(startTime, endTime)
        val validEnd = maxOf(startTime, endTime).let { if (it <= validStart) validStart + 1000L else it }
        var totalBytes = 0L

        // 1. Mobile interface
        try {
            val mobileStats = nsm.queryDetailsForUid(
                ConnectivityManager.TYPE_MOBILE,
                null,
                validStart,
                validEnd,
                uid
            )
            val bucket = NetworkStats.Bucket()
            while (mobileStats.hasNextBucket()) {
                mobileStats.getNextBucket(bucket)
                totalBytes += bucket.txBytes.coerceAtLeast(0L) + bucket.rxBytes.coerceAtLeast(0L)
            }
            mobileStats.close()
        } catch (e: Exception) {
            // Fallback via summary query for devices with restricted queryDetailsForUid
            try {
                val summary = nsm.querySummary(ConnectivityManager.TYPE_MOBILE, null, validStart, validEnd)
                val bucket = NetworkStats.Bucket()
                while (summary.hasNextBucket()) {
                    summary.getNextBucket(bucket)
                    if (bucket.uid == uid) {
                        totalBytes += bucket.txBytes.coerceAtLeast(0L) + bucket.rxBytes.coerceAtLeast(0L)
                    }
                }
                summary.close()
            } catch (ignored: Exception) {}
        }

        // 2. Wi-Fi interface
        try {
            val wifiStats = nsm.queryDetailsForUid(
                ConnectivityManager.TYPE_WIFI,
                null,
                validStart,
                validEnd,
                uid
            )
            val bucket = NetworkStats.Bucket()
            while (wifiStats.hasNextBucket()) {
                wifiStats.getNextBucket(bucket)
                totalBytes += bucket.txBytes.coerceAtLeast(0L) + bucket.rxBytes.coerceAtLeast(0L)
            }
            wifiStats.close()
        } catch (e: Exception) {
            try {
                val summary = nsm.querySummary(ConnectivityManager.TYPE_WIFI, null, validStart, validEnd)
                val bucket = NetworkStats.Bucket()
                while (summary.hasNextBucket()) {
                    summary.getNextBucket(bucket)
                    if (bucket.uid == uid) {
                        totalBytes += bucket.txBytes.coerceAtLeast(0L) + bucket.rxBytes.coerceAtLeast(0L)
                    }
                }
                summary.close()
            } catch (ignored: Exception) {}
        }

        // If query returned 0 and start is within current boot session, fallback to TrafficStats
        if (totalBytes == 0L) {
            val tsTx = android.net.TrafficStats.getUidTxBytes(uid)
            val tsRx = android.net.TrafficStats.getUidRxBytes(uid)
            if (tsTx > 0 || tsRx > 0) {
                totalBytes = tsTx.coerceAtLeast(0L) + tsRx.coerceAtLeast(0L)
            }
        }

        return totalBytes
    }

    private fun queryFromNetworkStatsManager(startTime: Long, endTime: Long): DeviceNetworkTelemetry {
        val nsm = networkStatsManager ?: return queryFallbackFromTrafficStats(startTime, endTime)

        val validStart = minOf(startTime, endTime)
        val validEnd = maxOf(startTime, endTime).let { if (it <= validStart) validStart + 1000L else it }

        var deviceMobileTx = 0L
        var deviceMobileRx = 0L
        var deviceWifiTx = 0L
        var deviceWifiRx = 0L

        // 1. Device overall summary for Mobile
        try {
            val mobileBucket = nsm.querySummaryForDevice(
                ConnectivityManager.TYPE_MOBILE,
                null,
                validStart,
                validEnd
            )
            deviceMobileTx = mobileBucket.txBytes.coerceAtLeast(0L)
            deviceMobileRx = mobileBucket.rxBytes.coerceAtLeast(0L)
        } catch (e: Exception) {
            // Handled per interface fallback
        }

        // 2. Device overall summary for Wi-Fi
        try {
            val wifiBucket = nsm.querySummaryForDevice(
                ConnectivityManager.TYPE_WIFI,
                null,
                validStart,
                validEnd
            )
            deviceWifiTx = wifiBucket.txBytes.coerceAtLeast(0L)
            deviceWifiRx = wifiBucket.rxBytes.coerceAtLeast(0L)
        } catch (e: Exception) {
            // Handled per interface fallback
        }

        // 3. Per-UID query across both networks
        val uidMobileMap = mutableMapOf<Int, Pair<Long, Long>>() // uid -> Pair(tx, rx)
        val uidWifiMap = mutableMapOf<Int, Pair<Long, Long>>()

        // Query per-app Mobile buckets
        try {
            val mobileStats = nsm.querySummary(
                ConnectivityManager.TYPE_MOBILE,
                null,
                validStart,
                validEnd
            )
            val bucket = NetworkStats.Bucket()
            while (mobileStats.hasNextBucket()) {
                mobileStats.getNextBucket(bucket)
                val uid = bucket.uid
                val current = uidMobileMap[uid] ?: Pair(0L, 0L)
                uidMobileMap[uid] = Pair(
                    current.first + bucket.txBytes.coerceAtLeast(0L),
                    current.second + bucket.rxBytes.coerceAtLeast(0L)
                )
            }
            mobileStats.close()
        } catch (e: Exception) {
            // Empty or unsupported on device
        }

        // Query per-app Wi-Fi buckets
        try {
            val wifiStats = nsm.querySummary(
                ConnectivityManager.TYPE_WIFI,
                null,
                validStart,
                validEnd
            )
            val bucket = NetworkStats.Bucket()
            while (wifiStats.hasNextBucket()) {
                wifiStats.getNextBucket(bucket)
                val uid = bucket.uid
                val current = uidWifiMap[uid] ?: Pair(0L, 0L)
                uidWifiMap[uid] = Pair(
                    current.first + bucket.txBytes.coerceAtLeast(0L),
                    current.second + bucket.rxBytes.coerceAtLeast(0L)
                )
            }
            wifiStats.close()
        } catch (e: Exception) {
            // Empty or unsupported on device
        }

        // Combine all active UIDs
        val allUids = (uidMobileMap.keys + uidWifiMap.keys).toSet()
        val apps = mutableListOf<AppDataUsage>()
        val packageMap = getInstalledPackagesMap()

        for (uid in allUids) {
            val mData = uidMobileMap[uid] ?: Pair(0L, 0L)
            val wData = uidWifiMap[uid] ?: Pair(0L, 0L)

            val mTx = mData.first
            val mRx = mData.second
            val wTx = wData.first
            val wRx = wData.second
            val total = mTx + mRx + wTx + wRx

            if (total > 0) {
                val appInfo = getAppInfoForUid(uid, packageMap)
                apps.add(
                    AppDataUsage(
                        uid = uid,
                        packageName = appInfo.packageName,
                        appName = appInfo.appName,
                        mobileTxBytes = mTx,
                        mobileRxBytes = mRx,
                        wifiTxBytes = wTx,
                        wifiRxBytes = wRx,
                        totalBytes = total,
                        txBytes = mTx + wTx,
                        rxBytes = mRx + wRx,
                        isSystemApp = appInfo.isSystemApp
                    )
                )
            }
        }

        // Calculate usage percentages based on real traffic
        val totalAppTraffic = apps.sumOf { it.totalBytes }.coerceAtLeast(1L)
        val sortedApps = apps
            .sortedByDescending { it.totalBytes }
            .map { app ->
                val pct = ((app.totalBytes.toDouble() / totalAppTraffic.toDouble()) * 100).toFloat()
                app.copy(usagePercentage = pct)
            }

        // Ensure device totals encompass at least the sum of visible applications
        deviceMobileTx = maxOf(deviceMobileTx, sortedApps.sumOf { it.mobileTxBytes })
        deviceMobileRx = maxOf(deviceMobileRx, sortedApps.sumOf { it.mobileRxBytes })
        deviceWifiTx = maxOf(deviceWifiTx, sortedApps.sumOf { it.wifiTxBytes })
        deviceWifiRx = maxOf(deviceWifiRx, sortedApps.sumOf { it.wifiRxBytes })

        return DeviceNetworkTelemetry(
            mobileTxBytes = deviceMobileTx,
            mobileRxBytes = deviceMobileRx,
            wifiTxBytes = deviceWifiTx,
            wifiRxBytes = deviceWifiRx,
            appList = sortedApps,
            startTime = validStart,
            endTime = validEnd,
            isFromNetworkStatsManager = true
        )
    }

    private fun queryFallbackFromTrafficStats(startTime: Long, endTime: Long): DeviceNetworkTelemetry {
        // TrafficStats only tracks cumulative stats since the current device boot session.
        // It cannot query historical dates prior to today.
        val startOfToday = com.example.dataguard.utils.DataFormatter.getStartOfDay(System.currentTimeMillis())
        if (endTime < startOfToday) {
            return DeviceNetworkTelemetry(
                mobileTxBytes = 0L,
                mobileRxBytes = 0L,
                wifiTxBytes = 0L,
                wifiRxBytes = 0L,
                appList = emptyList(),
                startTime = startTime,
                endTime = endTime,
                isFromNetworkStatsManager = false
            )
        }

        val currentMobileTx = TrafficStats.getMobileTxBytes().coerceAtLeast(0L)
        val currentMobileRx = TrafficStats.getMobileRxBytes().coerceAtLeast(0L)
        val currentTotalTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
        val currentTotalRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)

        val currentWifiTx = (currentTotalTx - currentMobileTx).coerceAtLeast(0L)
        val currentWifiRx = (currentTotalRx - currentMobileRx).coerceAtLeast(0L)

        // Query installed apps using TrafficStats per-UID
        val apps = mutableListOf<AppDataUsage>()

        try {
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledPackages(
                    PackageManager.PackageInfoFlags.of(
                        (PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS).toLong()
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS)
            }

            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue
                val uid = appInfo.uid

                val tx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0L)
                val rx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0L)

                if (tx > 0 || rx > 0) {
                    val appName = try {
                        packageManager.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        pkg.packageName
                    }

                    // In TrafficStats fallback, attribute proportionally based on interface ratio
                    val totalDeviceBytes = (currentTotalTx + currentTotalRx).coerceAtLeast(1L)
                    val mobileRatio = (currentMobileTx + currentMobileRx).toDouble() / totalDeviceBytes.toDouble()
                    val wifiRatio = (1.0 - mobileRatio).coerceIn(0.0, 1.0)

                    val mTx = (tx * mobileRatio).toLong()
                    val mRx = (rx * mobileRatio).toLong()
                    val wTx = (tx * wifiRatio).toLong()
                    val wRx = (rx * wifiRatio).toLong()

                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) || uid < 10000
                    val displayName = if (isSystem || appName.isBlank()) pkg.packageName else appName

                    apps.add(
                        AppDataUsage(
                            uid = uid,
                            packageName = pkg.packageName,
                            appName = displayName,
                            mobileTxBytes = mTx,
                            mobileRxBytes = mRx,
                            wifiTxBytes = wTx,
                            wifiRxBytes = wRx,
                            totalBytes = tx + rx,
                            txBytes = tx,
                            rxBytes = rx,
                            isSystemApp = isSystem
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val totalAppTraffic = apps.sumOf { it.totalBytes }.coerceAtLeast(1L)
        val sortedApps = apps
            .sortedByDescending { it.totalBytes }
            .map { app ->
                val pct = ((app.totalBytes.toDouble() / totalAppTraffic.toDouble()) * 100).toFloat()
                app.copy(usagePercentage = pct)
            }

        return DeviceNetworkTelemetry(
            mobileTxBytes = currentMobileTx,
            mobileRxBytes = currentMobileRx,
            wifiTxBytes = currentWifiTx,
            wifiRxBytes = currentWifiRx,
            appList = sortedApps,
            startTime = startTime,
            endTime = endTime,
            isFromNetworkStatsManager = false
        )
    }

    private fun getInstalledPackagesMap(): Map<Int, List<android.content.pm.PackageInfo>> {
        return try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PackageManager.PackageInfoFlags.of(
                    (PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS).toLong()
                )
            } else {
                @Suppress("DEPRECATION")
                (PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS)
            }
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledPackages(flags as PackageManager.PackageInfoFlags)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledPackages(flags as Int)
            }
            packages.groupBy { it.applicationInfo?.uid ?: -1 }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    data class ResolvedAppInfo(
        val packageName: String,
        val appName: String,
        val isSystemApp: Boolean
    )

    private fun getAppInfoForUid(
        uid: Int,
        packageMap: Map<Int, List<android.content.pm.PackageInfo>> = emptyMap()
    ): ResolvedAppInfo {
        // 1. Check pre-cached installed packages map
        val cachedPackages = packageMap[uid]
        if (!cachedPackages.isNullOrEmpty()) {
            val pkg = cachedPackages.firstOrNull { it.packageName != "android" } ?: cachedPackages[0]
            val pkgName = pkg.packageName
            val appInfo = pkg.applicationInfo
            val isSystem = (appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0)) || uid < 10000
            val label = try {
                if (appInfo != null) packageManager.getApplicationLabel(appInfo).toString() else pkgName
            } catch (e: Exception) {
                pkgName
            }
            // For system apps, use the exact package identifier (e.g., com.android.phone)
            val displayName = if (isSystem || label.isBlank()) pkgName else label
            return ResolvedAppInfo(
                packageName = pkgName,
                appName = displayName,
                isSystemApp = isSystem
            )
        }

        // 2. Direct package lookup
        val directPackages = try {
            packageManager.getPackagesForUid(uid)
        } catch (e: Exception) {
            null
        }
        if (!directPackages.isNullOrEmpty()) {
            val pkgName = directPackages.firstOrNull { it != "android" } ?: directPackages[0]
            val isSystem = uid < 10000 || pkgName.startsWith("com.android.") || pkgName.startsWith("android")
            val appInfo = try {
                packageManager.getApplicationInfo(pkgName, 0)
            } catch (e: Exception) {
                null
            }
            val label = try {
                if (appInfo != null) packageManager.getApplicationLabel(appInfo).toString() else pkgName
            } catch (e: Exception) {
                pkgName
            }
            val displayName = if (isSystem || label.isBlank()) pkgName else label
            return ResolvedAppInfo(
                packageName = pkgName,
                appName = displayName,
                isSystemApp = isSystem
            )
        }

        // 3. System process name lookup via PackageManager
        val nameForUid = try {
            packageManager.getNameForUid(uid)
        } catch (e: Exception) {
            null
        }
        if (!nameForUid.isNullOrBlank()) {
            val cleanName = when {
                nameForUid.startsWith("android.uid.phone") -> "com.android.phone"
                nameForUid.startsWith("android.uid.system") -> "android.uid.system"
                nameForUid.startsWith("android.uid.bluetooth") -> "com.android.bluetooth"
                nameForUid.startsWith("android.uid.nfc") -> "com.android.nfc"
                nameForUid.startsWith("android.uid.networkstack") -> "com.android.networkstack"
                nameForUid.contains(":") -> nameForUid.substringBefore(":")
                else -> nameForUid
            }
            return ResolvedAppInfo(
                packageName = cleanName,
                appName = cleanName,
                isSystemApp = true
            )
        }

        // 4. Deterministic AOSP System UIDs
        val systemPackage = when (uid) {
            0 -> "android.kernel"
            1000 -> "android.uid.system"
            1001 -> "com.android.phone"
            1002 -> "com.android.bluetooth"
            1013 -> "android.media"
            1014 -> "android.drm"
            1020 -> "android.net.mdns"
            1021 -> "android.location"
            1027 -> "com.android.nfc"
            1068 -> "android.net.dns"
            1073 -> "com.android.networkstack"
            1074 -> "com.android.captiveportallogin"
            -4 -> "android.uid.removed"
            -5 -> "android.uid.tethering"
            else -> null
        }
        if (systemPackage != null) {
            return ResolvedAppInfo(
                packageName = systemPackage,
                appName = systemPackage,
                isSystemApp = true
            )
        }

        // 5. Fallback without ambiguous 'System UID' string
        val fallback = if (uid < 10000) "android.process.$uid" else "app.uid.$uid"
        return ResolvedAppInfo(
            packageName = fallback,
            appName = fallback,
            isSystemApp = uid < 10000
        )
    }

    /**
     * Converts a list of AppDataUsage into normalized Room database records.
     */
    fun toNormalizedRecords(apps: List<AppDataUsage>, dateEpochDay: Long): List<AppNetworkUsageRecordEntity> {
        val now = System.currentTimeMillis()
        return apps.map { app ->
            AppNetworkUsageRecordEntity(
                uid = app.uid,
                packageName = app.packageName,
                appName = app.appName,
                dateEpochDay = dateEpochDay,
                mobileTxBytes = app.mobileTxBytes,
                mobileRxBytes = app.mobileRxBytes,
                wifiTxBytes = app.wifiTxBytes,
                wifiRxBytes = app.wifiRxBytes,
                lastUpdated = now
            )
        }
    }
}
