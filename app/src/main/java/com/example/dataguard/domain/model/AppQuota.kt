package com.example.dataguard.domain.model

import com.example.dataguard.data.entity.AppLimitEntity
import com.example.dataguard.data.entity.QuotaCycleType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Encapsulates the current computed state of an application's quota.
 */
data class AppQuotaCalculation(
    val limit: AppLimitEntity,
    val cycleStartTime: Long,
    val cycleEndTime: Long,
    val usedBytes: Long,
    val remainingBytes: Long,
    val percentage: Float,
    val isWarningReached: Boolean,
    val isLimitReached: Boolean,
    val resetDateFormatted: String,
    val cycleDescription: String
)

object AppQuotaCycleCalculator {

    /**
     * Calculates the exact [startTime, endTime] window in milliseconds for the current quota cycle.
     */
    fun calculateCycleWindow(
        limit: AppLimitEntity,
        now: Long = System.currentTimeMillis()
    ): Pair<Long, Long> {
        val cycleType = try {
            QuotaCycleType.valueOf(limit.cycleType)
        } catch (e: Exception) {
            QuotaCycleType.MONTHLY
        }

        return when (cycleType) {
            QuotaCycleType.DAILY -> {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, 1)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            QuotaCycleType.WEEKLY -> {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = now
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (cal.timeInMillis > now) {
                    cal.add(Calendar.WEEK_OF_YEAR, -1)
                }
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, 7)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            QuotaCycleType.MONTHLY -> {
                val startDay = limit.cycleStartDay.coerceIn(1, 31)
                val cal = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val currentDay = cal.get(Calendar.DAY_OF_MONTH)

                val startCal = Calendar.getInstance().apply {
                    timeInMillis = cal.timeInMillis
                    if (currentDay < startDay) {
                        add(Calendar.MONTH, -1)
                    }
                    val maxDayThisMonth = getActualMaximum(Calendar.DAY_OF_MONTH)
                    set(Calendar.DAY_OF_MONTH, minOf(startDay, maxDayThisMonth))
                }

                val endCal = Calendar.getInstance().apply {
                    timeInMillis = startCal.timeInMillis
                    add(Calendar.MONTH, 1)
                    val maxDayNextMonth = getActualMaximum(Calendar.DAY_OF_MONTH)
                    set(Calendar.DAY_OF_MONTH, minOf(startDay, maxDayNextMonth))
                }
                Pair(startCal.timeInMillis, endCal.timeInMillis)
            }
            QuotaCycleType.CUSTOM -> {
                val durationDays = limit.customCycleDays.coerceAtLeast(1)
                val durationMillis = durationDays * 24L * 60L * 60L * 1000L
                val anchor = if (limit.cycleStartTimestamp > 0L) limit.cycleStartTimestamp else now

                if (now < anchor) {
                    Pair(anchor, anchor + durationMillis)
                } else {
                    val elapsedCycles = (now - anchor) / durationMillis
                    val start = anchor + (elapsedCycles * durationMillis)
                    val end = start + durationMillis
                    Pair(start, end)
                }
            }
        }
    }

    /**
     * Formats human-readable reset countdown / date for UI display.
     */
    fun formatResetDate(endTime: Long, now: Long = System.currentTimeMillis()): String {
        val diff = endTime - now
        if (diff <= 0) return "Resets today"

        val hours = diff / (60 * 60 * 1000L)
        val days = diff / (24 * 60 * 60 * 1000L)
        val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
        val formattedDate = sdf.format(Date(endTime))

        return when {
            hours < 24 -> "Resets in ${hours.coerceAtLeast(1)}h (Midnight)"
            days == 1L -> "Resets tomorrow ($formattedDate)"
            days < 7 -> "Resets in $days days ($formattedDate)"
            else -> "Resets on $formattedDate ($days days)"
        }
    }

    fun getCycleDescription(limit: AppLimitEntity): String {
        val type = try {
            QuotaCycleType.valueOf(limit.cycleType)
        } catch (e: Exception) {
            QuotaCycleType.MONTHLY
        }

        return when (type) {
            QuotaCycleType.DAILY -> "Daily limit (resets every midnight)"
            QuotaCycleType.WEEKLY -> "Weekly limit (resets every Monday)"
            QuotaCycleType.MONTHLY -> "Monthly limit (billing cycle day ${limit.cycleStartDay})"
            QuotaCycleType.CUSTOM -> "Custom cycle (${limit.customCycleDays} days)"
        }
    }
}
