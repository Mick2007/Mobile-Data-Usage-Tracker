package com.example.dataguard.domain.plan

import com.example.dataguard.data.entity.BillingCycleType
import com.example.dataguard.data.entity.DataPlanEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DataPlanWindow(
    val cycleStartTime: Long,
    val cycleEndTime: Long,
    val formattedResetDate: String,
    val daysRemaining: Int
)

data class DataPlanStatus(
    val plan: DataPlanEntity,
    val cycleWindow: DataPlanWindow,
    val usedBytes: Long,
    val remainingBytes: Long,
    val usagePercentage: Float,
    val isLimitExceeded: Boolean,
    val currentThresholdReached: Int, // 0, 50, 75, 90, 100
    val activeExcludedApps: List<String>
)

object DataPlanCycleCalculator {

    fun calculateCycleWindow(plan: DataPlanEntity, now: Long = System.currentTimeMillis()): DataPlanWindow {
        val calendar = Calendar.getInstance().apply { timeInMillis = now }

        return when (plan.billingCycle) {
            BillingCycleType.DAILY.name -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                val end = calendar.timeInMillis
                val resetStr = "Tonight at Midnight"
                DataPlanWindow(start, end, resetStr, 0)
            }
            BillingCycleType.WEEKLY.name -> {
                calendar.firstDayOfWeek = Calendar.MONDAY
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.add(Calendar.WEEK_OF_YEAR, 1)
                val end = calendar.timeInMillis
                val remainingDays = maxOf(0, ((end - now) / (24 * 60 * 60 * 1000L)).toInt())
                val resetStr = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(end))
                DataPlanWindow(start, end, resetStr, remainingDays)
            }
            BillingCycleType.CUSTOM_DAYS.name -> {
                val cycleDurationMs = maxOf(1, plan.customCycleDays) * 24L * 60L * 60L * 1000L
                val base = if (plan.startTimestamp > 0) plan.startTimestamp else now
                val elapsedCycles = (now - base) / cycleDurationMs
                val start = base + (elapsedCycles * cycleDurationMs)
                val end = start + cycleDurationMs
                val remainingDays = maxOf(0, ((end - now) / (24 * 60 * 60 * 1000L)).toInt())
                val resetStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(end))
                DataPlanWindow(start, end, resetStr, remainingDays)
            }
            else -> {
                // Monthly (default)
                val targetDay = plan.cycleStartDayOfMonth.coerceIn(1, 28)
                val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

                if (currentDay < targetDay) {
                    calendar.add(Calendar.MONTH, -1)
                }
                calendar.set(Calendar.DAY_OF_MONTH, targetDay)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis

                calendar.add(Calendar.MONTH, 1)
                val end = calendar.timeInMillis
                val remainingDays = maxOf(0, ((end - now) / (24 * 60 * 60 * 1000L)).toInt())
                val resetStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(end))
                DataPlanWindow(start, end, resetStr, remainingDays)
            }
        }
    }
}
