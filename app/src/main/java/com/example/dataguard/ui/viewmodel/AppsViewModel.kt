package com.example.dataguard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dataguard.data.entity.AppDataUsage
import com.example.dataguard.data.entity.BlockedAppEntity
import com.example.dataguard.data.repository.DataRepository
import com.example.dataguard.domain.usecase.BlockAppDataUseCase
import com.example.dataguard.domain.usecase.GetUsageStatsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

enum class AppSortOption {
    TOTAL_USAGE,
    DOWNLOAD_RX,
    UPLOAD_TX,
    NAME
}

class AppsViewModel(
    private val getUsageStatsUseCase: GetUsageStatsUseCase,
    private val blockAppDataUseCase: BlockAppDataUseCase,
    private val repository: DataRepository
) : ViewModel() {

    private val _allApps = MutableStateFlow<List<AppDataUsage>>(emptyList())
    val allApps: StateFlow<List<AppDataUsage>> = _allApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(AppSortOption.TOTAL_USAGE)
    val sortOption: StateFlow<AppSortOption> = _sortOption.asStateFlow()

    private val _blockedApps = MutableStateFlow<List<BlockedAppEntity>>(emptyList())
    val blockedApps: StateFlow<List<BlockedAppEntity>> = _blockedApps.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadApps()
        observeBlockedApps()
    }

    private fun observeBlockedApps() {
        viewModelScope.launch {
            repository.getAllBlockedApps().collect { blockedList ->
                _blockedApps.value = blockedList
            }
        }
    }

    fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Ensure latest stats are captured
                getUsageStatsUseCase("daily")
                val latest = repository.getLatestUsage().firstOrNull()
                if (latest != null) {
                    val apps = AppDataUsage.deserializeList(latest.topAppsJson)
                    _allApps.value = apps
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(option: AppSortOption) {
        _sortOption.value = option
    }

    fun toggleBlockApp(app: AppDataUsage, shouldBlock: Boolean) {
        viewModelScope.launch {
            val result = blockAppDataUseCase(app.uid, app.packageName, app.appName, shouldBlock)
            if (result is BlockAppDataUseCase.Result.Error) {
                _errorMessage.value = result.message
            }
        }
    }

    fun isAppBlocked(uid: Int): Boolean {
        return _blockedApps.value.any { it.uid == uid }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
