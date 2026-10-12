package com.example.dataguard.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Granular user preferences for notification alerting.
 * Prevents spam and allows fine-grained toggling of each alert condition.
 */
@Entity(tableName = "notification_preferences")
data class NotificationPreferencesEntity(
    @PrimaryKey
    val id: Long = 1L,
    val isEnabledMaster: Boolean = true,
    // Threshold alerts (50%, 75%, 90%, 100%)
    val alertOn50Percent: Boolean = true,
    val alertOn75Percent: Boolean = true,
    val alertOn90Percent: Boolean = true,
    val alertOn100Percent: Boolean = true,
    // Specialized condition alerts
    val alertAppExceedsMb: Boolean = true,
    val appExceedsThresholdMb: Long = 250L, // Alert if app consumes > 250MB in a session/interval
    val alertDomainExceedsMb: Boolean = true,
    val domainExceedsThresholdMb: Long = 100L, // Alert if domain consumes > 100MB
    val alertHotspotExceedsMb: Boolean = true,
    val hotspotExceedsThresholdMb: Long = 500L, // Alert if hotspot exceeds 500MB
    val alertUnusualSpike: Boolean = true,
    val spikeSpeedThresholdKbps: Long = 2048L, // Alert if background speed > 2MB/s sustained
    val alertNewlyObservedApp: Boolean = true,
    val alertNewlyObservedDomain: Boolean = false, // Off by default to prevent spamming
    val alertFirewallBlockEvent: Boolean = true,
    // Anti-spam throttling
    val minAlertIntervalMinutes: Int = 15, // Throttle repetitive notifications
    val groupNotifications: Boolean = true
)
