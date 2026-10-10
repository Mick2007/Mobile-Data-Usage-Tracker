package com.example.dataguard.domain.model

import com.example.dataguard.utils.DataFormatter
import java.util.Calendar

enum class TimeRangeType(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    LAST_7_DAYS("Last 7 Days"),
    THIS_MONTH("This Month"),
    CUSTOM("Custom")
}

data class TimeRangeSelection(
    val type: TimeRangeType,
    val startTime: Long,
    val endTime: Long,
    val customLabel: String? = null
) {
    val displayLabel: String
        get() = when (type) {
            TimeRangeType.CUSTOM -> customLabel ?: "${DataFormatter.formatDate(startTime)} - ${DataFormatter.formatDate(endTime)}"
            else -> type.label
        }

    val startEpochDay: Long
        get() = startTime / (24 * 60 * 60 * 1000L)

    val endEpochDay: Long
        get() = endTime / (24 * 60 * 60 * 1000L)

    companion object {
        fun today(now: Long = System.currentTimeMillis()): TimeRangeSelection {
            val start = DataFormatter.getStartOfDay(now)
            return TimeRangeSelection(TimeRangeType.TODAY, start, now)
        }

        fun yesterday(now: Long = System.currentTimeMillis()): TimeRangeSelection {
            val startOfToday = DataFormatter.getStartOfDay(now)
            val cal = Calendar.getInstance().apply {
                timeInMillis = startOfToday
                add(Calendar.DAY_OF_YEAR, -1)
            }
            val startOfYesterday = cal.timeInMillis
            val endOfYesterday = startOfToday - 1L
            return TimeRangeSelection(TimeRangeType.YESTERDAY, startOfYesterday, endOfYesterday)
        }

        fun last7Days(now: Long = System.currentTimeMillis()): TimeRangeSelection {
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, -6)
            }
            val start = DataFormatter.getStartOfDay(cal.timeInMillis)
            return TimeRangeSelection(TimeRangeType.LAST_7_DAYS, start, now)
        }

        fun thisMonth(now: Long = System.currentTimeMillis()): TimeRangeSelection {
            val start = DataFormatter.getStartOfMonth(now)
            return TimeRangeSelection(TimeRangeType.THIS_MONTH, start, now)
        }

        fun custom(start: Long, end: Long, label: String? = null): TimeRangeSelection {
            val validStart = minOf(start, end)
            val validEnd = maxOf(start, end)
            return TimeRangeSelection(TimeRangeType.CUSTOM, validStart, validEnd, label)
        }
    }
}
