package com.example.dataguard.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a discrete Wi-Fi Hotspot or Tethering session on the Android device.
 *
 * Technical Reality:
 * Android aggregates all tethered/hotspot traffic at the kernel routing layer under
 * UID -5 (TrafficStats.UID_TETHERING). Third-party non-root applications cannot inspect
 * individual connected clients or per-client bandwidth usage.
 */
@Entity(
    tableName = "hotspot_sessions",
    indices = [
        Index(value = ["startTime"]),
        Index(value = ["isActive"]),
        Index(value = ["lastUpdated"])
    ]
)
data class HotspotSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val isActive: Boolean = true,
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val sessionType: String = "WIFI_AP", // "WIFI_AP", "USB_TETHERING", "BLUETOOTH_TETHERING", "TETHERING"
    val clientAttributionState: String = "Tethering traffic",
    val attributionExplanation: String = "Android OS aggregates tethering traffic under UID_TETHERING (-5). Individual client attribution is not exposed by Android APIs.",
    val interfaceName: String = "ap0",
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val durationMs: Long
        get() = (endTime ?: System.currentTimeMillis()) - startTime
}
