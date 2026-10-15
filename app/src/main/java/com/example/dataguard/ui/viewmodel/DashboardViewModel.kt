package com.example.dataguard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dataguard.DataGuardApp
import com.example.dataguard.data.entity.AppDataUsage
import com.example.dataguard.data.entity.AttributionConfidence
import com.example.dataguard.data.entity.BlockedAppEntity
import com.example.dataguard.data.entity.BlockedTrafficEventEntity
import com.example.dataguard.data.entity.DomainRuleEntity
import com.example.dataguard.data.entity.DomainSummary
import com.example.dataguard.data.entity.NetworkFlowEntity
import com.example.dataguard.data.entity.QuotaBreachEventEntity
import com.example.dataguard.data.entity.UsageEntity
import com.example.dataguard.data.repository.DataRepository
import com.example.dataguard.domain.model.AppQuotaCalculation
import com.example.dataguard.domain.model.AppQuotaCycleCalculator
import com.example.dataguard.domain.model.DashboardTimeRange
import com.example.dataguard.domain.model.GraphFilter
import com.example.dataguard.domain.model.GraphIntervalDetails
import com.example.dataguard.domain.model.NetworkTimelinePoint
import com.example.dataguard.domain.model.AppIntervalUsage
import com.example.dataguard.domain.model.DomainIntervalUsage
import com.example.dataguard.domain.model.TimeRangeSelection
import com.example.dataguard.domain.usecase.GetUsageStatsUseCase
import com.example.dataguard.domain.usecase.ToggleMobileDataUseCase
import com.example.dataguard.service.DataMonitorService
import com.example.dataguard.vpn.engine.DomainNormalizer
import com.example.dataguard.vpn.engine.FirewallDecision
import com.example.dataguard.vpn.engine.FirewallEvaluationResult
import com.example.dataguard.vpn.engine.RuleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DashboardViewModel(
    private val getUsageStatsUseCase: GetUsageStatsUseCase,
    private val toggleMobileDataUseCase: ToggleMobileDataUseCase,
    private val repository: DataRepository
) : ViewModel() {

    // --- Core Telemetry & Usage Records ---
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

    // 8. Live Network Activity (Live speed flow from background monitor service)
    val liveTxSpeed: StateFlow<Long> = DataMonitorService.liveTxSpeed
    val liveRxSpeed: StateFlow<Long> = DataMonitorService.liveRxSpeed

    // 9. Recent Firewall Events (Blocked connections)
    val recentBlockedEvents: StateFlow<List<BlockedTrafficEventEntity>> =
        repository.getRecentBlockedTrafficEvents(10)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 10. Recent Alerts (Quota breaches & limit warnings)
    val recentAlerts: StateFlow<List<QuotaBreachEventEntity>> =
        repository.getRecentQuotaBreachEvents(10)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 11. Current VPN/Firewall State
    val vpnState: StateFlow<com.example.dataguard.vpn.model.VpnState> =
        DataGuardApp.instance.vpnSessionManager.vpnState

    // 6. Top Applications
    private val _topApps = MutableStateFlow<List<AppDataUsage>>(emptyList())
    val topApps: StateFlow<List<AppDataUsage>> = _topApps.asStateFlow()

    // 7. Top Domains
    private val _topDomains = MutableStateFlow<List<DomainSummary>>(emptyList())
    val topDomains: StateFlow<List<DomainSummary>> = _topDomains.asStateFlow()

    // Network Flow Records Cache
    private var allCachedFlows: List<NetworkFlowEntity> = emptyList()

    // --- Timeline Graph & Filtering ---
    private val _selectedTimeRange = MutableStateFlow(DashboardTimeRange.TODAY)
    val selectedTimeRange: StateFlow<DashboardTimeRange> = _selectedTimeRange.asStateFlow()

    private val _selectedFilter = MutableStateFlow(GraphFilter.ALL)
    val selectedFilter: StateFlow<GraphFilter> = _selectedFilter.asStateFlow()

    private val _timelinePoints = MutableStateFlow<List<NetworkTimelinePoint>>(emptyList())
    val timelinePoints: StateFlow<List<NetworkTimelinePoint>> = _timelinePoints.asStateFlow()

    private val _selectedPoint = MutableStateFlow<NetworkTimelinePoint?>(null)
    val selectedPoint: StateFlow<NetworkTimelinePoint?> = _selectedPoint.asStateFlow()

    private val _selectedIntervalDetails = MutableStateFlow<GraphIntervalDetails?>(null)
    val selectedIntervalDetails: StateFlow<GraphIntervalDetails?> = _selectedIntervalDetails.asStateFlow()

    // Custom Date Range
    var customStartTime: Long = System.currentTimeMillis() - (3L * 24 * 60 * 60 * 1000L)
    var customEndTime: Long = System.currentTimeMillis()

    // --- App Detail Dialog State ---
    private val _selectedAppForDetail = MutableStateFlow<AppDataUsage?>(null)
    val selectedAppForDetail: StateFlow<AppDataUsage?> = _selectedAppForDetail.asStateFlow()

    private val _selectedAppQuota = MutableStateFlow<AppQuotaCalculation?>(null)
    val selectedAppQuota: StateFlow<AppQuotaCalculation?> = _selectedAppQuota.asStateFlow()

    private val _isAppBlocked = MutableStateFlow(false)
    val isAppBlocked: StateFlow<Boolean> = _isAppBlocked.asStateFlow()

    private val _appContactedFlows = MutableStateFlow<List<NetworkFlowEntity>>(emptyList())
    val appContactedFlows: StateFlow<List<NetworkFlowEntity>> = _appContactedFlows.asStateFlow()

    // --- Domain Detail Dialog State ---
    private val _selectedDomainForDetail = MutableStateFlow<DomainSummary?>(null)
    val selectedDomainForDetail: StateFlow<DomainSummary?> = _selectedDomainForDetail.asStateFlow()

    private val _domainFlows = MutableStateFlow<List<NetworkFlowEntity>>(emptyList())
    val domainFlows: StateFlow<List<NetworkFlowEntity>> = _domainFlows.asStateFlow()

    private val _domainEvaluation = MutableStateFlow<FirewallEvaluationResult?>(null)
    val domainEvaluation: StateFlow<FirewallEvaluationResult?> = _domainEvaluation.asStateFlow()

    // Allowance limits
    val dailyLimitMB: Long get() = DataGuardApp.instance.dailyLimitMB
    val weeklyLimitMB: Long get() = DataGuardApp.instance.weeklyLimitMB
    val monthlyLimitMB: Long get() = DataGuardApp.instance.monthlyLimitMB
    // 5. Remaining data allowance
    private val _remainingAllowanceMB = MutableStateFlow<Long?>(null)
    val remainingAllowanceMB: StateFlow<Long?> = _remainingAllowanceMB.asStateFlow()

    private val _allowanceProgress = MutableStateFlow(0f)
    val allowanceProgress: StateFlow<Float> = _allowanceProgress.asStateFlow()

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
                recalculateAllowanceAndTotals()
            }
        }
        viewModelScope.launch {
            repository.getUsageByPeriod("daily", 100).collect { list ->
                _dailyUsage.value = list
                generateTimelinePoints()
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
        viewModelScope.launch {
            repository.getAllFlows().collect { flows ->
                allCachedFlows = flows
                updateTopDomains(flows)
                generateTimelinePoints()
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
                        _topApps.value = result.topApps.sortedByDescending { it.totalBytes }
                        recalculateAllowanceAndTotals()
                        generateTimelinePoints()
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

    private fun updateTopDomains(flows: List<NetworkFlowEntity>) {
        val grouped = flows.groupBy { it.domain }.map { (domain, domainFlows) ->
            val totalBytes = domainFlows.sumOf { it.totalBytes }
            val uploadBytes = domainFlows.sumOf { it.uploadBytes }
            val downloadBytes = domainFlows.sumOf { it.downloadBytes }
            val conns = domainFlows.sumOf { it.connectionCount }
            val apps = domainFlows.map { it.appName }.distinct()
            val pkgs = domainFlows.map { it.packageName }.distinct()
            val firstSeen = domainFlows.minOfOrNull { it.firstSeen } ?: System.currentTimeMillis()
            val lastSeen = domainFlows.maxOfOrNull { it.lastSeen } ?: System.currentTimeMillis()
            val conf = when {
                domainFlows.any { it.confidenceEnum == AttributionConfidence.HIGH } -> AttributionConfidence.HIGH
                domainFlows.any { it.confidenceEnum == AttributionConfidence.MEDIUM } -> AttributionConfidence.MEDIUM
                domainFlows.any { it.confidenceEnum == AttributionConfidence.LOW } -> AttributionConfidence.LOW
                else -> AttributionConfidence.UNKNOWN
            }
            DomainSummary(
                domain = domain,
                totalBytes = totalBytes,
                uploadBytes = uploadBytes,
                downloadBytes = downloadBytes,
                connectionCount = conns,
                associatedPackages = pkgs,
                associatedAppNames = apps,
                firstSeen = firstSeen,
                lastSeen = lastSeen,
                confidence = conf
            )
        }.sortedByDescending { it.totalBytes }

        _topDomains.value = grouped
    }

    // --- Graph Filter & Range Controls ---
    fun setGraphFilter(filter: GraphFilter) {
        _selectedFilter.value = filter
        generateTimelinePoints()
    }

    fun setTimeRange(range: DashboardTimeRange) {
        _selectedTimeRange.value = range
        generateTimelinePoints()
    }

    fun setCustomDateRange(start: Long, end: Long) {
        customStartTime = minOf(start, end)
        customEndTime = maxOf(start, end)
        _selectedTimeRange.value = DashboardTimeRange.CUSTOM
        generateTimelinePoints()
    }

    /**
     * Generates normalized timeline data points for the graph based on selected time range.
     */
    fun generateTimelinePoints() {
        val range = _selectedTimeRange.value
        val now = System.currentTimeMillis()
        val points = mutableListOf<NetworkTimelinePoint>()

        when (range) {
            DashboardTimeRange.TODAY -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfToday = cal.timeInMillis
                val hourMs = 60 * 60 * 1000L

                // 12 2-hour buckets across today
                for (i in 0 until 12) {
                    val bStart = startOfToday + (i * 2 * hourMs)
                    val bEnd = bStart + (2 * hourMs)
                    val label = String.format(Locale.US, "%02d:00", i * 2)

                    val bucketFlows = allCachedFlows.filter { it.lastSeen in bStart until bEnd }
                    val ul = bucketFlows.sumOf { it.uploadBytes }
                    val dl = bucketFlows.sumOf { it.downloadBytes }
                    val mob = bucketFlows.filter { it.networkType == "MOBILE" }.sumOf { it.totalBytes }
                    val wifi = bucketFlows.filter { it.networkType == "WIFI" }.sumOf { it.totalBytes }

                    // Baseline from latest usage if flows haven't accumulated yet
                    val totalBase = ul + dl
                    val (finalUl, finalDl) = if (totalBase == 0L && i == Calendar.getInstance().get(Calendar.HOUR_OF_DAY) / 2) {
                        val latest = _latestUsage.value
                        val ltx = latest?.totalTxBytes ?: 0L
                        val lrx = latest?.totalRxBytes ?: 0L
                        Pair(ltx, lrx)
                    } else Pair(ul, dl)

                    points.add(
                        NetworkTimelinePoint(
                            timestamp = bStart,
                            label = label,
                            startTime = bStart,
                            endTime = bEnd,
                            uploadBytes = finalUl,
                            downloadBytes = finalDl,
                            mobileBytes = if (mob > 0) mob else (finalUl + finalDl),
                            wifiBytes = wifi,
                            allowedBytes = finalUl + finalDl,
                            blockedBytes = 0L,
                            activeApps = bucketFlows.map { it.appName }.distinct(),
                            contactedDomains = bucketFlows.map { it.domain }.distinct()
                        )
                    )
                }
            }

            DashboardTimeRange.DAYS_7 -> {
                val cal = Calendar.getInstance()
                val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
                for (i in 6 downTo 0) {
                    val dayCal = (cal.clone() as Calendar).apply {
                        add(Calendar.DAY_OF_YEAR, -i)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val bStart = dayCal.timeInMillis
                    val bEnd = bStart + (24 * 60 * 60 * 1000L)
                    val label = dayFormat.format(Date(bStart))

                    val bucketFlows = allCachedFlows.filter { it.lastSeen in bStart until bEnd }
                    val ul = bucketFlows.sumOf { it.uploadBytes }
                    val dl = bucketFlows.sumOf { it.downloadBytes }

                    val dailyEntity = _dailyUsage.value.find { it.timestamp in bStart until bEnd }
                    val finalUl = if (ul > 0) ul else (dailyEntity?.totalTxBytes ?: 0L)
                    val finalDl = if (dl > 0) dl else (dailyEntity?.totalRxBytes ?: 0L)
                    val mob = dailyEntity?.totalMobileBytes ?: bucketFlows.filter { it.networkType == "MOBILE" }.sumOf { it.totalBytes }
                    val wifi = dailyEntity?.totalWifiBytes ?: bucketFlows.filter { it.networkType == "WIFI" }.sumOf { it.totalBytes }

                    points.add(
                        NetworkTimelinePoint(
                            timestamp = bStart,
                            label = label,
                            startTime = bStart,
                            endTime = bEnd,
                            uploadBytes = finalUl,
                            downloadBytes = finalDl,
                            mobileBytes = mob,
                            wifiBytes = wifi,
                            allowedBytes = finalUl + finalDl,
                            blockedBytes = 0L,
                            activeApps = bucketFlows.map { it.appName }.distinct(),
                            contactedDomains = bucketFlows.map { it.domain }.distinct()
                        )
                    )
                }
            }

            DashboardTimeRange.DAYS_30 -> {
                val cal = Calendar.getInstance()
                val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())
                // 10 3-day intervals
                for (i in 9 downTo 0) {
                    val bucketCal = (cal.clone() as Calendar).apply {
                        add(Calendar.DAY_OF_YEAR, -(i * 3))
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val bStart = bucketCal.timeInMillis
                    val bEnd = bStart + (3L * 24 * 60 * 60 * 1000L)
                    val label = dateFormat.format(Date(bStart))

                    val bucketFlows = allCachedFlows.filter { it.lastSeen in bStart until bEnd }
                    val ul = bucketFlows.sumOf { it.uploadBytes }
                    val dl = bucketFlows.sumOf { it.downloadBytes }

                    points.add(
                        NetworkTimelinePoint(
                            timestamp = bStart,
                            label = label,
                            startTime = bStart,
                            endTime = bEnd,
                            uploadBytes = ul,
                            downloadBytes = dl,
                            mobileBytes = bucketFlows.filter { it.networkType == "MOBILE" }.sumOf { it.totalBytes },
                            wifiBytes = bucketFlows.filter { it.networkType == "WIFI" }.sumOf { it.totalBytes },
                            allowedBytes = ul + dl,
                            blockedBytes = 0L,
                            activeApps = bucketFlows.map { it.appName }.distinct(),
                            contactedDomains = bucketFlows.map { it.domain }.distinct()
                        )
                    )
                }
            }

            DashboardTimeRange.CUSTOM -> {
                val span = (customEndTime - customStartTime).coerceAtLeast(1000L)
                val bucketCount = 7
                val bucketSpan = span / bucketCount
                val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())

                for (i in 0 until bucketCount) {
                    val bStart = customStartTime + (i * bucketSpan)
                    val bEnd = bStart + bucketSpan
                    val label = dateFormat.format(Date(bStart))

                    val bucketFlows = allCachedFlows.filter { it.lastSeen in bStart until bEnd }
                    val ul = bucketFlows.sumOf { it.uploadBytes }
                    val dl = bucketFlows.sumOf { it.downloadBytes }

                    points.add(
                        NetworkTimelinePoint(
                            timestamp = bStart,
                            label = label,
                            startTime = bStart,
                            endTime = bEnd,
                            uploadBytes = ul,
                            downloadBytes = dl,
                            mobileBytes = bucketFlows.filter { it.networkType == "MOBILE" }.sumOf { it.totalBytes },
                            wifiBytes = bucketFlows.filter { it.networkType == "WIFI" }.sumOf { it.totalBytes },
                            allowedBytes = ul + dl,
                            blockedBytes = 0L,
                            activeApps = bucketFlows.map { it.appName }.distinct(),
                            contactedDomains = bucketFlows.map { it.domain }.distinct()
                        )
                    )
                }
            }
        }

        _timelinePoints.value = points
        // Default select the last point if none selected yet
        if (_selectedPoint.value == null && points.isNotEmpty()) {
            selectIntervalPoint(points.last())
        }
    }

    /**
     * TAPPING A GRAPH REGION:
     * When the user taps a graph interval, inspects:
     * - Applications active during that period
     * - Domains contacted
     * - Data consumed
     * - Blocked traffic vs Allowed traffic
     */
    fun selectIntervalPoint(point: NetworkTimelinePoint) {
        _selectedPoint.value = point

        viewModelScope.launch {
            val flows = repository.getFlowsInTimeRangeList(point.startTime, point.endTime)
            val blockedEvents = repository.getBlockedTrafficEventsInTimeRangeList(point.startTime, point.endTime)
            val blockedUids = DataGuardApp.instance.firewallEngine.getBlockedUids()

            // 1. Group active apps
            val appUsageMap = mutableMapOf<String, AppIntervalUsage>()
            flows.forEach { f ->
                val curr = appUsageMap[f.packageName] ?: AppIntervalUsage(
                    appName = f.appName,
                    packageName = f.packageName,
                    uid = f.uid,
                    isBlocked = blockedUids.contains(f.uid)
                )
                appUsageMap[f.packageName] = curr.copy(
                    uploadBytes = curr.uploadBytes + f.uploadBytes,
                    downloadBytes = curr.downloadBytes + f.downloadBytes,
                    mobileBytes = curr.mobileBytes + if (f.networkType == "MOBILE") f.totalBytes else 0L,
                    wifiBytes = curr.wifiBytes + if (f.networkType == "WIFI") f.totalBytes else 0L
                )
            }

            // 2. Group contacted domains
            val domainUsageMap = mutableMapOf<String, DomainIntervalUsage>()
            flows.forEach { f ->
                val curr = domainUsageMap[f.domain] ?: DomainIntervalUsage(
                    domain = f.domain,
                    associatedApps = mutableListOf(),
                    isBlocked = DataGuardApp.instance.firewallEngine.isDomainBlocked(f.domain)
                )
                val apps = (curr.associatedApps + f.appName).distinct()
                domainUsageMap[f.domain] = curr.copy(
                    uploadBytes = curr.uploadBytes + f.uploadBytes,
                    downloadBytes = curr.downloadBytes + f.downloadBytes,
                    connectionCount = curr.connectionCount + f.connectionCount,
                    associatedApps = apps
                )
            }

            // Fallback: If interval flows are empty, populate active apps from top apps for user feedback
            val activeAppsList = if (appUsageMap.isNotEmpty()) {
                appUsageMap.values.sortedByDescending { it.totalBytes }
            } else {
                _topApps.value.take(4).map {
                    AppIntervalUsage(
                        appName = it.appName,
                        packageName = it.packageName,
                        uid = it.uid,
                        uploadBytes = it.txBytes,
                        downloadBytes = it.rxBytes,
                        totalBytes = it.totalBytes,
                        isBlocked = blockedUids.contains(it.uid)
                    )
                }
            }

            val domainsList = if (domainUsageMap.isNotEmpty()) {
                domainUsageMap.values.sortedByDescending { it.totalBytes }
            } else {
                _topDomains.value.take(4).map {
                    DomainIntervalUsage(
                        domain = it.domain,
                        uploadBytes = it.uploadBytes,
                        downloadBytes = it.downloadBytes,
                        totalBytes = it.totalBytes,
                        connectionCount = it.connectionCount,
                        associatedApps = it.associatedApps,
                        isBlocked = DataGuardApp.instance.firewallEngine.isDomainBlocked(it.domain)
                    )
                }
            }

            val totalBytes = maxOf(point.totalBytes, activeAppsList.sumOf { it.totalBytes })
            val blockedBytes = blockedEvents.sumOf { it.bytesBlocked }
            val allowedBytes = maxOf(0L, totalBytes - blockedBytes)

            _selectedIntervalDetails.value = GraphIntervalDetails(
                startTime = point.startTime,
                endTime = point.endTime,
                label = point.label,
                totalDataConsumed = totalBytes,
                uploadBytes = point.uploadBytes,
                downloadBytes = point.downloadBytes,
                mobileBytes = point.mobileBytes,
                wifiBytes = point.wifiBytes,
                allowedBytes = allowedBytes,
                blockedBytes = blockedBytes,
                activeApps = activeAppsList,
                contactedDomains = domainsList,
                blockedEvents = blockedEvents
            )
        }
    }

    // --- App Detail Dialog Operations ---
    fun openAppDetail(app: AppDataUsage) {
        _selectedAppForDetail.value = app
        viewModelScope.launch {
            val calc = DataGuardApp.instance.appQuotaManager.evaluateQuotaForApp(app.uid)
            _selectedAppQuota.value = calc

            val blocked = repository.getBlockedApp(app.uid) != null
            _isAppBlocked.value = blocked

            val flows = repository.getFlowsForApp(app.packageName, app.uid)
            _appContactedFlows.value = flows
        }
    }

    fun openAppDetailByPackage(packageName: String, uid: Int) {
        val app = _topApps.value.find { it.packageName == packageName || it.uid == uid }
            ?: AppDataUsage(
                uid = uid,
                packageName = packageName,
                appName = packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
            )
        openAppDetail(app)
    }

    fun closeAppDetail() {
        _selectedAppForDetail.value = null
        _selectedAppQuota.value = null
        _appContactedFlows.value = emptyList()
    }

    fun toggleAppFirewall(blocked: Boolean) {
        val app = _selectedAppForDetail.value ?: return
        viewModelScope.launch {
            if (blocked) {
                repository.insertBlockedApp(
                    BlockedAppEntity(
                        uid = app.uid,
                        packageName = app.packageName,
                        appName = app.appName
                    )
                )
                DataGuardApp.instance.firewallEngine.addBlockedPackage(app.packageName, app.uid)
            } else {
                repository.deleteBlockedAppByUid(app.uid)
                DataGuardApp.instance.firewallEngine.removeBlockedPackage(app.packageName)
            }
            _isAppBlocked.value = blocked
        }
    }

    // --- Domain Detail Dialog Operations ---
    fun openDomainDetail(domainName: String) {
        viewModelScope.launch {
            val summary = _topDomains.value.find { it.domain == domainName }
                ?: DomainSummary(
                    domain = domainName,
                    totalBytes = 0L,
                    uploadBytes = 0L,
                    downloadBytes = 0L,
                    connectionCount = 1,
                    associatedPackages = emptyList(),
                    associatedAppNames = emptyList(),
                    firstSeen = System.currentTimeMillis(),
                    lastSeen = System.currentTimeMillis(),
                    confidence = AttributionConfidence.HIGH
                )
            _selectedDomainForDetail.value = summary

            val flows = repository.getFlowListForDomain(domainName)
            _domainFlows.value = flows

            val eval = DataGuardApp.instance.firewallEngine.evaluateConnection(
                domain = domainName,
                packageName = summary.associatedPackages.firstOrNull()
            )
            _domainEvaluation.value = eval
        }
    }

    fun closeDomainDetail() {
        _selectedDomainForDetail.value = null
        _domainFlows.value = emptyList()
        _domainEvaluation.value = null
    }

    fun toggleDomainFirewall() {
        val domain = _selectedDomainForDetail.value ?: return
        val currentEval = _domainEvaluation.value
        val isCurrentlyBlocked = currentEval?.decision == FirewallDecision.BLOCK
        val norm = DomainNormalizer.normalize(domain.domain)

        viewModelScope.launch {
            if (isCurrentlyBlocked) {
                repository.deleteGlobalDomainRule(norm)
                DataGuardApp.instance.firewallEngine.updateDomainRulesFromEntities(
                    repository.getActiveDomainRules()
                )
            } else {
                val rule = DomainRuleEntity(
                    domainPattern = domain.domain,
                    normalizedDomain = norm,
                    ruleType = RuleType.GLOBAL_BLOCK.name,
                    networkScope = "ANY",
                    notes = "Blocked from Dashboard"
                )
                repository.insertOrUpdateDomainRule(rule)
                DataGuardApp.instance.firewallEngine.updateDomainRulesFromEntities(
                    repository.getActiveDomainRules()
                )
            }
            val newEval = DataGuardApp.instance.firewallEngine.evaluateConnection(
                domain = domain.domain,
                packageName = domain.associatedPackages.firstOrNull()
            )
            _domainEvaluation.value = newEval
        }
    }

    // --- Calculations for 1-5 Requirements ---
    // 1. Current Mobile data usage
    fun getTotalDailyMobileMB(): Double {
        val latest = _latestUsage.value
        val listTotal = _dailyUsage.value.sumOf { it.totalMobileBytes }
        val bytes = if (latest != null) maxOf(latest.totalMobileBytes, listTotal) else listTotal
        return bytes / (1024.0 * 1024.0)
    }

    // 2. Current Wi-Fi usage
    fun getTotalDailyWifiMB(): Double {
        val latest = _latestUsage.value
        val listTotal = _dailyUsage.value.sumOf { it.totalWifiBytes }
        val bytes = if (latest != null) maxOf(latest.totalWifiBytes, listTotal) else listTotal
        return bytes / (1024.0 * 1024.0)
    }

    // 3. Today's usage (Mobile + Wi-Fi)
    fun getTodayTotalUsageMB(): Double = getTotalDailyMobileMB() + getTotalDailyWifiMB()

    // 4. Current billing-cycle usage
    fun getBillingCycleUsageMB(): Double {
        val monthlyTotal = _monthlyUsage.value.sumOf { it.totalMobileBytes }
        val weeklyTotal = _weeklyUsage.value.sumOf { it.totalMobileBytes }
        val dailyTotal = (getTotalDailyMobileMB() * 1024 * 1024).toLong()
        val maxUsage = maxOf(monthlyTotal, weeklyTotal, dailyTotal)
        return maxUsage / (1024.0 * 1024.0)
    }


    private fun recalculateAllowanceAndTotals() {
        val limitMB = if (dailyLimitMB > 0) dailyLimitMB else monthlyLimitMB
        if (limitMB > 0) {
            val usedMB = if (dailyLimitMB > 0) getTotalDailyMobileMB() else getBillingCycleUsageMB()
            val remaining = (limitMB - usedMB).toLong().coerceAtLeast(0L)
            _remainingAllowanceMB.value = remaining
            _allowanceProgress.value = (usedMB.toFloat() / limitMB.toFloat()).coerceIn(0f, 1f)
        } else {
            _remainingAllowanceMB.value = null
            _allowanceProgress.value = 0f
        }
    }

    fun startVpn(context: android.content.Context) {
        DataGuardApp.instance.vpnSessionManager.startVpn(context)
    }

    fun stopVpn(context: android.content.Context? = null) {
        val ctx = context ?: DataGuardApp.instance
        DataGuardApp.instance.vpnSessionManager.stopVpn(ctx)
    }

    fun toggleMobileData(enabled: Boolean) {
        viewModelScope.launch {
            when (val result = toggleMobileDataUseCase(enabled, "dashboard_toggle")) {
                is ToggleMobileDataUseCase.Result.Success -> _userMessage.value = result.message
                is ToggleMobileDataUseCase.Result.Error -> _userMessage.value = result.message
            }
        }
    }

    fun clearMessage() {
        _userMessage.value = null
    }
}
