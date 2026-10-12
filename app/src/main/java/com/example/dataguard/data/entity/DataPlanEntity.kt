package com.example.dataguard.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class BillingCycleType(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    CUSTOM_DAYS("Custom Days")
}

enum class PlanTrafficScope(val label: String) {
    MOBILE_ONLY("Mobile Only"),
    ALL_TRAFFIC("All Traffic (Mobile + Wi-Fi)")
}

/**
 * Entity representing the user-configured primary Carrier / Data Plan.
 * Supports configurable thresholds (50%, 75%, 90%, 100%), rollover allowances,
 * excluded apps, and cycle resets.
 */
@Entity(tableName = "data_plans")
data class DataPlanEntity(
    @PrimaryKey
    val id: Long = 1L, // Primary plan ID
    val planName: String = "My Cellular Plan",
    val totalAllowanceBytes: Long = 10L * 1024L * 1024L * 1024L, // Default 10 GB
    val rolloverBytes: Long = 0L,
    val billingCycle: String = BillingCycleType.MONTHLY.name,
    val trafficScope: String = PlanTrafficScope.MOBILE_ONLY.name,
    val startTimestamp: Long = System.currentTimeMillis(),
    val cycleStartDayOfMonth: Int = 1,
    val customCycleDays: Int = 30,
    val warningThresholdsJson: String = "50,75,90,100", // Comma-separated percentages
    val excludedPackageNamesJson: String = "", // Comma-separated package names
    val isEnabled: Boolean = true,
    val lastNotifiedThreshold: Int = 0, // Highest threshold alerted in current cycle
    val lastCycleResetTimestamp: Long = 0L
) {
    val totalEffectiveAllowanceBytes: Long
        get() = totalAllowanceBytes + rolloverBytes

    val warningThresholdsList: List<Int>
        get() = if (warningThresholdsJson.isBlank()) listOf(50, 75, 90, 100)
        else warningThresholdsJson.split(",").mapNotNull { it.trim().toIntOrNull() }.sorted()

    val excludedPackagesList: List<String>
        get() = if (excludedPackageNamesJson.isBlank()) emptyList()
        else excludedPackageNamesJson.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
