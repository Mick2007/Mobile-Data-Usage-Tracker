package com.example.dataguard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dataguard.DataGuardApp
import com.example.dataguard.data.entity.AppDataUsage
import com.example.dataguard.data.entity.BlockedAppEntity
import com.example.dataguard.data.entity.BlockedTrafficEventEntity
import com.example.dataguard.data.repository.DataRepository
import com.example.dataguard.domain.model.TimeRangeSelection
import com.example.dataguard.domain.usecase.BlockAppDataUseCase
import com.example.dataguard.domain.usecase.GetUsageStatsUseCase
import com.example.dataguard.vpn.model.VpnState
import com.example.dataguard.vpn.model.VpnStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FirewallFilterOption(val label: String) {
    ALL("All Apps"),
    BLOCKED("Blocked"),
    ALLOWED("Allowed")
}

class FirewallViewModel(
    private val repository: DataRepository,
    private val blockAppDataUseCase: BlockAppDataUseCase,
    private val getUsageStatsUseCase: GetUsageStatsUseCase
) : ViewModel() {

    private val vpnSessionManager = DataGuardApp.instance.vpnSessionManager

    val vpnState: StateFlow<VpnState> = vpnSessionManager.vpnState

    private val _allApps = MutableStateFlow<List<AppDataUsage>>(emptyList())
    val allApps: StateFlow<List<AppDataUsage>> = _allApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterOption = MutableStateFlow(FirewallFilterOption.ALL)
    val filterOption: StateFlow<FirewallFilterOption> = _filterOption.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val blockedApps: StateFlow<List<BlockedAppEntity>> = repository.getAllBlockedApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentBlockedEvents: StateFlow<List<BlockedTrafficEventEntity>> = repository.getRecentBlockedTrafficEvents(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadApps()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun setFilterOption(option: FirewallFilterOption) {
        _filterOption.value = option
    }

    fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                when (val result = getUsageStatsUseCase(TimeRangeSelection.today())) {
                    is GetUsageStatsUseCase.Result.Success -> {
                        _allApps.value = result.topApps
                    }
                    is GetUsageStatsUseCase.Result.Error -> {
                        _errorMessage.value = "Failed to load applications: ${result.exception.message}"
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load applications: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun isAppBlocked(uid: Int): Boolean {
        return blockedApps.value.any { it.uid == uid }
    }

    fun getBlockedApp(uid: Int): BlockedAppEntity? {
        return blockedApps.value.firstOrNull { it.uid == uid }
    }

    fun setAppBlockedState(
        app: AppDataUsage,
        block: Boolean,
        reason: String = "User firewall rule",
        blockMobile: Boolean = true,
        blockWifi: Boolean = true
    ) {
        viewModelScope.launch {
            val result = blockAppDataUseCase(
                uid = app.uid,
                packageName = app.packageName,
                appName = app.appName,
                block = block,
                reason = reason,
                blockMobile = blockMobile,
                blockWifi = blockWifi
            )
            if (result is BlockAppDataUseCase.Result.Error) {
                _errorMessage.value = result.message
            } else {
                // Dynamically reload firewall rules in running VPN session
                vpnSessionManager.reloadFirewallRules()
            }
        }
    }

    fun toggleAppBlock(app: AppDataUsage) {
        val currentlyBlocked = isAppBlocked(app.uid)
        setAppBlockedState(app, !currentlyBlocked)
    }

    fun startVpn(context: android.content.Context) {
        vpnSessionManager.startVpn(context)
    }

    fun stopVpn(context: android.content.Context) {
        vpnSessionManager.stopVpn(context)
    }

    fun clearAllBlockedTrafficEvents() {
        viewModelScope.launch {
            repository.clearAllBlockedTrafficEvents()
        }
    }
}
