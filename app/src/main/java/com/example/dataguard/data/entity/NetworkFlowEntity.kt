package com.example.dataguard.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AttributionConfidence(val label: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low"),
    UNKNOWN("Unknown")
}

/**
 * Normalized network traffic flow record aggregated per domain / application flow.
 * Does NOT store raw packets individually. Aggregates bytes, connection count, and protocol.
 */
@Entity(
    tableName = "network_flow_records",
    indices = [
        Index(value = ["domain"]),
        Index(value = ["packageName"]),
        Index(value = ["uid"]),
        Index(value = ["lastSeen"]),
        Index(value = ["flowKey"], unique = true)
    ]
)
data class NetworkFlowEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val flowKey: String, // domain + "::" + packageName + "::" + protocol + "::" + networkType
    val domain: String,  // e.g. "googlevideo.com", "youtube.com", or "Unknown / IP-only (142.250.180.14)"
    val uid: Int = -1,
    val packageName: String = "unknown",
    val appName: String = "Unknown",
    val destinationIp: String = "",
    val protocol: String = "TCP", // TCP, UDP, TLS, DNS
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val connectionCount: Int = 1,
    val networkType: String = "ANY", // "MOBILE", "WIFI", "ANY"
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val confidence: String = AttributionConfidence.HIGH.name
) {
    val confidenceEnum: AttributionConfidence
        get() = try {
            AttributionConfidence.valueOf(confidence)
        } catch (_: Exception) {
            AttributionConfidence.UNKNOWN
        }
}

/**
 * Aggregated view for UI Top Domains listing.
 */
data class DomainSummary(
    val domain: String,
    val totalBytes: Long,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val connectionCount: Int,
    val associatedPackages: List<String>,
    val associatedAppNames: List<String>,
    val firstSeen: Long,
    val lastSeen: Long,
    val confidence: AttributionConfidence
) {
    val associatedApps: List<String> get() = associatedAppNames
}
