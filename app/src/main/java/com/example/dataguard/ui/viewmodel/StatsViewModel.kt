package com.example.dataguard.ui.viewmodel

import android.net.ConnectivityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dataguard.data.entity.AppDataUsage
import com.example.dataguard.data.entity.UsageEntity
import com.example.dataguard.data.repository.DataRepository
import com.example.dataguard.domain.model.TimeRangeSelection
import com.example.dataguard.domain.model.TimeRangeType
import com.example.dataguard.domain.usecase.NetworkStatsHelper
import com.example.dataguard.utils.CsvExporter
import com.example.dataguard.utils.DataFormatter
import com.example.dataguard.utils.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DailyChartPoint(
    val label: String,
    val valueMB: Double,
    val mobileMB: Double = 0.0,
    val wifiMB: Double = 0.0,
    val timestamp: Long
)

class StatsViewModel(
    private val repository: DataRepository,
    private val csvExporter: CsvExporter,
    private val networkStatsHelper: NetworkStatsHelper,
    private val permissionHelper: PermissionHelper
) : ViewModel() {

    private val _selectedTimeRange = MutableStateFlow(TimeRangeSelection.last7Days())
    val selectedTimeRange: StateFlow<TimeRangeSelection> = _selectedTimeRange.asStateFlow()

    private val _recentUsages = MutableStateFlow<List<UsageEntity>>(emptyList())
    val recentUsages: StateFlow<List<UsageEntity>> = _recentUsages.asStateFlow()

    private val _chartPoints = MutableStateFlow<List<DailyChartPoint>>(emptyList())
    val chartPoints: StateFlow<List<DailyChartPoint>> = _chartPoints.asStateFlow()

    private val _appDistribution = MutableStateFlow<List<AppDataUsage>>(emptyList())
    val appDistribution: StateFlow<List<AppDataUsage>> = _appDistribution.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        loadStats()
    }

    fun setTimeRangeType(type: TimeRangeType) {
        val now = System.currentTimeMillis()
        val range = when (type) {
            TimeRangeType.TODAY -> TimeRangeSelection.today(now)
            TimeRangeType.YESTERDAY -> TimeRangeSelection.yesterday(now)
            TimeRangeType.LAST_7_DAYS -> TimeRangeSelection.last7Days(now)
            TimeRangeType.THIS_MONTH -> TimeRangeSelection.thisMonth(now)
            TimeRangeType.CUSTOM -> _selectedTimeRange.value
        }
        _selectedTimeRange.value = range
        refreshAnalyticsForRange(range)
    }

    fun setCustomRange(startMillis: Long, endMillis: Long) {
        val range = TimeRangeSelection.custom(startMillis, endMillis)
        _selectedTimeRange.value = range
        refreshAnalyticsForRange(range)
    }

    fun loadStats() {
        viewModelScope.launch {
            repository.getAllRecentUsage(150).collect { list ->
                _recentUsages.value = list
                refreshAnalyticsForRange(_selectedTimeRange.value)
            }
        }
    }

    private fun refreshAnalyticsForRange(timeRange: TimeRangeSelection) {
        viewModelScope.launch(Dispatchers.IO) {
            val hasPermission = permissionHelper.hasPackageUsageStatsPermission()
            val points = mutableListOf<DailyChartPoint>()
            val now = System.currentTimeMillis()
            val dayMillis = 24 * 60 * 60 * 1000L

            when (timeRange.type) {
                TimeRangeType.TODAY -> {
                    val startOfDay = DataFormatter.getStartOfDay(now)
                    for (i in 0..5) {
                        val hour = i * 4
                        val label = String.format("%02d:00", hour)
                        val segStart = startOfDay + hour * 3600 * 1000L
                        val segEnd = minOf(now, segStart + 4 * 3600 * 1000L)

                        var mBytes = 0L
                        var wBytes = 0L

                        if (hasPermission && segEnd > segStart) {
                            val (mTx, mRx) = networkStatsHelper.queryDeviceSummary(ConnectivityManager.TYPE_MOBILE, segStart, segEnd)
                            val (wTx, wRx) = networkStatsHelper.queryDeviceSummary(ConnectivityManager.TYPE_WIFI, segStart, segEnd)
                            mBytes = mTx + mRx
                            wBytes = wTx + wRx
                        } else {
                            val segUsage = _recentUsages.value.filter { it.timestamp in segStart..segEnd }
                            mBytes = segUsage.sumOf { it.totalMobileBytes }
                            wBytes = segUsage.sumOf { it.totalWifiBytes }
                        }

                        val totalMB = (mBytes + wBytes) / (1024.0 * 1024.0)
                        val mMB = mBytes / (1024.0 * 1024.0)
                        val wMB = wBytes / (1024.0 * 1024.0)
                        points.add(DailyChartPoint(label, totalMB, mMB, wMB, segStart))
                    }
                }
                TimeRangeType.YESTERDAY -> {
                    val startOfYesterday = timeRange.startTime
                    for (i in 0..5) {
                        val hour = i * 4
                        val label = String.format("%02d:00", hour)
                        val segStart = startOfYesterday + hour * 3600 * 1000L
                        val segEnd = segStart + 4 * 3600 * 1000L

                        var mBytes = 0L
                        var wBytes = 0L

                        if (hasPermission) {
                            val (mTx, mRx) = networkStatsHelper.queryDeviceSummary(ConnectivityManager.TYPE_MOBILE, segStart, segEnd)
                            val (wTx, wRx) = networkStatsHelper.queryDeviceSummary(ConnectivityManager.TYPE_WIFI, segStart, segEnd)
                            mBytes = mTx + mRx
                            wBytes = wTx + wRx
                        } else {
                            val segUsage = _recentUsages.value.filter { it.timestamp in segStart..segEnd }
                            mBytes = segUsage.sumOf { it.totalMobileBytes }
                            wBytes = segUsage.sumOf { it.totalWifiBytes }
                        }

                        val totalMB = (mBytes + wBytes) / (1024.0 * 1024.0)
                        val mMB = mBytes / (1024.0 * 1024.0)
                        val wMB = wBytes / (1024.0 * 1024.0)
                        points.add(DailyChartPoint(label, totalMB, mMB, wMB, segStart))
                    }
                }
                TimeRangeType.LAST_7_DAYS -> {
                    for (i in 6 downTo 0) {
                        val dayTime = now - (i * dayMillis)
                        val label = DataFormatter.formatDateShort(dayTime)
                        val dayStart = DataFormatter.getStartOfDay(dayTime)
                        val dayEnd = if (i == 0) now else DataFormatter.getEndOfDay(dayTime)

                        var mBytes = 0L
                        var wBytes = 0L

                        if (hasPermission) {
                            val (mTx, mRx) = networkStatsHelper.queryDeviceSummary(ConnectivityManager.TYPE_MOBILE, dayStart, dayEnd)
                            val (wTx, wRx) = networkStatsHelper.queryDeviceSummary(ConnectivityManager.TYPE_WIFI, dayStart, dayEnd)
                            mBytes = mTx + mRx
                            wBytes = wTx + wRx
                        } else {
                            val dayUsage = _recentUsages.value.filter { it.timestamp in dayStart..dayEnd }
                            mBytes = dayUsage.sumOf { it.totalMobileBytes }
                            wBytes = dayUsage.sumOf { it.totalWifiBytes }
                        }

                        val totalMB = (mBytes + wBytes) / (1024.0 * 1024.0)
                        val mMB = mBytes / (1024.0 * 1024.0)
                        val wMB = wBytes / (1024.0 * 1024.0)
                        points.add(DailyChartPoint(label, totalMB, mMB, wMB, dayTime))
                    }
                }
                TimeRangeType.THIS_MONTH, TimeRangeType.CUSTOM -> {
                    val diff = (timeRange.endTime - timeRange.startTime).coerceAtLeast(dayMillis)
                    val step = diff / 4
                    for (w in 0..3) {
                        val segStart = timeRange.startTime + (w * step)
                        val segEnd = minOf(timeRange.endTime, segStart + step)
                        val label = DataFormatter.formatDateShort(segStart)

                        var mBytes = 0L
                        var wBytes = 0L

                        if (hasPermission) {
                            val (mTx, mRx) = networkStatsHelper.queryDeviceSummary(ConnectivityManager.TYPE_MOBILE, segStart, segEnd)
                            val (wTx, wRx) = networkStatsHelper.queryDeviceSummary(ConnectivityManager.TYPE_WIFI, segStart, segEnd)
                            mBytes = mTx + mRx
                            wBytes = wTx + wRx
                        } else {
                            val segUsage = _recentUsages.value.filter { it.timestamp in segStart..segEnd }
                            mBytes = segUsage.sumOf { it.totalMobileBytes }
                            wBytes = segUsage.sumOf { it.totalWifiBytes }
                        }

                        val totalMB = (mBytes + wBytes) / (1024.0 * 1024.0)
                        val mMB = mBytes / (1024.0 * 1024.0)
                        val wMB = wBytes / (1024.0 * 1024.0)
                        points.add(DailyChartPoint(label, totalMB, mMB, wMB, segStart))
                    }
                }
            }

            // Top apps in this range from real telemetry
            val topApps = if (hasPermission) {
                val telemetry = networkStatsHelper.queryNetworkTelemetry(timeRange)
                telemetry.appList.take(6)
            } else {
                // Fallback to saved entities
                val filteredUsages = _recentUsages.value.filter { it.timestamp in timeRange.startTime..timeRange.endTime }
                val targetUsages = if (filteredUsages.isNotEmpty()) filteredUsages else _recentUsages.value.take(15)
                val allAppsMap = mutableMapOf<String, AppDataUsage>()
                targetUsages.forEach { usage ->
                    val apps = AppDataUsage.deserializeList(usage.topAppsJson)
                    apps.forEach { app ->
                        val existing = allAppsMap[app.packageName]
                        if (existing == null) {
                            allAppsMap[app.packageName] = app
                        } else {
                            allAppsMap[app.packageName] = existing.copy(
                                mobileTxBytes = existing.mobileTxBytes + app.mobileTxBytes,
                                mobileRxBytes = existing.mobileRxBytes + app.mobileRxBytes,
                                wifiTxBytes = existing.wifiTxBytes + app.wifiTxBytes,
                                wifiRxBytes = existing.wifiRxBytes + app.wifiRxBytes,
                                totalBytes = existing.totalBytes + app.totalBytes,
                                txBytes = existing.txBytes + app.txBytes,
                                rxBytes = existing.rxBytes + app.rxBytes
                            )
                        }
                    }
                }
                val totalAppTraffic = allAppsMap.values.sumOf { it.totalBytes }.coerceAtLeast(1L)
                allAppsMap.values
                    .sortedByDescending { it.totalBytes }
                    .take(6)
                    .map { app ->
                        val pct = ((app.totalBytes.toDouble() / totalAppTraffic.toDouble()) * 100).toFloat()
                        app.copy(usagePercentage = pct)
                    }
            }

            withContext(Dispatchers.Main) {
                _chartPoints.value = points
                _appDistribution.value = topApps
            }
        }
    }

    fun exportAndShareCSV() {
        val list = _recentUsages.value
        if (list.isEmpty()) {
            _statusMessage.value = "No usage records available to export"
            return
        }
        val uri = csvExporter.exportToCSV(list)
        if (uri != null) {
            csvExporter.shareCSV(uri)
            _statusMessage.value = "CSV Telemetry Report generated successfully"
        } else {
            _statusMessage.value = "Failed to generate CSV export file"
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    fun getTotalPeriodMB(): Double = _chartPoints.value.sumOf { it.valueMB }

    fun getTotalMobileMB(): Double = _chartPoints.value.sumOf { it.mobileMB }

    fun getTotalWifiMB(): Double = _chartPoints.value.sumOf { it.wifiMB }

    fun getAverageMB(): Double {
        val size = _chartPoints.value.size
        return if (size > 0) getTotalPeriodMB() / size else 0.0
    }

    fun getPeakMB(): Double = _chartPoints.value.maxOfOrNull { it.valueMB } ?: 0.0
}
