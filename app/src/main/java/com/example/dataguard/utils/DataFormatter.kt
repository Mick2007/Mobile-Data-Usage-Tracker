package com.example.dataguard.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DataFormatter {
    fun formatBytes(bytes: Long): String {
        val absBytes = if (bytes < 0) 0L else bytes
        return when {
            absBytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", absBytes / (1024.0 * 1024.0 * 1024.0))
            absBytes >= 1024L * 1024L -> String.format(Locale.US, "%.2f MB", absBytes / (1024.0 * 1024.0))
            absBytes >= 1024L -> String.format(Locale.US, "%.1f KB", absBytes / 1024.0)
            else -> "$absBytes B"
        }
    }

    fun formatBytesShort(bytes: Long): String {
        val absBytes = if (bytes < 0) 0L else bytes
        return when {
            absBytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f GB", absBytes / (1024.0 * 1024.0 * 1024.0))
            absBytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", absBytes / (1024.0 * 1024.0))
            absBytes >= 1024L -> String.format(Locale.US, "%.0f KB", absBytes / 1024.0)
            else -> "$absBytes B"
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        val absSpeed = if (bytesPerSec < 0) 0L else bytesPerSec
        return when {
            absSpeed >= 1024L * 1024L -> String.format(Locale.US, "%.2f MB/s", absSpeed / (1024.0 * 1024.0))
            absSpeed >= 1024L -> String.format(Locale.US, "%.1f KB/s", absSpeed / 1024.0)
            else -> "$absSpeed B/s"
        }
    }

    fun formatTime(timestamp: Long): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        return format.format(date)
    }

    fun formatTimestamp(timestamp: Long): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return format.format(date)
    }

    fun formatDate(timestamp: Long): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return format.format(date)
    }

    fun formatDateShort(timestamp: Long): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat("EEE", Locale.getDefault())
        return format.format(date)
    }

    fun formatDateTime(timestamp: Long): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        return format.format(date)
    }

    fun getStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun getEndOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    fun getStartOfWeek(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun getStartOfMonth(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
