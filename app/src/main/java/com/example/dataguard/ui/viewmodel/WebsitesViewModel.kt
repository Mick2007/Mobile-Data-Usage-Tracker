package com.example.dataguard.ui.viewmodel

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dataguard.DataGuardApp
import com.example.dataguard.data.entity.AttributionConfidence
import com.example.dataguard.data.entity.DomainRuleEntity
import com.example.dataguard.data.entity.DomainSummary
import com.example.dataguard.data.entity.NetworkFlowEntity
import com.example.dataguard.data.repository.DataRepository
import com.example.dataguard.utils.ExportAppItem
import com.example.dataguard.utils.ExportDomainItem
import com.example.dataguard.utils.WebsiteExportReportData
import com.example.dataguard.vpn.engine.DomainNormalizer
import com.example.dataguard.vpn.engine.FirewallDecision
import com.example.dataguard.vpn.engine.FirewallEvaluationResult
import com.example.dataguard.vpn.engine.NetworkInterfaceType
import com.example.dataguard.vpn.engine.RuleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class DomainTimeWindow(val label: String, val durationMillis: Long) {
    TODAY("Today", 24 * 60 * 60 * 1000L),
    SEVEN_DAYS("7 Days", 7 * 24 * 60 * 60 * 1000L),
    THIRTY_DAYS("30 Days", 30 * 24 * 60 * 60 * 1000L),
    ALL_TIME("All Time", Long.MAX_VALUE)
}

enum class DomainSortOption(val label: String) {
    DATA("Data"),
    CONNECTIONS("Connections"),
    RECENT_ACTIVITY("Recent")
}

enum class DomainFilterTab(val label: String) {
    ALL("All Domains"),
    BLOCKED("Blocked"),
    ALLOWED("Allowed"),
    EXPORT("Export")
}

enum class ExportDatePreset(val label: String) {
    PREVIOUS_DAY("Yesterday"),
    PAST_7_DAYS("7 Days"),
    PAST_30_DAYS("30 Days"),
    CUSTOM("Custom")
}

