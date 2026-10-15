package com.example.dataguard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dataguard.DataGuardApp
import com.example.dataguard.data.entity.AppDataUsage
import com.example.dataguard.data.entity.AppLimitEntity
import com.example.dataguard.data.entity.BlockedAppEntity
import com.example.dataguard.data.entity.QuotaCycleType
import com.example.dataguard.data.entity.QuotaEnforcementMode
import com.example.dataguard.data.repository.DataRepository
import com.example.dataguard.domain.model.AppQuotaCalculation
import com.example.dataguard.domain.model.TimeRangeSelection
import com.example.dataguard.domain.model.TimeRangeType
import com.example.dataguard.domain.usecase.AppQuotaManager
import com.example.dataguard.domain.usecase.BlockAppDataUseCase
import com.example.dataguard.domain.usecase.GetUsageStatsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppSortOption(val label: String) {
    TOTAL_USAGE("Total"),
    MOBILE_DATA("Cellular"),
    WIFI_DATA("Wi-Fi"),
    DOWNLOAD_RX("Download"),
    UPLOAD_TX("Upload"),
    NAME("Name")
}

class AppsViewModel(
    private val getUsageStatsUseCase: GetUsageStatsUseCase,
    private val blockAppDataUseCase: BlockAppDataUseCase,
    private val repository: DataRepository,
    private val appQuotaManager: AppQuotaManager
) : ViewModel() {

    private val _allApps = MutableStateFlow<List<AppDataUsage>>(emptyList())
    val allApps: StateFlow<List<AppDataUsage>> = _allApps.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(TimeRangeSelection.today())
    val selectedTimeRange: StateFlow<TimeRangeSelection> = _selectedTimeRange.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(AppSortOption.TOTAL_USAGE)
    val sortOption: StateFlow<AppSortOption> = _sortOption.asStateFlow()

    private val _showSystemApps = MutableStateFlow(true)
    val showSystemApps: StateFlow<Boolean> = _showSystemApps.asStateFlow()

    fun toggleShowSystemApps() {
        _showSystemApps.value = !_showSystemApps.value
    }

    fun setShowSystemApps(show: Boolean) {
        _showSystemApps.value = show
    }

    private val _blockedApps = MutableStateFlow<List<BlockedAppEntity>>(emptyList())
    val blockedApps: StateFlow<List<BlockedAppEntity>> = _blockedApps.asStateFlow()

    // Map of UID -> AppLimitEntity for fast lookup in list cards
    private val _appLimits = MutableStateFlow<Map<Int, AppLimitEntity>>(emptyMap())
    val appLimits: StateFlow<Map<Int, AppLimitEntity>> = _appLimits.asStateFlow()

    // Map of UID -> AppQuotaCalculation for quick status badges in the list
    private val _quotaCalculations = MutableStateFlow<Map<Int, AppQuotaCalculation>>(emptyMap())
    val quotaCalculations: StateFlow<Map<Int, AppQuotaCalculation>> = _quotaCalculations.asStateFlow()

    // Currently inspected app in the detail screen
    private val _selectedAppForDetail = MutableStateFlow<AppDataUsage?>(null)
    val selectedAppForDetail: StateFlow<AppDataUsage?> = _selectedAppForDetail.asStateFlow()

    private val _selectedAppQuotaCalculation = MutableStateFlow<AppQuotaCalculation?>(null)
    val selectedAppQuotaCalculation: StateFlow<AppQuotaCalculation?> = _selectedAppQuotaCalculation.asStateFlow()

    private val _showDetailDialog = MutableStateFlow(false)
    val showDetailDialog: StateFlow<Boolean> = _showDetailDialog.asStateFlow()

    private val _showQuotaEditor = MutableStateFlow(false)
    val showQuotaEditor: StateFlow<Boolean> = _showQuotaEditor.asStateFlow()

    private val _isFromNetworkStats = MutableStateFlow(true)
    val isFromNetworkStats: StateFlow<Boolean> = _isFromNetworkStats.asStateFlow()

    private val _hasUsageStatsPermission = MutableStateFlow(true)
    val hasUsageStatsPermission: StateFlow<Boolean> = _hasUsageStatsPermission.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        checkPermission()
        loadApps()
        observeBlockedApps()
        observeAppLimits()
    }

    fun checkPermission() {
        val perm = DataGuardApp.instance.permissionHelper.hasPackageUsageStatsPermission()
        _hasUsageStatsPermission.value = perm
    }

    private fun observeBlockedApps() {
        viewModelScope.launch {
            repository.getAllBlockedApps().collect { blockedList ->
                _blockedApps.value = blockedList
            }
        }
    }

    private fun observeAppLimits() {
        viewModelScope.launch {
            repository.getAllAppLimits().collect { limitsList ->
                val map = limitsList.associateBy { it.uid }
                _appLimits.value = map

                // Calculate quota status for each app with limit
                val calcMap = mutableMapOf<Int, AppQuotaCalculation>()
                for (limit in limitsList) {
                    calcMap[limit.uid] = appQuotaManager.evaluateQuota(limit)
                }
                _quotaCalculations.value = calcMap

                // If currently inspecting an app, refresh its quota calculation
                _selectedAppForDetail.value?.let { currentApp ->
                    val limit = map[currentApp.uid]
                    if (limit != null) {
                        _selectedAppQuotaCalculation.value = appQuotaManager.evaluateQuota(limit)
                    } else {
                        _selectedAppQuotaCalculation.value = null
                    }
                }
            }
        }
    }

    fun setTimeRange(range: TimeRangeSelection) {
        _selectedTimeRange.value = range
        loadApps()
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
        loadApps()
    }

    fun setCustomRange(startMillis: Long, endMillis: Long) {
        _selectedTimeRange.value = TimeRangeSelection.custom(startMillis, endMillis)
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            checkPermission()
            try {
                when (val result = getUsageStatsUseCase(_selectedTimeRange.value)) {
                    is GetUsageStatsUseCase.Result.Success -> {
                        _allApps.value = result.topApps
                        _isFromNetworkStats.value = result.isFromNetworkStatsManager
                        // Re-evaluate quota calculations with updated app telemetry
                        refreshQuotaCalculations()
                    }
                    is GetUsageStatsUseCase.Result.Error -> {
                        _errorMessage.value = result.exception.message
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun refreshQuotaCalculations() {
        val limits = _appLimits.value.values
        val calcMap = mutableMapOf<Int, AppQuotaCalculation>()
        for (limit in limits) {
            calcMap[limit.uid] = appQuotaManager.evaluateQuota(limit)
        }
        _quotaCalculations.value = calcMap

        _selectedAppForDetail.value?.let { app ->
            val limit = _appLimits.value[app.uid]
            if (limit != null) {
                _selectedAppQuotaCalculation.value = appQuotaManager.evaluateQuota(limit)
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(option: AppSortOption) {
        _sortOption.value = option
    }

    fun toggleBlockApp(
        app: AppDataUsage,
        shouldBlock: Boolean,
        reason: String = "User firewall rule",
        blockMobile: Boolean = true,
        blockWifi: Boolean = true
    ) {
        viewModelScope.launch {
            val result = blockAppDataUseCase(
                uid = app.uid,
                packageName = app.packageName,
                appName = app.appName,
                block = shouldBlock,
                reason = reason,
                blockMobile = blockMobile,
                blockWifi = blockWifi
            )
            if (result is BlockAppDataUseCase.Result.Error) {
                _errorMessage.value = result.message
            } else {
                // Dynamically signal running VPN session to reload firewall rules
                DataGuardApp.instance.vpnSessionManager.reloadFirewallRules()
            }
        }
    }

    fun isAppBlocked(uid: Int): Boolean {
        return _blockedApps.value.any { it.uid == uid }
    }

    fun openAppSettings(packageName: String) {
        blockAppDataUseCase.openSystemAppRestrictions(packageName)
    }

    // --- Application Detail & Quota Management ---

    fun openAppDetail(app: AppDataUsage) {
        _selectedAppForDetail.value = app
        val limit = _appLimits.value[app.uid]
        if (limit != null) {
            _selectedAppQuotaCalculation.value = appQuotaManager.evaluateQuota(limit)
        } else {
            _selectedAppQuotaCalculation.value = null
        }
        _showDetailDialog.value = true
    }

    fun closeAppDetail() {
        _showDetailDialog.value = false
        _selectedAppForDetail.value = null
        _selectedAppQuotaCalculation.value = null
    }

    fun openQuotaEditor() {
        _showQuotaEditor.value = true
    }

    fun closeQuotaEditor() {
        _showQuotaEditor.value = false
    }

    fun saveQuota(
        limitBytes: Long,
        warningThresholdPercent: Int,
        enforcementMode: QuotaEnforcementMode,
        cycleType: QuotaCycleType,
        cycleStartDay: Int = 1,
        customCycleDays: Int = 30,
        isEnabled: Boolean = true
    ) {
        val app = _selectedAppForDetail.value ?: return
        viewModelScope.launch {
            try {
                val existing = repository.getAppLimit(app.uid)
                val newLimit = AppLimitEntity(
                    uid = app.uid,
                    packageName = app.packageName,
                    appName = app.appName,
                    limitBytes = limitBytes,
                    warningThresholdPercent = warningThresholdPercent,
                    enforcementMode = enforcementMode.name,
                    cycleType = cycleType.name,
                    cycleStartDay = cycleStartDay,
                    cycleStartTimestamp = existing?.cycleStartTimestamp ?: System.currentTimeMillis(),
                    customCycleDays = customCycleDays,
                    isEnabled = isEnabled,
                    lastWarningNotifiedAt = existing?.lastWarningNotifiedAt ?: 0L,
                    lastLimitNotifiedAt = existing?.lastLimitNotifiedAt ?: 0L,
                    isLimitReached = false,
                    dailyLimitMB = if (cycleType == QuotaCycleType.DAILY) limitBytes / (1024 * 1024) else 0L,
                    weeklyLimitMB = if (cycleType == QuotaCycleType.WEEKLY) limitBytes / (1024 * 1024) else 0L,
                    monthlyLimitMB = if (cycleType == QuotaCycleType.MONTHLY) limitBytes / (1024 * 1024) else 0L
                )

                val calc = appQuotaManager.saveOrUpdateLimit(newLimit)
                _selectedAppQuotaCalculation.value = calc
                _showQuotaEditor.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save quota: ${e.message}"
            }
        }
    }

    fun resetAppQuotaCycle(uid: Int) {
        viewModelScope.launch {
            try {
                appQuotaManager.resetCycleUsage(uid)
                val updated = repository.getAppLimit(uid)
                if (updated != null) {
                    _selectedAppQuotaCalculation.value = appQuotaManager.evaluateQuota(updated)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to reset quota cycle: ${e.message}"
            }
        }
    }

    fun deleteAppQuota(uid: Int) {
        val app = _selectedAppForDetail.value ?: return
        viewModelScope.launch {
            try {
                appQuotaManager.deleteLimit(uid, app.packageName, app.appName)
                _selectedAppQuotaCalculation.value = null
                _showQuotaEditor.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete quota: ${e.message}"
            }
        }
    }

    fun toggleQuotaEnabled(uid: Int, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val limit = repository.getAppLimit(uid) ?: return@launch
                val updated = limit.copy(isEnabled = enabled)
                val calc = appQuotaManager.saveOrUpdateLimit(updated)
                _selectedAppQuotaCalculation.value = calc
            } catch (e: Exception) {
                _errorMessage.value = "Failed to toggle quota: ${e.message}"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
