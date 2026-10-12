package com.example.dataguard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dataguard.DataGuardApp
import com.example.dataguard.data.entity.BillingCycleType
import com.example.dataguard.data.entity.DataPlanEntity
import com.example.dataguard.data.entity.NotificationHistoryEntity
import com.example.dataguard.data.entity.NotificationPreferencesEntity
import com.example.dataguard.data.entity.PlanTrafficScope
import com.example.dataguard.data.repository.DataRepository
import com.example.dataguard.domain.plan.DataPlanStatus
import com.example.dataguard.utils.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlanAndAlertViewModel(
    private val repository: DataRepository,
    private val permissionHelper: PermissionHelper
) : ViewModel() {

    // Plan Configuration & Status
    private val _currentPlan = MutableStateFlow(DataPlanEntity())
    val currentPlan: StateFlow<DataPlanEntity> = _currentPlan.asStateFlow()

    private val _planStatus = MutableStateFlow<DataPlanStatus?>(null)
    val planStatus: StateFlow<DataPlanStatus?> = _planStatus.asStateFlow()

    // Notification Preferences
    private val _notificationPreferences = MutableStateFlow(NotificationPreferencesEntity())
    val notificationPreferences: StateFlow<NotificationPreferencesEntity> = _notificationPreferences.asStateFlow()

    // Notification History
    val notificationHistory: StateFlow<List<NotificationHistoryEntity>> =
        repository.getNotificationHistoryFlow()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedHistoryCategory = MutableStateFlow("ALL")
    val selectedHistoryCategory: StateFlow<String> = _selectedHistoryCategory.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            val plan = repository.getDataPlan() ?: DataPlanEntity().also {
                repository.saveOrUpdateDataPlan(it)
            }
            _currentPlan.value = plan

            val prefs = repository.getNotificationPreferences() ?: NotificationPreferencesEntity().also {
                repository.saveNotificationPreferences(it)
            }
            _notificationPreferences.value = prefs

            refreshPlanStatus()
        }
    }

    fun refreshPlanStatus() {
        viewModelScope.launch {
            try {
                val status = DataGuardApp.instance.dataPlanAlertManager.evaluatePlanAndAlerts()
                _planStatus.value = status
            } catch (e: Exception) {
                _toastMessage.value = e.message
            }
        }
    }

    // Save Data Plan
    fun savePlan(
        name: String,
        allowanceGB: Double,
        cycleType: BillingCycleType,
        trafficScope: PlanTrafficScope,
        cycleStartDay: Int,
        customDays: Int,
        rolloverGB: Double,
        warningThresholds: List<Int>,
        excludedPackages: List<String>
    ) {
        viewModelScope.launch {
            val allowanceBytes = (allowanceGB * 1024.0 * 1024.0 * 1024.0).toLong()
            val rolloverBytes = (rolloverGB * 1024.0 * 1024.0 * 1024.0).toLong()
            val thresholdsStr = warningThresholds.sorted().joinToString(",")
            val excludedStr = excludedPackages.joinToString(",")

            val updated = _currentPlan.value.copy(
                planName = name.ifBlank { "Carrier Plan" },
                totalAllowanceBytes = allowanceBytes,
                rolloverBytes = rolloverBytes,
                billingCycle = cycleType.name,
                trafficScope = trafficScope.name,
                cycleStartDayOfMonth = cycleStartDay.coerceIn(1, 28),
                customCycleDays = customDays.coerceAtLeast(1),
                warningThresholdsJson = thresholdsStr,
                excludedPackageNamesJson = excludedStr,
                lastNotifiedThreshold = 0 // reset notified status for new/updated plan
            )

            repository.saveOrUpdateDataPlan(updated)
            _currentPlan.value = updated
            refreshPlanStatus()
            _toastMessage.value = "Data plan '${updated.planName}' saved successfully"
        }
    }

    // Preset Carrier Plan Shortcuts
    fun applyPresetMtnMonthly() {
        savePlan(
            name = "MTN Monthly Plan",
            allowanceGB = 10.0,
            cycleType = BillingCycleType.MONTHLY,
            trafficScope = PlanTrafficScope.MOBILE_ONLY,
            cycleStartDay = 1,
            customDays = 30,
            rolloverGB = 0.0,
            warningThresholds = listOf(50, 75, 90, 100),
            excludedPackages = emptyList()
        )
    }

    fun applyPresetAirtelWeekly() {
        savePlan(
            name = "Airtel Weekly",
            allowanceGB = 5.0,
            cycleType = BillingCycleType.WEEKLY,
            trafficScope = PlanTrafficScope.MOBILE_ONLY,
            cycleStartDay = 1,
            customDays = 7,
            rolloverGB = 1.0,
            warningThresholds = listOf(50, 75, 90, 100),
            excludedPackages = emptyList()
        )
    }

    // Update Notification Preferences
    fun updatePreferences(prefs: NotificationPreferencesEntity) {
        viewModelScope.launch {
            repository.saveNotificationPreferences(prefs)
            _notificationPreferences.value = prefs
            _toastMessage.value = "Alert preferences updated"
        }
    }

    fun setMasterNotifications(enabled: Boolean) {
        updatePreferences(_notificationPreferences.value.copy(isEnabledMaster = enabled))
    }

    fun toggleThresholdAlert(percent: Int, enabled: Boolean) {
        val current = _notificationPreferences.value
        val updated = when (percent) {
            50 -> current.copy(alertOn50Percent = enabled)
            75 -> current.copy(alertOn75Percent = enabled)
            90 -> current.copy(alertOn90Percent = enabled)
            100 -> current.copy(alertOn100Percent = enabled)
            else -> current
        }
        updatePreferences(updated)
    }

    fun setAppExceedsThreshold(enabled: Boolean, thresholdMb: Long) {
        updatePreferences(
            _notificationPreferences.value.copy(
                alertAppExceedsMb = enabled,
                appExceedsThresholdMb = thresholdMb
            )
        )
    }

    fun setDomainExceedsThreshold(enabled: Boolean, thresholdMb: Long) {
        updatePreferences(
            _notificationPreferences.value.copy(
                alertDomainExceedsMb = enabled,
                domainExceedsThresholdMb = thresholdMb
            )
        )
    }

    fun setHotspotExceedsThreshold(enabled: Boolean, thresholdMb: Long) {
        updatePreferences(
            _notificationPreferences.value.copy(
                alertHotspotExceedsMb = enabled,
                hotspotExceedsThresholdMb = thresholdMb
            )
        )
    }

    fun setSpikeAlert(enabled: Boolean, speedThresholdKbps: Long) {
        updatePreferences(
            _notificationPreferences.value.copy(
                alertUnusualSpike = enabled,
                spikeSpeedThresholdKbps = speedThresholdKbps
            )
        )
    }

    fun setNewAppAlert(enabled: Boolean) {
        updatePreferences(_notificationPreferences.value.copy(alertNewlyObservedApp = enabled))
    }

    fun setNewDomainAlert(enabled: Boolean) {
        updatePreferences(_notificationPreferences.value.copy(alertNewlyObservedDomain = enabled))
    }

    fun setFirewallBlockAlert(enabled: Boolean) {
        updatePreferences(_notificationPreferences.value.copy(alertFirewallBlockEvent = enabled))
    }

    fun setHistoryCategoryFilter(category: String) {
        _selectedHistoryCategory.value = category
    }

    fun clearNotificationHistory() {
        viewModelScope.launch {
            repository.clearAllNotificationHistory()
            _toastMessage.value = "Notification history cleared"
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsAsRead()
        }
    }

    fun clearMessage() {
        _toastMessage.value = null
    }
}