class WebsitesViewModel(
    private val repository: DataRepository
) : ViewModel() {

    private val firewallEngine = DataGuardApp.instance.firewallEngine
    private val vpnSessionManager = DataGuardApp.instance.vpnSessionManager

    private val _selectedTimeWindow = MutableStateFlow(DomainTimeWindow.TODAY)
    val selectedTimeWindow: StateFlow<DomainTimeWindow> = _selectedTimeWindow.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(DomainSortOption.DATA)
    val sortOption: StateFlow<DomainSortOption> = _sortOption.asStateFlow()

    private val _selectedFilterTab = MutableStateFlow(DomainFilterTab.ALL)
    val selectedFilterTab: StateFlow<DomainFilterTab> = _selectedFilterTab.asStateFlow()

    private val _selectedDomainDetail = MutableStateFlow<DomainSummary?>(null)
    val selectedDomainDetail: StateFlow<DomainSummary?> = _selectedDomainDetail.asStateFlow()

    private val _selectedDomainFlows = MutableStateFlow<List<NetworkFlowEntity>>(emptyList())
    val selectedDomainFlows: StateFlow<List<NetworkFlowEntity>> = _selectedDomainFlows.asStateFlow()

    // --- Export Feature States ---
    private val _selectedExportPreset = MutableStateFlow(ExportDatePreset.PREVIOUS_DAY)
    val selectedExportPreset: StateFlow<ExportDatePreset> = _selectedExportPreset.asStateFlow()

    // 0 = Today, 1 = Yesterday (Previous Day), 2 = 2 days ago, etc.
    private val _exportTargetDayOffset = MutableStateFlow(1)
    val exportTargetDayOffset: StateFlow<Int> = _exportTargetDayOffset.asStateFlow()

    private val _exportCustomStartMillis = MutableStateFlow(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L)
    val exportCustomStartMillis: StateFlow<Long> = _exportCustomStartMillis.asStateFlow()

    private val _exportCustomEndMillis = MutableStateFlow(System.currentTimeMillis())
    val exportCustomEndMillis: StateFlow<Long> = _exportCustomEndMillis.asStateFlow()

    private val _exportStatusMessage = MutableStateFlow<String?>(null)
    val exportStatusMessage: StateFlow<String?> = _exportStatusMessage.asStateFlow()

    // Observe active domain rules from database
    val domainRules: StateFlow<List<DomainRuleEntity>> = repository.getAllDomainRulesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Observe all flows reactive to database
    val rawFlows: StateFlow<List<NetworkFlowEntity>> = repository.getAllFlows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Aggregated Domain Summaries with rule awareness for normal browsing tabs
    val topDomains: StateFlow<List<DomainSummary>> = combine(
        combine(rawFlows, domainRules, _selectedTimeWindow) { flows, rules, window ->
            Triple(flows, rules, window)
        },
        combine(_searchQuery, _sortOption, _selectedFilterTab) { query, sort, filterTab ->
            Triple(query, sort, filterTab)
        }
    ) { (flows: List<NetworkFlowEntity>, rules: List<DomainRuleEntity>, window: DomainTimeWindow),
        (query: String, sort: DomainSortOption, filterTab: DomainFilterTab) ->

        val cutoff = if (window == DomainTimeWindow.ALL_TIME) 0L else System.currentTimeMillis() - window.durationMillis

        // Group flows by domain within time cutoff
        val filteredFlows = flows.filter { it.lastSeen >= cutoff }

        // Start with all domains seen in traffic
        val flowMap: MutableMap<String, List<NetworkFlowEntity>> = filteredFlows.groupBy { it.domain }.toMutableMap()

        // Also ensure explicitly ruled domains appear even if zero traffic yet
        for (rule in rules) {
            if (!flowMap.containsKey(rule.domainPattern) && !flowMap.containsKey(rule.normalizedDomain)) {
                flowMap[rule.domainPattern] = emptyList()
            }
        }

        val summaries: List<DomainSummary> = flowMap.map { (domain, domainFlows) ->
            val totalBytes = domainFlows.sumOf { it.totalBytes }
            val uploadBytes = domainFlows.sumOf { it.uploadBytes }
            val downloadBytes = domainFlows.sumOf { it.downloadBytes }
            val connections = domainFlows.sumOf { it.connectionCount }
            val firstSeen = domainFlows.minOfOrNull { it.firstSeen } ?: System.currentTimeMillis()
            val lastSeen = domainFlows.maxOfOrNull { it.lastSeen } ?: System.currentTimeMillis()

            val pkgs = domainFlows.map { it.packageName }.filter { it != "unknown" }.distinct()
            val apps = domainFlows.map { it.appName }.filter { it != "Unknown" }.distinct()

            val conf = when {
                domainFlows.any { it.confidenceEnum == AttributionConfidence.HIGH } -> AttributionConfidence.HIGH
                domainFlows.any { it.confidenceEnum == AttributionConfidence.MEDIUM } -> AttributionConfidence.MEDIUM
                domainFlows.any { it.confidenceEnum == AttributionConfidence.LOW } -> AttributionConfidence.LOW
                domainFlows.isEmpty() -> AttributionConfidence.HIGH // explicitly user-configured rule
                else -> AttributionConfidence.UNKNOWN
            }

            DomainSummary(
                domain = domain,
                totalBytes = totalBytes,
                uploadBytes = uploadBytes,
                downloadBytes = downloadBytes,
                connectionCount = connections,
                associatedPackages = pkgs,
                associatedAppNames = apps,
                firstSeen = firstSeen,
                lastSeen = lastSeen,
                confidence = conf
            )
        }.filter { summary ->
            val matchesQuery = query.isBlank() || summary.domain.contains(query, ignoreCase = true) ||
                    summary.associatedAppNames.any { it.contains(query, ignoreCase = true) }

            val eval = firewallEngine.evaluateConnection(
                domain = summary.domain,
                packageName = summary.associatedPackages.firstOrNull(),
                networkType = NetworkInterfaceType.ANY
            )
            val isBlocked = eval.decision == FirewallDecision.BLOCK

            val matchesFilter = when (filterTab) {
                DomainFilterTab.ALL -> true
                DomainFilterTab.BLOCKED -> isBlocked
                DomainFilterTab.ALLOWED -> !isBlocked
                DomainFilterTab.EXPORT -> true
            }

            matchesQuery && matchesFilter
        }

        when (sort) {
            DomainSortOption.DATA -> summaries.sortedByDescending { it.totalBytes }
            DomainSortOption.CONNECTIONS -> summaries.sortedByDescending { it.connectionCount }
            DomainSortOption.RECENT_ACTIVITY -> summaries.sortedByDescending { it.lastSeen }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Reactive Export Report Preview ---
    val exportReportPreview: StateFlow<WebsiteExportReportData> = combine(
        rawFlows,
        _selectedExportPreset,
        _exportTargetDayOffset,
        combine(_exportCustomStartMillis, _exportCustomEndMillis) { start, end -> Pair(start, end) }
    ) { flows, preset, dayOffset, customRange ->
        val (startMillis, endMillis) = computeDateRange(preset, dayOffset, customRange.first, customRange.second)
        val periodLabel = computePeriodLabel(preset, dayOffset, startMillis, endMillis)

        val filteredFlows = flows.filter {
            (it.lastSeen in startMillis..endMillis) || (it.firstSeen in startMillis..endMillis)
        }

        // Domain breakdown
        val domainGroups = filteredFlows.groupBy { it.domain }
        val domainItems = domainGroups.map { (domain, dFlows) ->
            val uploadBytes = dFlows.sumOf { it.uploadBytes }
            val downloadBytes = dFlows.sumOf { it.downloadBytes }
            val totalBytes = uploadBytes + downloadBytes
            val connections = dFlows.sumOf { it.connectionCount }
            val firstSeen = dFlows.minOfOrNull { it.firstSeen } ?: startMillis
            val lastSeen = dFlows.maxOfOrNull { it.lastSeen } ?: endMillis
            val appName = dFlows.firstOrNull { it.appName != "Unknown" && it.appName.isNotBlank() }?.appName ?: "Unknown / Web Traffic"
            val packageName = dFlows.firstOrNull { it.packageName != "unknown" && it.packageName.isNotBlank() }?.packageName ?: "unknown"

            val conf = when {
                dFlows.any { it.confidenceEnum == AttributionConfidence.HIGH } -> "High"
                dFlows.any { it.confidenceEnum == AttributionConfidence.MEDIUM } -> "Medium"
                dFlows.any { it.confidenceEnum == AttributionConfidence.LOW } -> "Low"
                else -> "Unknown"
            }

            val eval = firewallEngine.evaluateConnection(domain = domain, packageName = packageName, networkType = NetworkInterfaceType.ANY)
            val fwStatus = if (eval.decision == FirewallDecision.BLOCK) "Blocked" else "Allowed"

            ExportDomainItem(
                domain = domain,
                appName = appName,
                packageName = packageName,
                uploadBytes = uploadBytes,
                downloadBytes = downloadBytes,
                totalBytes = totalBytes,
                connectionCount = connections,
                firstSeen = firstSeen,
                lastSeen = lastSeen,
                confidence = conf,
                firewallStatus = fwStatus
            )
        }.sortedByDescending { it.totalBytes }

        // Per-App breakdown
        val appGroups = filteredFlows.groupBy { if (it.packageName.isNotBlank()) it.packageName else "unknown" }
        val appItems = appGroups.map { (pkg, aFlows) ->
            val appName = aFlows.firstOrNull { it.appName != "Unknown" && it.appName.isNotBlank() }?.appName ?: pkg
            val uploadBytes = aFlows.sumOf { it.uploadBytes }
            val downloadBytes = aFlows.sumOf { it.downloadBytes }
            val totalBytes = uploadBytes + downloadBytes
            val domainCount = aFlows.map { it.domain }.distinct().size

            ExportAppItem(
                appName = appName,
                packageName = pkg,
                uploadBytes = uploadBytes,
                downloadBytes = downloadBytes,
                totalBytes = totalBytes,
                domainCount = domainCount
            )
        }.sortedByDescending { it.totalBytes }

        val totalUpload = filteredFlows.sumOf { it.uploadBytes }
        val totalDownload = filteredFlows.sumOf { it.downloadBytes }
        val grandTotal = totalUpload + totalDownload

        WebsiteExportReportData(
            periodLabel = periodLabel,
            startTimeMillis = startMillis,
            endTimeMillis = endMillis,
            totalUploadBytes = totalUpload,
            totalDownloadBytes = totalDownload,
            totalBytes = grandTotal,
            domainItems = domainItems,
            appItems = appItems,
            flowRecords = filteredFlows
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        WebsiteExportReportData(
            periodLabel = "Yesterday",
            startTimeMillis = 0L,
            endTimeMillis = 0L,
            totalUploadBytes = 0L,
            totalDownloadBytes = 0L,
            totalBytes = 0L,
            domainItems = emptyList(),
            appItems = emptyList()
        )
    )

    fun setTimeWindow(window: DomainTimeWindow) {
        _selectedTimeWindow.value = window
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(sort: DomainSortOption) {
        _sortOption.value = sort
    }

    fun setFilterTab(tab: DomainFilterTab) {
        _selectedFilterTab.value = tab
    }

    fun setExportPreset(preset: ExportDatePreset) {
        _selectedExportPreset.value = preset
        if (preset == ExportDatePreset.PREVIOUS_DAY && _exportTargetDayOffset.value < 1) {
            _exportTargetDayOffset.value = 1
        }
    }

    fun navigateDayOffset(delta: Int) {
        val newOffset = (_exportTargetDayOffset.value + delta).coerceAtLeast(0)
        _exportTargetDayOffset.value = newOffset
    }

    fun setCustomExportRange(startMillis: Long, endMillis: Long) {
        _exportCustomStartMillis.value = startMillis
        _exportCustomEndMillis.value = endMillis
        _selectedExportPreset.value = ExportDatePreset.CUSTOM
    }

    fun clearExportStatusMessage() {
        _exportStatusMessage.value = null
    }

    fun exportAndShareReport(context: Context) {
        val report = exportReportPreview.value
        if (report.totalBytes == 0L && report.domainItems.isEmpty()) {
            _exportStatusMessage.value = "No telemetry traffic recorded for ${report.periodLabel}"
            return
        }

        val exporter = DataGuardApp.instance.csvExporter
        val uri = exporter.exportComprehensiveWebsiteReport(report)
        if (uri != null) {
            exporter.shareCSV(uri, "DataGuard Website Telemetry Audit (${report.periodLabel})")
            _exportStatusMessage.value = "Exported ${report.domainItems.size} domains & ${report.appItems.size} apps"
        } else {
            _exportStatusMessage.value = "Failed to generate CSV export file"
        }
    }

    fun selectDomain(summary: DomainSummary) {
        _selectedDomainDetail.value = summary
        viewModelScope.launch {
            _selectedDomainFlows.value = repository.getFlowListForDomain(summary.domain)
        }
    }

    fun clearSelectedDomain() {
        _selectedDomainDetail.value = null
        _selectedDomainFlows.value = emptyList()
    }

    fun getDomainStatus(domain: String, packageName: String? = null, networkType: NetworkInterfaceType = NetworkInterfaceType.ANY): FirewallEvaluationResult {
        return firewallEngine.evaluateConnection(domain, packageName, networkType = networkType)
    }

    fun getRulesForDomain(domain: String): List<DomainRuleEntity> {
        val norm = DomainNormalizer.normalize(domain)
        return domainRules.value.filter { rule ->
            DomainNormalizer.matches(norm, rule.domainPattern) || rule.normalizedDomain == norm
        }
    }

    // --- Domain Rule Management Actions ---

    fun addGlobalDomainRule(domain: String, block: Boolean, networkScope: String = "ANY") {
        viewModelScope.launch {
            val ruleType = if (block) RuleType.GLOBAL_BLOCK else RuleType.GLOBAL_ALLOW
            val entity = DomainRuleEntity(
                domainPattern = domain.trim(),
                normalizedDomain = DomainNormalizer.normalize(domain),
                ruleType = ruleType.name,
                packageName = null,
                appName = null,
                networkScope = networkScope,
                isEnabled = true
            )
            repository.insertOrUpdateDomainRule(entity)
            vpnSessionManager.reloadFirewallRules()
        }
    }

    fun addAppDomainRule(domain: String, packageName: String, appName: String, block: Boolean, networkScope: String = "ANY") {
        viewModelScope.launch {
            val ruleType = if (block) RuleType.APP_BLOCK else RuleType.APP_ALLOW
            val entity = DomainRuleEntity(
                domainPattern = domain.trim(),
                normalizedDomain = DomainNormalizer.normalize(domain),
                ruleType = ruleType.name,
                packageName = packageName,
                appName = appName,
                networkScope = networkScope,
                isEnabled = true
            )
            repository.insertOrUpdateDomainRule(entity)
            vpnSessionManager.reloadFirewallRules()
        }
    }

    fun toggleDomainBlock(domain: String) {
        val eval = getDomainStatus(domain)
        val isCurrentlyBlocked = eval.decision == FirewallDecision.BLOCK
        val norm = DomainNormalizer.normalize(domain)

        viewModelScope.launch {
            if (isCurrentlyBlocked) {
                // Delete existing block rules for this domain
                repository.deleteGlobalDomainRule(norm)
            } else {
                // Insert global block rule
                val entity = DomainRuleEntity(
                    domainPattern = norm,
                    normalizedDomain = norm,
                    ruleType = RuleType.GLOBAL_BLOCK.name,
                    packageName = null,
                    appName = null,
                    networkScope = "ANY",
                    isEnabled = true
                )
                repository.insertOrUpdateDomainRule(entity)
            }
            vpnSessionManager.reloadFirewallRules()
        }
    }

    fun deleteRule(rule: DomainRuleEntity) {
        viewModelScope.launch {
            repository.deleteDomainRule(rule)
            vpnSessionManager.reloadFirewallRules()
        }
    }

    fun clearAllFlows() {
        viewModelScope.launch {
            repository.clearAllFlows()
        }
    }

    /**
     * Diagnostic helper: records test website traffic to instantly verify live domain tracking.
     * DEBUG ONLY: This method is intended strictly for testing and diagnostics.
     */
    @VisibleForTesting
    fun testTrackWebsite(testDomain: String = "wikipedia.org") {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val tracker = DataGuardApp.instance.domainFlowTracker
            tracker.recordDnsQuery(
                queriedDomain = testDomain,
                clientIp = "10.1.10.1",
                uploadBytes = 72L,
                downloadBytes = 144L
            )
            tracker.registerIpDomainMapping("198.35.26.96", testDomain)
            tracker.flushPendingDeltas()
        }
    }

    companion object {
        fun computeDateRange(preset: ExportDatePreset, dayOffset: Int, customStart: Long, customEnd: Long): Pair<Long, Long> {
            val now = System.currentTimeMillis()
            return when (preset) {
                ExportDatePreset.PREVIOUS_DAY -> {
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = now
                    cal.add(Calendar.DAY_OF_YEAR, -dayOffset)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val start = cal.timeInMillis
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    val end = cal.timeInMillis
                    Pair(start, end)
                }
                ExportDatePreset.PAST_7_DAYS -> {
                    val start = now - (7L * 24 * 60 * 60 * 1000L)
                    Pair(start, now)
                }
                ExportDatePreset.PAST_30_DAYS -> {
                    val start = now - (30L * 24 * 60 * 60 * 1000L)
                    Pair(start, now)
                }
                ExportDatePreset.CUSTOM -> {
                    val start = if (customStart > 0L) customStart else now - (24 * 60 * 60 * 1000L)
                    val end = if (customEnd > 0L) customEnd else now
                    Pair(start, end)
                }
            }
        }

        fun computePeriodLabel(preset: ExportDatePreset, dayOffset: Int, startMillis: Long, endMillis: Long): String {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            return when (preset) {
                ExportDatePreset.PREVIOUS_DAY -> {
                    val dayName = when (dayOffset) {
                        0 -> "Today"
                        1 -> "Yesterday"
                        else -> "$dayOffset Days Ago"
                    }
                    "$dayName (${sdf.format(Date(startMillis))})"
                }
                ExportDatePreset.PAST_7_DAYS -> "Past 7 Days (${sdf.format(Date(startMillis))} – ${sdf.format(Date(endMillis))})"
                ExportDatePreset.PAST_30_DAYS -> "Past 30 Days (${sdf.format(Date(startMillis))} – ${sdf.format(Date(endMillis))})"
                ExportDatePreset.CUSTOM -> "Custom Range (${sdf.format(Date(startMillis))} – ${sdf.format(Date(endMillis))})"
            }
        }
    }
}
