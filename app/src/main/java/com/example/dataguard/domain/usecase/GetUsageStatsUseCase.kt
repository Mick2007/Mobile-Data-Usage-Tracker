package com.example.dataguard.domain.usecase

import android.content.Context
import com.example.dataguard.data.entity.AppDataUsage
import com.example.dataguard.data.entity.UsageEntity
import com.example.dataguard.data.repository.DataRepository
import com.example.dataguard.domain.model.TimeRangeSelection
import com.example.dataguard.utils.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetUsageStatsUseCase(
    private val context: Context,
    private val repository: DataRepository,
    private val permissionHelper: PermissionHelper
) {
    val networkStatsHelper = NetworkStatsHelper(context, permissionHelper)

    suspend operator fun invoke(
        timeRange: TimeRangeSelection = TimeRangeSelection.today()
    ): Result = withContext(Dispatchers.IO) {
        try {
            val telemetry = networkStatsHelper.queryNetworkTelemetry(timeRange)
            val topApps = telemetry.appList

            val mobileTx = telemetry.mobileTxBytes
            val mobileRx = telemetry.mobileRxBytes
            val wifiTx = telemetry.wifiTxBytes
            val wifiRx = telemetry.wifiRxBytes

            val usage = UsageEntity(
                timestamp = timeRange.endTime,
                totalMobileTx = mobileTx,
                totalMobileRx = mobileRx,
                totalWifiTx = wifiTx,
                totalWifiRx = wifiRx,
                topAppsJson = AppDataUsage.serializeList(topApps),
                period = timeRange.type.name.lowercase()
            )

            // Save overall usage snapshot in Room
            repository.insertUsage(usage)

            // Save normalized per-app records for historical date queries
            if (topApps.isNotEmpty()) {
                val epochDay = timeRange.startEpochDay
                val normalizedRecords = networkStatsHelper.toNormalizedRecords(topApps, epochDay)
                repository.insertOrUpdateAppRecords(normalizedRecords)
            }

            Result.Success(usage, topApps, telemetry.isFromNetworkStatsManager)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    sealed class Result {
        data class Success(
            val usage: UsageEntity,
            val topApps: List<AppDataUsage>,
            val isFromNetworkStatsManager: Boolean
        ) : Result()
        data class Error(val exception: Exception) : Result()
    }
}
