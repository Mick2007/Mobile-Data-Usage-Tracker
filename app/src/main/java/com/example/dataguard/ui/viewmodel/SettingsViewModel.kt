package com.example.dataguard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dataguard.DataGuardApp
import com.example.dataguard.data.repository.DataRepository
import com.example.dataguard.utils.DataLimitUnit
import com.example.dataguard.utils.DataUnitUtils
import com.example.dataguard.utils.PermissionHelper
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: DataRepository,
    private val permissionHelper: PermissionHelper
) : ViewModel() {

    val currentThemeMode: StateFlow<AppThemeMode> = DataGuardApp.instance.appThemeMode

    fun selectThemeMode(mode: AppThemeMode) {
        DataGuardApp.instance.setAppThemeMode(mode)
        _toastMessage.value = "Theme updated to ${mode.title}"
    }

    private val _hasUsageStatsPermission = MutableStateFlow(false)
    val hasUsageStatsPermission: StateFlow<Boolean> = _hasUsageStatsPermission.asStateFlow()

    private val _hasBatteryOptimizationExemption = MutableStateFlow(false)
    val hasBatteryOptimizationExemption: StateFlow<Boolean> = _hasBatteryOptimizationExemption.asStateFlow()

    private val _hasNotificationPermission = MutableStateFlow(false)
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

    // Canonical bytes
    private val _dailyLimitBytes = MutableStateFlow(DataGuardApp.instance.dailyLimitBytes)
    val dailyLimitBytes: StateFlow<Long> = _dailyLimitBytes.asStateFlow()

    private val _weeklyLimitBytes = MutableStateFlow(DataGuardApp.instance.weeklyLimitBytes)
    val weeklyLimitBytes: StateFlow<Long> = _weeklyLimitBytes.asStateFlow()

    private val _monthlyLimitBytes = MutableStateFlow(DataGuardApp.instance.monthlyLimitBytes)
    val monthlyLimitBytes: StateFlow<Long> = _monthlyLimitBytes.asStateFlow()

    // Unit selections
    private val _dailyUnit = MutableStateFlow(DataLimitUnit.fromString(DataGuardApp.instance.dailyLimitUnit))
    val dailyUnit: StateFlow<DataLimitUnit> = _dailyUnit.asStateFlow()

    private val _weeklyUnit = MutableStateFlow(DataLimitUnit.fromString(DataGuardApp.instance.weeklyLimitUnit))
    val weeklyUnit: StateFlow<DataLimitUnit> = _weeklyUnit.asStateFlow()

    private val _monthlyUnit = MutableStateFlow(DataLimitUnit.fromString(DataGuardApp.instance.monthlyLimitUnit))
    val monthlyUnit: StateFlow<DataLimitUnit> = _monthlyUnit.asStateFlow()

    // Text inputs
    private val _dailyInputText = MutableStateFlow(
        DataUnitUtils.formatLimitValue(DataUnitUtils.bytesToLimitValue(_dailyLimitBytes.value, _dailyUnit.value))
    )
    val dailyInputText: StateFlow<String> = _dailyInputText.asStateFlow()

    private val _weeklyInputText = MutableStateFlow(
        DataUnitUtils.formatLimitValue(DataUnitUtils.bytesToLimitValue(_weeklyLimitBytes.value, _weeklyUnit.value))
    )
    val weeklyInputText: StateFlow<String> = _weeklyInputText.asStateFlow()

    private val _monthlyInputText = MutableStateFlow(
        DataUnitUtils.formatLimitValue(DataUnitUtils.bytesToLimitValue(_monthlyLimitBytes.value, _monthlyUnit.value))
    )
    val monthlyInputText: StateFlow<String> = _monthlyInputText.asStateFlow()

    // Input errors
    private val _dailyInputError = MutableStateFlow<String?>(null)
    val dailyInputError: StateFlow<String?> = _dailyInputError.asStateFlow()

    private val _weeklyInputError = MutableStateFlow<String?>(null)
    val weeklyInputError: StateFlow<String?> = _weeklyInputError.asStateFlow()

    private val _monthlyInputError = MutableStateFlow<String?>(null)
    val monthlyInputError: StateFlow<String?> = _monthlyInputError.asStateFlow()

    // Backwards-compatible MB values
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

    fun updateDailyLimit(text: String) {
        _dailyInputText.value = text
        if (text.isBlank()) {
            // When daily limit is not inserted, fall back to previous day limit
            val prevBytes = DataGuardApp.instance.previousDayLimitBytes
            val prevValue = DataUnitUtils.bytesToLimitValue(prevBytes, _dailyUnit.value)
            val prevFormatted = DataUnitUtils.formatLimitValue(prevValue)
            _dailyInputError.value = "Not inserted. Using previous day limit ($prevFormatted ${_dailyUnit.value.name})"
            _dailyLimitBytes.value = prevBytes
            _dailyLimitMB.value = prevBytes / (1024L * 1024L)
            DataGuardApp.instance.dailyLimitBytes = prevBytes
            return
        }

        val (parsed, error) = DataUnitUtils.validateInput(text)
        _dailyInputError.value = error
        if (parsed != null && error == null) {
            val bytes = DataUnitUtils.limitToBytes(parsed, _dailyUnit.value)
            _dailyLimitBytes.value = bytes
            _dailyLimitMB.value = bytes / (1024L * 1024L)
            DataGuardApp.instance.dailyLimitBytes = bytes
        }
    }

    fun applyPreviousDayLimit() {
        val prevBytes = DataGuardApp.instance.previousDayLimitBytes
        val prevValue = DataUnitUtils.bytesToLimitValue(prevBytes, _dailyUnit.value)
        val prevFormatted = DataUnitUtils.formatLimitValue(prevValue)
        _dailyInputText.value = prevFormatted
        _dailyInputError.value = null
        _dailyLimitBytes.value = prevBytes
        _dailyLimitMB.value = prevBytes / (1024L * 1024L)
        DataGuardApp.instance.dailyLimitBytes = prevBytes
    }

    fun changeDailyUnit(newUnit: DataLimitUnit) {
        if (_dailyUnit.value == newUnit) return
        val currentBytes = _dailyLimitBytes.value
        val converted = DataUnitUtils.bytesToLimitValue(currentBytes, newUnit)
        val formatted = DataUnitUtils.formatLimitValue(converted)
        _dailyUnit.value = newUnit
        _dailyInputText.value = formatted
        _dailyInputError.value = null
        DataGuardApp.instance.dailyLimitUnit = newUnit.name
    }

    fun updateWeeklyLimit(text: String) {
        _weeklyInputText.value = text
        val (parsed, error) = DataUnitUtils.validateInput(text)
        _weeklyInputError.value = error
        if (parsed != null && error == null) {
            val bytes = DataUnitUtils.limitToBytes(parsed, _weeklyUnit.value)
            _weeklyLimitBytes.value = bytes
            _weeklyLimitMB.value = bytes / (1024L * 1024L)
            DataGuardApp.instance.weeklyLimitBytes = bytes
        }
    }

    fun changeWeeklyUnit(newUnit: DataLimitUnit) {
        if (_weeklyUnit.value == newUnit) return
        val currentBytes = _weeklyLimitBytes.value
        val converted = DataUnitUtils.bytesToLimitValue(currentBytes, newUnit)
        val formatted = DataUnitUtils.formatLimitValue(converted)
        _weeklyUnit.value = newUnit
        _weeklyInputText.value = formatted
        _weeklyInputError.value = null
        DataGuardApp.instance.weeklyLimitUnit = newUnit.name
    }

    fun updateMonthlyLimit(text: String) {
        _monthlyInputText.value = text
        val (parsed, error) = DataUnitUtils.validateInput(text)
        _monthlyInputError.value = error
        if (parsed != null && error == null) {
            val bytes = DataUnitUtils.limitToBytes(parsed, _monthlyUnit.value)
            _monthlyLimitBytes.value = bytes
            _monthlyLimitMB.value = bytes / (1024L * 1024L)
            DataGuardApp.instance.monthlyLimitBytes = bytes
        }
    }

    fun changeMonthlyUnit(newUnit: DataLimitUnit) {
        if (_monthlyUnit.value == newUnit) return
        val currentBytes = _monthlyLimitBytes.value
        val converted = DataUnitUtils.bytesToLimitValue(currentBytes, newUnit)
        val formatted = DataUnitUtils.formatLimitValue(converted)
        _monthlyUnit.value = newUnit
        _monthlyInputText.value = formatted
        _monthlyInputError.value = null
        DataGuardApp.instance.monthlyLimitUnit = newUnit.name
    }

    // Backwards-compatible helpers
    fun setDailyLimit(limitMB: Long) {
        _dailyLimitMB.value = limitMB
        val bytes = limitMB * 1024L * 1024L
        _dailyLimitBytes.value = bytes
        DataGuardApp.instance.dailyLimitBytes = bytes
        val valueInUnit = DataUnitUtils.bytesToLimitValue(bytes, _dailyUnit.value)
        _dailyInputText.value = DataUnitUtils.formatLimitValue(valueInUnit)
        _dailyInputError.value = null
    }

    fun setWeeklyLimit(limitMB: Long) {
        _weeklyLimitMB.value = limitMB
        val bytes = limitMB * 1024L * 1024L
        _weeklyLimitBytes.value = bytes
        DataGuardApp.instance.weeklyLimitBytes = bytes
        val valueInUnit = DataUnitUtils.bytesToLimitValue(bytes, _weeklyUnit.value)
        _weeklyInputText.value = DataUnitUtils.formatLimitValue(valueInUnit)
        _weeklyInputError.value = null
    }

    fun setMonthlyLimit(limitMB: Long) {
        _monthlyLimitMB.value = limitMB
        val bytes = limitMB * 1024L * 1024L
        _monthlyLimitBytes.value = bytes
        DataGuardApp.instance.monthlyLimitBytes = bytes
        val valueInUnit = DataUnitUtils.bytesToLimitValue(bytes, _monthlyUnit.value)
        _monthlyInputText.value = DataUnitUtils.formatLimitValue(valueInUnit)
        _monthlyInputError.value = null
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
