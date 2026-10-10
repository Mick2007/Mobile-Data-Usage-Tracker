package com.example.dataguard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dataguard.DataGuardApp
import com.example.dataguard.data.entity.AttributionConfidence
import com.example.dataguard.data.entity.DomainRuleEntity
import com.example.dataguard.data.entity.DomainSummary
import com.example.dataguard.data.entity.NetworkFlowEntity
import com.example.dataguard.data.repository.DataRepository
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

enum class DomainTimeWindow(val label: String, val durationMillis: Long) {
    TODAY("Today", 24L * 60 * 60 * 1000L),
    SEVEN_DAYS("7 Days", 7L * 24 * 60 * 60 * 1000L),
    THIRTY_DAYS("30 Days", 30L * 24 * 60 * 60 * 1000L)
}

enum class DomainSortOption(val label: String) {
    DATA("Data"),
    CONNECTIONS("Connections"),
    RECENT_ACTIVITY("Recent")
}

enum class DomainFilterTab(val label: String) {
    ALL("All Domains"),
    BLOCKED("Blocked"),
    ALLOWED("Allowed")
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

    // Observe active domain rules from database
    val domainRules: StateFlow<List<DomainRuleEntity>> = repository.getAllDomainRulesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Observe all flows reactive to database
    val rawFlows: StateFlow<List<NetworkFlowEntity>> = repository.getAllFlows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Aggregated Domain Summaries with rule awareness
    val topDomains: StateFlow<List<DomainSummary>> = combine(
        combine(rawFlows, domainRules, _selectedTimeWindow) { flows, rules, window ->
            Triple(flows, rules, window)
        },
        combine(_searchQuery, _sortOption, _selectedFilterTab) { query, sort, filterTab ->
            Triple(query, sort, filterTab)
        }
    ) { (flows: List<NetworkFlowEntity>, rules: List<DomainRuleEntity>, window: DomainTimeWindow),
        (query: String, sort: DomainSortOption, filterTab: DomainFilterTab) ->

        val cutoff = System.currentTimeMillis() - window.durationMillis

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
            }

            matchesQuery && matchesFilter
        }

        when (sort) {
            DomainSortOption.DATA -> summaries.sortedByDescending { it.totalBytes }
            DomainSortOption.CONNECTIONS -> summaries.sortedByDescending { it.connectionCount }
            DomainSortOption.RECENT_ACTIVITY -> summaries.sortedByDescending { it.lastSeen }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
}
