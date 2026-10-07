package com.example.dataguard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dataguard.DataGuardApp
import com.example.dataguard.data.entity.UsageEntity
import com.example.dataguard.data.repository.DataRepository
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

    val liveTxSpeed: StateFlow<Long> = DataMonitorService.liveTxSpeed
    val liveRxSpeed: StateFlow<Long> = DataMonitorService.liveRxSpeed

    val dailyLimitMB: Long
        get() = DataGuardApp.instance.dailyLimitMB

    val weeklyLimitMB: Long
        get() = DataGuardApp.instance.weeklyLimitMB

    val monthlyLimitMB: Long
        get() = DataGuardApp.instance.monthlyLimitMB

    init {
        loadData()
        observeDatabase()
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
            try {
                getUsageStatsUseCase("daily")
                val usage = repository.getLatestUsage().firstOrNull()
                _latestUsage.value = usage
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
            // Add a simulated data entry to demo live graphs & alerts
            val simulatedTx = (15L * 1024 * 1024)
            val simulatedRx = (85L * 1024 * 1024)
            val prev = _latestUsage.value
            val newUsage = UsageEntity(
                timestamp = System.currentTimeMillis(),
                totalMobileTx = (prev?.totalMobileTx ?: 0L) + simulatedTx,
                totalMobileRx = (prev?.totalMobileRx ?: 0L) + simulatedRx,
                topAppsJson = prev?.topAppsJson ?: "",
                period = "daily"
            )
            repository.insertUsage(newUsage)
            _latestUsage.value = newUsage
            DataGuardApp.instance.notificationHelper.showHighUsageNotification(
                (newUsage.totalMobileTx + newUsage.totalMobileRx),
                DataGuardApp.instance.dailyLimitBytes,
                "Daily"
            )
        }
    }

    fun clearMessage() {
        _userMessage.value = null
    }

    fun getTotalDailyUsageMB(): Double {
        val latest = _latestUsage.value
        val listTotal = _dailyUsage.value.sumOf { it.totalMobileTx + it.totalMobileRx }
        val bytes = if (latest != null) {
            maxOf(latest.totalMobileTx + latest.totalMobileRx, listTotal)
        } else listTotal
        return bytes / (1024.0 * 1024.0)
    }

    fun getTotalWeeklyUsageMB(): Double {
        val total = _weeklyUsage.value.sumOf { it.totalMobileTx + it.totalMobileRx }
        return (total.coerceAtLeast((getTotalDailyUsageMB() * 1024 * 1024).toLong())) / (1024.0 * 1024.0)
    }

    fun getTotalMonthlyUsageMB(): Double {
        val total = _monthlyUsage.value.sumOf { it.totalMobileTx + it.totalMobileRx }
        return (total.coerceAtLeast((getTotalWeeklyUsageMB() * 1024 * 1024).toLong())) / (1024.0 * 1024.0)
    }
}
