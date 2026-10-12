package com.example.dataguard.domain.model

import com.example.dataguard.data.entity.BlockedTrafficEventEntity

enum class GraphFilter(val label: String) {
    ALL("All"),
    MOBILE("Mobile"),
    WIFI("Wi-Fi"),
    APPS("Apps"),
    WEBSITES("Websites")
}

enum class DashboardTimeRange(val label: String) {
    TODAY("Today"),
    DAYS_7("7 Days"),
    DAYS_30("30 Days"),
    CUSTOM("Custom")
}

data class NetworkTimelinePoint(
    val timestamp: Long,
    val label: String,
    val startTime: Long,
    val endTime: Long,
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val totalBytes: Long = uploadBytes + downloadBytes,
    val mobileBytes: Long = 0L,
    val wifiBytes: Long = 0L,
    val allowedBytes: Long = 0L,
    val blockedBytes: Long = 0L,
    val activeApps: List<String> = emptyList(),
    val contactedDomains: List<String> = emptyList()
)

data class AppIntervalUsage(
    val appName: String,
    val packageName: String,
    val uid: Int,
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val totalBytes: Long = uploadBytes + downloadBytes,
    val mobileBytes: Long = 0L,
    val wifiBytes: Long = 0L,
    val isBlocked: Boolean = false
)

data class DomainIntervalUsage(
    val domain: String,
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val totalBytes: Long = uploadBytes + downloadBytes,
    val connectionCount: Int = 1,
    val associatedApps: List<String> = emptyList(),
    val isBlocked: Boolean = false
)

data class GraphIntervalDetails(
    val startTime: Long,
    val endTime: Long,
    val label: String,
    val totalDataConsumed: Long,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val mobileBytes: Long,
    val wifiBytes: Long,
    val allowedBytes: Long,
    val blockedBytes: Long,
    val activeApps: List<AppIntervalUsage> = emptyList(),
    val contactedDomains: List<DomainIntervalUsage> = emptyList(),
    val blockedEvents: List<BlockedTrafficEventEntity> = emptyList()
)
