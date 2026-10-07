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
    val topAppsJson: String, // Compact serialized list of apps
    val period: String // "daily", "weekly", "monthly"
)

data class AppDataUsage(
    val uid: Int,
    val packageName: String,
    val appName: String,
    val txBytes: Long,
    val rxBytes: Long,
    val totalBytes: Long
) {
    fun serialize(): String = "$uid\t$packageName\t$appName\t$txBytes\t$rxBytes\t$totalBytes"

    companion object {
        fun deserialize(raw: String): AppDataUsage? {
            val parts = raw.split("\t")
            if (parts.size < 6) return null
            return try {
                AppDataUsage(
                    uid = parts[0].toInt(),
                    packageName = parts[1],
                    appName = parts[2],
                    txBytes = parts[3].toLong(),
                    rxBytes = parts[4].toLong(),
                    totalBytes = parts[5].toLong()
                )
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

@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey
    val uid: Int,
    val packageName: String,
    val appName: String,
    val blockedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey
    val uid: Int,
    val packageName: String,
    val dailyLimitMB: Long,
    val weeklyLimitMB: Long,
    val monthlyLimitMB: Long
)

@Entity(tableName = "data_toggle_history")
data class DataToggleHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val enabled: Boolean,
    val reason: String // "idle", "limit_reached", "manual", "auto_toggle"
)
