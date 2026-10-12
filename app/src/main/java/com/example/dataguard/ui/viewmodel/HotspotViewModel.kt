package com.example.dataguard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dataguard.data.entity.HotspotSessionEntity
import com.example.dataguard.data.repository.DataRepository
import com.example.dataguard.domain.hotspot.HotspotMonitor
import com.example.dataguard.domain.hotspot.HotspotState
import com.example.dataguard.domain.hotspot.TetheringTrafficSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class HistoryFilter {
    ALL, TODAY, THIS_WEEK, THIS_MONTH
}

class HotspotViewModel(
    private val hotspotMonitor: HotspotMonitor,
    private val repository: DataRepository
) : ViewModel() {

    val hotspotState: StateFlow<HotspotState> = hotspotMonitor.hotspotState

    val activeSession: StateFlow<HotspotSessionEntity?> = hotspotMonitor.activeSession

    val allSessions: StateFlow<List<HotspotSessionEntity>> = repository.getAllHotspotSessionsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _todayUsage = MutableStateFlow(TetheringTrafficSummary())
    val todayUsage: StateFlow<TetheringTrafficSummary> = _todayUsage.asStateFlow()

    private val _billingCycleUsage = MutableStateFlow(TetheringTrafficSummary())
    val billingCycleUsage: StateFlow<TetheringTrafficSummary> = _billingCycleUsage.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _historyFilter = MutableStateFlow(HistoryFilter.ALL)
    val historyFilter: StateFlow<HistoryFilter> = _historyFilter.asStateFlow()

    val explanationText: String = HotspotMonitor.ANDROID_LIMITATION_EXPLANATION

    init {
        refresh()
        startLivePolling()
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyFilter.value = filter
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                hotspotMonitor.syncHotspotStatus()
                updateUsageSummaries()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun updateUsageSummaries() {
        val today = hotspotMonitor.getTodayTetheringUsage()
        _todayUsage.value = today

        val cycle = hotspotMonitor.getBillingCycleTetheringUsage(cycleStartDay = 1)
        _billingCycleUsage.value = cycle
    }

    private fun startLivePolling() {
        viewModelScope.launch {
            while (isActive) {
                delay(3000)
                try {
                    hotspotMonitor.syncHotspotStatus()
                    updateUsageSummaries()
                } catch (_: Exception) {}
            }
        }
    }

    fun filterSessions(sessions: List<HotspotSessionEntity>, filter: HistoryFilter): List<HotspotSessionEntity> {
        val now = System.currentTimeMillis()
        return when (filter) {
            HistoryFilter.ALL -> sessions
            HistoryFilter.TODAY -> {
                val startOfToday = hotspotMonitor.getTodayStartTimestamp()
                sessions.filter { it.startTime >= startOfToday }
            }
            HistoryFilter.THIS_WEEK -> {
                val startOfWeek = now - (7L * 24 * 60 * 60 * 1000L)
                sessions.filter { it.startTime >= startOfWeek }
            }
            HistoryFilter.THIS_MONTH -> {
                val startOfMonth = hotspotMonitor.getBillingCycleStartTimestamp(1)
                sessions.filter { it.startTime >= startOfMonth }
            }
        }
    }

    suspend fun clearHistory() {
        repository.clearAllHotspotSessions()
        refresh()
    }
}
