package com.example.dataguard.domain.usecase

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DetectIdleUseCase(
    private val context: Context
) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    suspend operator fun invoke(idleThresholdMinutes: Long = 5): Boolean =
        withContext(Dispatchers.Default) {
            try {
                // If screen is off, device is definitely idle
                val isScreenOn = powerManager?.isInteractive ?: true
                if (!isScreenOn) return@withContext true

                val currentTime = System.currentTimeMillis()
                val startTime = currentTime - (idleThresholdMinutes * 60 * 1000)

                val stats = usageStatsManager?.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    currentTime
                ) ?: emptyList()

                if (stats.isEmpty()) return@withContext false

                // If any app was used in foreground within the threshold, not idle
                val hasRecentForeground = stats.any { stat ->
                    stat.lastTimeUsed in (startTime + 1)..currentTime
                }

                !hasRecentForeground
            } catch (e: Exception) {
                false
            }
        }
}
