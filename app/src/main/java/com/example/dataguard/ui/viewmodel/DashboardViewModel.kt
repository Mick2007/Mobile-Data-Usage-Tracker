package com.example.dataguard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dataguard.DataGuardApp
import com.example.dataguard.data.entity.UsageEntity
import com.example.dataguard.data.repository.DataRepository
import com.example.dataguard.domain.model.TimeRangeSelection
import com.example.dataguard.domain.usecase.GetUsageStatsUseCase
import com.example.dataguard.domain.usecase.ToggleMobileDataUseCase
import com.example.dataguard.service.DataMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val getUsageStatsUseCase: GetUsageStatsUseCase,
    private val toggleMobileDataUseCase: ToggleMobileDataUseCase,
    private val repository: DataRepository
) : ViewModel() {

    private val _latestUsage = MutableStateFlow<UsageEntity?>(null)
    val latestUsage: StateFlow<UsageEntity?> = _latestUsage.asStateFlow()

    private val _dailyUsage = MutableStateFlow<List<UsageEntity>>(emptyList())
    val dailyUsage: StateFlow<List<UsageEntity>> = _dailyUsage.asStateFlow()

    private val _weeklyUsage = MutableStateFlow<List<UsageEntity>>(emptyList())
    val weeklyUsage: StateFlow<List<UsageEntity>> = _weeklyUsage.asStateFlow()

    private val _monthlyUsage = MutableStateFlow<List<UsageEntity>>(emptyList())
    val monthlyUsage: StateFlow<List<UsageEntity>> = _monthlyUsage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _hasUsageStatsPermission = MutableStateFlow(true)
    val hasUsageStatsPermission: StateFlow<Boolean> = _hasUsageStatsPermission.asStateFlow()

    private val _isFromNetworkStats = MutableStateFlow(true)
    val isFromNetworkStats: StateFlow<Boolean> = _isFromNetworkStats.asStateFlow()

    val liveTxSpeed: StateFlow<Long> = DataMonitorService.liveTxSpeed
    val liveRxSpeed: StateFlow<Long> = DataMonitorService.liveRxSpeed

    val dailyLimitMB: Long
        get() = DataGuardApp.instance.dailyLimitMB

    val weeklyLimitMB: Long
        get() = DataGuardApp.instance.weeklyLimitMB

    val monthlyLimitMB: Long
        get() = DataGuardApp.instance.monthlyLimitMB

    init {
        checkPermission()
        loadData()
        observeDatabase()
    }

    fun checkPermission() {
        val perm = DataGuardApp.instance.permissionHelper.hasPackageUsageStatsPermission()
        _hasUsageStatsPermission.value = perm
    }

    private fun observeDatabase() {
        viewModelScope.launch {
            repository.getLatestUsage().collect { usage ->
                _latestUsage.value = usage
            }
        }
        viewModelScope.launch {
            repository.getUsageByPeriod("daily", 100).collect { list ->
                _dailyUsage.value = list
            }
        }
        viewModelScope.launch {
            repository.getUsageByPeriod("weekly", 100).collect { list ->
                _weeklyUsage.value = list
            }
        }
        viewModelScope.launch {
            repository.getUsageByPeriod("monthly", 100).collect { list ->
                _monthlyUsage.value = list
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            checkPermission()
            try {
                when (val result = getUsageStatsUseCase(TimeRangeSelection.today())) {
                    is GetUsageStatsUseCase.Result.Success -> {
                        _latestUsage.value = result.usage
                        _isFromNetworkStats.value = result.isFromNetworkStatsManager
                    }
                    is GetUsageStatsUseCase.Result.Error -> {
                        _userMessage.value = result.exception.message
                    }
                }
            } catch (e: Exception) {
                _userMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleMobileData(enabled: Boolean) {
        viewModelScope.launch {
            when (val result = toggleMobileDataUseCase(enabled, "dashboard_toggle")) {
                is ToggleMobileDataUseCase.Result.Success -> {
                    _userMessage.value = result.message
                }
                is ToggleMobileDataUseCase.Result.Error -> {
                    _userMessage.value = result.message
                }
            }
        }
    }

    fun simulateLiveTraffic() {
        viewModelScope.launch {
            _userMessage.value = "⚡ Simulating active cellular data burst..."
            val simulatedTx = (15L * 1024 * 1024)
            val simulatedRx = (85L * 1024 * 1024)
            val prev = _latestUsage.value
            val newUsage = UsageEntity(
                timestamp = System.currentTimeMillis(),
                totalMobileTx = (prev?.totalMobileTx ?: 0L) + simulatedTx,
                totalMobileRx = (prev?.totalMobileRx ?: 0L) + simulatedRx,
                totalWifiTx = prev?.totalWifiTx ?: (12L * 1024 * 1024),
                totalWifiRx = prev?.totalWifiRx ?: (48L * 1024 * 1024),
                topAppsJson = prev?.topAppsJson ?: "",
                period = "daily"
            )
            repository.insertUsage(newUsage)
            _latestUsage.value = newUsage
            DataGuardApp.instance.notificationHelper.showHighUsageNotification(
                newUsage.totalMobileBytes,
                DataGuardApp.instance.dailyLimitBytes,
                "Daily"
            )
        }
    }

    fun clearMessage() {
        _userMessage.value = null
    }

    fun getTotalDailyMobileMB(): Double {
        val latest = _latestUsage.value
        val listTotal = _dailyUsage.value.sumOf { it.totalMobileBytes }
        val bytes = if (latest != null) {
            maxOf(latest.totalMobileBytes, listTotal)
        } else listTotal
        return bytes / (1024.0 * 1024.0)
    }

    fun getTotalDailyWifiMB(): Double {
        val latest = _latestUsage.value
        val listTotal = _dailyUsage.value.sumOf { it.totalWifiBytes }
        val bytes = if (latest != null) {
            maxOf(latest.totalWifiBytes, listTotal)
        } else listTotal
        return bytes / (1024.0 * 1024.0)
    }

    fun getTotalDailyUsageMB(): Double = getTotalDailyMobileMB()

    fun getTotalWeeklyUsageMB(): Double {
        val total = _weeklyUsage.value.sumOf { it.totalMobileBytes }
        return (total.coerceAtLeast((getTotalDailyUsageMB() * 1024 * 1024).toLong())) / (1024.0 * 1024.0)
    }

    fun getTotalWeeklyWifiMB(): Double {
        val total = _weeklyUsage.value.sumOf { it.totalWifiBytes }
        return (total.coerceAtLeast((getTotalDailyWifiMB() * 1024 * 1024).toLong())) / (1024.0 * 1024.0)
    }

    fun getTotalMonthlyUsageMB(): Double {
        val total = _monthlyUsage.value.sumOf { it.totalMobileBytes }
        return (total.coerceAtLeast((getTotalWeeklyUsageMB() * 1024 * 1024).toLong())) / (1024.0 * 1024.0)
    }

    fun getTotalMonthlyWifiMB(): Double {
        val total = _monthlyUsage.value.sumOf { it.totalWifiBytes }
        return (total.coerceAtLeast((getTotalDailyWifiMB() * 1024 * 1024).toLong())) / (1024.0 * 1024.0)
    }

    val vpnState: StateFlow<com.example.dataguard.vpn.model.VpnState> =
        DataGuardApp.instance.vpnSessionManager.vpnState

    fun isVpnPrepared(context: android.content.Context): Boolean =
        DataGuardApp.instance.vpnSessionManager.isVpnPrepared(context)

    fun getVpnPrepareIntent(context: android.content.Context): android.content.Intent? =
        DataGuardApp.instance.vpnSessionManager.getPrepareIntent(context)

    fun startVpn(context: android.content.Context) {
        DataGuardApp.instance.vpnSessionManager.startVpn(context)
    }

    fun stopVpn(context: android.content.Context? = null) {
        val ctx = context ?: DataGuardApp.instance
        DataGuardApp.instance.vpnSessionManager.stopVpn(ctx)
    }

    fun setVpnAutoStartOnBoot(enabled: Boolean) {
        DataGuardApp.instance.vpnSessionManager.setAutoStartOnBoot(enabled)
    }
}
