package com.example.dataguard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dataguard.DataGuardApp
import com.example.dataguard.data.repository.DataRepository
import com.example.dataguard.utils.PermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: DataRepository,
    private val permissionHelper: PermissionHelper
) : ViewModel() {

    private val _hasUsageStatsPermission = MutableStateFlow(false)
    val hasUsageStatsPermission: StateFlow<Boolean> = _hasUsageStatsPermission.asStateFlow()

    private val _hasBatteryOptimizationExemption = MutableStateFlow(false)
    val hasBatteryOptimizationExemption: StateFlow<Boolean> = _hasBatteryOptimizationExemption.asStateFlow()

    private val _hasNotificationPermission = MutableStateFlow(false)
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

    private val _dailyLimitMB = MutableStateFlow(DataGuardApp.instance.dailyLimitMB)
    val dailyLimitMB: StateFlow<Long> = _dailyLimitMB.asStateFlow()

    private val _weeklyLimitMB = MutableStateFlow(DataGuardApp.instance.weeklyLimitMB)
    val weeklyLimitMB: StateFlow<Long> = _weeklyLimitMB.asStateFlow()

    private val _monthlyLimitMB = MutableStateFlow(DataGuardApp.instance.monthlyLimitMB)
    val monthlyLimitMB: StateFlow<Long> = _monthlyLimitMB.asStateFlow()

    private val _isAutoIdleSaverEnabled = MutableStateFlow(DataGuardApp.instance.isAutoIdleSaverEnabled)
    val isAutoIdleSaverEnabled: StateFlow<Boolean> = _isAutoIdleSaverEnabled.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        checkPermissions()
    }

    fun checkPermissions() {
        _hasUsageStatsPermission.value = permissionHelper.hasPackageUsageStatsPermission()
        _hasBatteryOptimizationExemption.value = permissionHelper.isBatteryOptimizationIgnored()
        _hasNotificationPermission.value = permissionHelper.hasNotificationPermission()
    }

    fun requestUsageStatsPermission() {
        permissionHelper.requestPackageUsageStatsPermission()
        recheckAfterDelay()
    }

    fun requestBatteryOptimizationExemption() {
        permissionHelper.requestBatteryOptimizationExemption()
        recheckAfterDelay()
    }

    fun requestNotificationPermission() {
        permissionHelper.requestNotificationPermission()
        recheckAfterDelay()
    }

    private fun recheckAfterDelay() {
        viewModelScope.launch {
            delay(1000)
            checkPermissions()
        }
    }

    fun setDailyLimit(limitMB: Long) {
        _dailyLimitMB.value = limitMB
        DataGuardApp.instance.dailyLimitMB = limitMB
    }

    fun setWeeklyLimit(limitMB: Long) {
        _weeklyLimitMB.value = limitMB
        DataGuardApp.instance.weeklyLimitMB = limitMB
    }

    fun setMonthlyLimit(limitMB: Long) {
        _monthlyLimitMB.value = limitMB
        DataGuardApp.instance.monthlyLimitMB = limitMB
    }

    fun setAutoIdleSaver(enabled: Boolean) {
        _isAutoIdleSaverEnabled.value = enabled
        DataGuardApp.instance.isAutoIdleSaverEnabled = enabled
    }

    fun resetAllStats() {
        viewModelScope.launch {
            repository.clearAllUsage()
            repository.clearAllBlockedApps()
            repository.clearAllAppLimits()
            repository.clearAllHistory()
            _toastMessage.value = "All usage logs and history cleared"
        }
    }

    fun clearMessage() {
        _toastMessage.value = null
    }
}
