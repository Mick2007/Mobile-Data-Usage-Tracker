package com.example.dataguard.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_stats")
data class UsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val totalMobileTx: Long,
    val totalMobileRx: Long,
    val totalWifiTx: Long = 0L,
    val totalWifiRx: Long = 0L,
    val topAppsJson: String = "",
    val period: String // "daily", "weekly", "monthly", "custom"
) {
    val totalMobileBytes: Long get() = totalMobileTx + totalMobileRx
    val totalWifiBytes: Long get() = totalWifiTx + totalWifiRx
    val totalBytes: Long get() = totalMobileBytes + totalWifiBytes
    val totalTxBytes: Long get() = totalMobileTx + totalWifiTx
    val totalRxBytes: Long get() = totalMobileRx + totalWifiRx
}

data class AppDataUsage(
    val uid: Int,
    val packageName: String,
    val appName: String,
    val mobileTxBytes: Long = 0L,
    val mobileRxBytes: Long = 0L,
    val wifiTxBytes: Long = 0L,
    val wifiRxBytes: Long = 0L,
    val totalBytes: Long = mobileTxBytes + mobileRxBytes + wifiTxBytes + wifiRxBytes,
    val txBytes: Long = mobileTxBytes + wifiTxBytes,
    val rxBytes: Long = mobileRxBytes + wifiRxBytes,
    val usagePercentage: Float = 0f,
    val isSystemApp: Boolean = false
) {
    val mobileTotalBytes: Long get() = mobileTxBytes + mobileRxBytes
    val wifiTotalBytes: Long get() = wifiTxBytes + wifiRxBytes

    val isSystem: Boolean get() = isSystemApp ||
        uid < 10000 ||
        packageName.startsWith("android") ||
        packageName.startsWith("com.android.") ||
        packageName.startsWith("uid.")

    fun serialize(): String =
        "$uid\t$packageName\t$appName\t$mobileTxBytes\t$mobileRxBytes\t$wifiTxBytes\t$wifiRxBytes\t$totalBytes\t$usagePercentage"

    companion object {
        fun deserialize(raw: String): AppDataUsage? {
            val parts = raw.split("\t")
            if (parts.size < 6) return null
            return try {
                if (parts.size >= 9) {
                    val uid = parts[0].toInt()
                    val packageName = parts[1]
                    val appName = parts[2]
                    val mTx = parts[3].toLong()
                    val mRx = parts[4].toLong()
                    val wTx = parts[5].toLong()
                    val wRx = parts[6].toLong()
                    val total = parts[7].toLong()
                    val pct = parts[8].toFloat()
                    AppDataUsage(
                        uid = uid,
                        packageName = packageName,
                        appName = appName,
                        mobileTxBytes = mTx,
                        mobileRxBytes = mRx,
                        wifiTxBytes = wTx,
                        wifiRxBytes = wRx,
                        totalBytes = total,
                        usagePercentage = pct
                    )
                } else {
                    // Backward-compatible fallback for 6-field format:
                    // uid, packageName, appName, txBytes, rxBytes, totalBytes
                    val uid = parts[0].toInt()
                    val packageName = parts[1]
                    val appName = parts[2]
                    val tx = parts[3].toLong()
                    val rx = parts[4].toLong()
                    val total = parts[5].toLong()
                    AppDataUsage(
                        uid = uid,
                        packageName = packageName,
                        appName = appName,
                        mobileTxBytes = tx,
                        mobileRxBytes = rx,
                        wifiTxBytes = 0L,
                        wifiRxBytes = 0L,
                        totalBytes = total
                    )
                }
            } catch (e: Exception) {
                null
            }
        }

        fun serializeList(apps: List<AppDataUsage>): String {
            return apps.joinToString("\n") { it.serialize() }
        }

        fun deserializeList(raw: String): List<AppDataUsage> {
            if (raw.isBlank()) return emptyList()
            return raw.split("\n").mapNotNull { deserialize(it) }
        }
    }
}

/**
 * Normalized database entity for per-app historical usage breakdown by day and network interface.
 */
@Entity(
    tableName = "app_network_usage_records",
    primaryKeys = ["uid", "dateEpochDay"]
)
data class AppNetworkUsageRecordEntity(
    val uid: Int,
    val packageName: String,
    val appName: String,
    val dateEpochDay: Long, // Epoch day: timestamp / (24 * 60 * 60 * 1000L)
    val mobileTxBytes: Long,
    val mobileRxBytes: Long,
    val wifiTxBytes: Long,
    val wifiRxBytes: Long,
    val lastUpdated: Long
) {
    val totalMobileBytes: Long get() = mobileTxBytes + mobileRxBytes
    val totalWifiBytes: Long get() = wifiTxBytes + wifiRxBytes
    val totalBytes: Long get() = totalMobileBytes + totalWifiBytes
    val totalTxBytes: Long get() = mobileTxBytes + wifiTxBytes
    val totalRxBytes: Long get() = mobileRxBytes + wifiRxBytes
}

@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey
    val uid: Int,
    val packageName: String,
    val appName: String,
    val blockedAt: Long = System.currentTimeMillis(),
    val reason: String = "User firewall rule",
    val blockMobile: Boolean = true,
    val blockWifi: Boolean = true
)

@Entity(tableName = "blocked_traffic_events")
data class BlockedTrafficEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uid: Int,
    val packageName: String,
    val appName: String,
    val reason: String = "User firewall rule",
    val networkInterface: String = "ALL", // "ALL", "MOBILE", "WIFI"
    val timestamp: Long = System.currentTimeMillis(),
    val bytesBlocked: Long = 0L
)

enum class QuotaCycleType(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    CUSTOM("Custom Cycle")
}

enum class QuotaEnforcementMode(val label: String) {
    WARN_ONLY("Warn Only"),
    BLOCK_AT_LIMIT("Block at Limit")
}

@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey
    val uid: Int,
    val packageName: String,
    val appName: String = "",
    val limitBytes: Long = 0L,
    val warningThresholdPercent: Int = 80,
    val enforcementMode: String = QuotaEnforcementMode.WARN_ONLY.name,
    val cycleType: String = QuotaCycleType.MONTHLY.name,
    val cycleStartDay: Int = 1,
    val cycleStartTimestamp: Long = 0L,
    val customCycleDays: Int = 30,
    val isEnabled: Boolean = true,
    val lastWarningNotifiedAt: Long = 0L,
    val lastLimitNotifiedAt: Long = 0L,
    val isLimitReached: Boolean = false,
    val dailyLimitMB: Long = 0L,
    val weeklyLimitMB: Long = 0L,
    val monthlyLimitMB: Long = 0L
)

@Entity(tableName = "quota_breach_events")
data class QuotaBreachEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uid: Int,
    val packageName: String,
    val appName: String,
    val eventType: String, // "WARNING" or "LIMIT_REACHED"
    val usedBytes: Long,
    val limitBytes: Long,
    val enforcementAction: String, // "WARN_ONLY" or "BLOCK_AT_LIMIT"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "data_toggle_history")
data class DataToggleHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val enabled: Boolean,
    val reason: String // "idle", "limit_reached", "manual", "auto_toggle"
)
