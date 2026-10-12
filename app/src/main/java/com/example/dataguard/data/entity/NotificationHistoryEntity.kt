package com.example.dataguard.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class NotificationAlertCategory(val label: String) {
    PLAN_THRESHOLD("Plan Allowance Threshold"),
    APP_EXCEEDS_QUOTA("App Quota Exceeded"),
    DOMAIN_EXCEEDS_QUOTA("Domain Quota Exceeded"),
    HOTSPOT_EXCEEDS_QUOTA("Hotspot Quota Exceeded"),
    DATA_SPIKE("Unusual Data Spike"),
    NEW_APP_NETWORK_ACTIVITY("Newly Observed App"),
    NEW_DOMAIN_CONTACTED("Newly Observed Domain"),
    FIREWALL_BLOCK("Firewall Block Event")
}

/**
 * Historical record of all triggered alerts and notifications.
 * Preserved for audit, grouping, and user review in Notification History screen.
 */
@Entity(
    tableName = "notification_history",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["category"]),
        Index(value = ["targetKey"])
    ]
)
data class NotificationHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val message: String,
    val category: String, // from NotificationAlertCategory
    val targetKey: String = "", // packageName, domain, "hotspot", or "plan"
    val bytesObserved: Long = 0L,
    val thresholdPercent: Int = 0,
    val actionTaken: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
