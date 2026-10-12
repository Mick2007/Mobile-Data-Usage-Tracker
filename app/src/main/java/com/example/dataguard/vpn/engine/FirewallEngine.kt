package com.example.dataguard.vpn.engine

import android.util.Log
import com.example.dataguard.data.entity.BlockedAppEntity
import com.example.dataguard.data.entity.BlockedTrafficEventEntity
import com.example.dataguard.data.entity.DomainRuleEntity
import com.example.dataguard.data.repository.DataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Deterministic application-level and domain-level firewall engine.
 *
 * EVALUATION PRIORITY (Strictly Deterministic):
 * 1. APP + DOMAIN rule (Specific application targeting a specific domain or wildcard)
 *    - APP_ALLOW -> Explicitly allows connection
 *    - APP_BLOCK -> Blocks connection
 * 2. GLOBAL DOMAIN rule (Affects all applications targeting this domain)
 *    - GLOBAL_ALLOW -> Explicitly allows connection globally
 *    - GLOBAL_BLOCK -> Blocks connection globally
 * 3. APP RULE (Application-level block configured via App Accounting / Disallowed list)
 *    - Blocked -> Blocks application connection
 * 4. GLOBAL DEFAULT -> ALLOW (Fail-open safety ensures no device-wide lockouts)
 *
 * Persists and synchronizes rules dynamically from Room database.
 */
class FirewallEngine(
    private val repository: DataRepository? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    // In-memory thread-safe lookup tables for microsecond evaluation
    private val blockedPackages = ConcurrentHashMap.newKeySet<String>()
    private val blockedUids = ConcurrentHashMap.newKeySet<Int>()
    private val blockedRulesByUid = ConcurrentHashMap<Int, BlockedAppEntity>()
    private val blockedRulesByPackage = ConcurrentHashMap<String, BlockedAppEntity>()

    // Domain rules cached in memory
    private val activeDomainRules = CopyOnWriteArrayList<DomainRuleEntity>()

    // Event broadcast for blocked traffic notifications and UI telemetry
    private val _blockedEventFlow = MutableSharedFlow<BlockedTrafficEventEntity>(extraBufferCapacity = 64)
    val blockedEventFlow: SharedFlow<BlockedTrafficEventEntity> = _blockedEventFlow.asSharedFlow()

    init {
        repository?.let { repo ->
            scope.launch {
                repo.getAllBlockedApps().collectLatest { apps ->
                    updateRulesFromEntities(apps)
                }
            }
            scope.launch {
                repo.getActiveDomainRulesFlow().collectLatest { rules ->
                    updateDomainRulesFromEntities(rules)
                }
            }
        }
    }

    /**
     * Synchronizes in-memory app rules from Room.
     */
    fun updateRulesFromEntities(apps: List<BlockedAppEntity>) {
        try {
            blockedPackages.clear()
            blockedUids.clear()
            blockedRulesByUid.clear()
            blockedRulesByPackage.clear()

            apps.forEach { app ->
                blockedPackages.add(app.packageName)
                blockedUids.add(app.uid)
                blockedRulesByUid[app.uid] = app
                blockedRulesByPackage[app.packageName] = app
            }
            Log.d(TAG, "App firewall rules updated: ${apps.size} rules active")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating app firewall rules: ${e.message}", e)
        }
    }

    /**
     * Synchronizes in-memory domain rules from Room.
     */
    fun updateDomainRulesFromEntities(rules: List<DomainRuleEntity>) {
        try {
            activeDomainRules.clear()
            activeDomainRules.addAll(rules.filter { it.isEnabled })
            Log.d(TAG, "Domain firewall rules updated: ${activeDomainRules.size} active rules")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating domain rules: ${e.message}", e)
        }
    }

    /**
     * Evaluates network connection with full deterministic priority:
     * 1. APP + DOMAIN
     * 2. GLOBAL DOMAIN
     * 3. APP RULE
     * 4. GLOBAL DEFAULT (ALLOW)
     */
    fun evaluateConnection(
        domain: String?,
        packageName: String?,
        uid: Int = -1,
        networkType: NetworkInterfaceType = NetworkInterfaceType.ANY
    ): FirewallEvaluationResult {
        return try {
            val normalizedTargetDomain = if (!domain.isNullOrBlank()) DomainNormalizer.normalize(domain) else null

            // 1. Check APP + DOMAIN rules if domain & package are provided
            if (normalizedTargetDomain != null && !packageName.isNullOrBlank()) {
                val matchingAppDomainRule = activeDomainRules.firstOrNull { rule ->
                    (rule.ruleType == RuleType.APP_BLOCK.name || rule.ruleType == RuleType.APP_ALLOW.name) &&
                            rule.packageName.equals(packageName, ignoreCase = true) &&
                            isNetworkScopeMatching(rule.networkScope, networkType) &&
                            DomainNormalizer.matches(normalizedTargetDomain, rule.domainPattern)
                }

                if (matchingAppDomainRule != null) {
                    if (matchingAppDomainRule.ruleType == RuleType.APP_ALLOW.name) {
                        return FirewallEvaluationResult(
                            decision = FirewallDecision.ALLOW,
                            uid = uid,
                            packageName = packageName,
                            matchedDomain = normalizedTargetDomain,
                            reason = "App allow rule for ${matchingAppDomainRule.domainPattern}"
                        )
                    } else {
                        val reason = "App domain block rule: ${matchingAppDomainRule.domainPattern} (${packageName})"
                        recordBlockEvent(uid, packageName, matchingAppDomainRule.appName ?: packageName, reason, networkType)
                        return FirewallEvaluationResult(
                            decision = FirewallDecision.BLOCK,
                            uid = uid,
                            packageName = packageName,
                            matchedDomain = normalizedTargetDomain,
                            reason = reason
                        )
                    }
                }
            }

            // 2. Check GLOBAL DOMAIN rules
            if (normalizedTargetDomain != null) {
                val matchingGlobalDomainRule = activeDomainRules.firstOrNull { rule ->
                    (rule.ruleType == RuleType.GLOBAL_BLOCK.name || rule.ruleType == RuleType.GLOBAL_ALLOW.name) &&
                            isNetworkScopeMatching(rule.networkScope, networkType) &&
                            DomainNormalizer.matches(normalizedTargetDomain, rule.domainPattern)
                }

                if (matchingGlobalDomainRule != null) {
                    if (matchingGlobalDomainRule.ruleType == RuleType.GLOBAL_ALLOW.name) {
                        return FirewallEvaluationResult(
                            decision = FirewallDecision.ALLOW,
                            uid = uid,
                            packageName = packageName,
                            matchedDomain = normalizedTargetDomain,
                            reason = "Global domain allow rule: ${matchingGlobalDomainRule.domainPattern}"
                        )
                    } else {
                        val reason = "Global domain block rule: ${matchingGlobalDomainRule.domainPattern}"
                        recordBlockEvent(uid, packageName ?: "all", "All Apps", reason, networkType)
                        return FirewallEvaluationResult(
                            decision = FirewallDecision.BLOCK,
                            uid = uid,
                            packageName = packageName,
                            matchedDomain = normalizedTargetDomain,
                            reason = reason
                        )
                    }
                }
            }

            // 3. Check APP RULE (Package / UID)
            if (uid > 0) {
                val uidResult = evaluateUid(uid, networkType)
                if (uidResult.decision == FirewallDecision.BLOCK) {
                    return uidResult.copy(matchedDomain = normalizedTargetDomain)
                }
            }
            if (!packageName.isNullOrBlank()) {
                val pkgResult = evaluatePackage(packageName, networkType)
                if (pkgResult.decision == FirewallDecision.BLOCK) {
                    return pkgResult.copy(matchedDomain = normalizedTargetDomain)
                }
            }

            // 4. DEFAULT -> ALLOW (Fail-open)
            FirewallEvaluationResult(
                decision = FirewallDecision.ALLOW,
                uid = uid,
                packageName = packageName,
                matchedDomain = normalizedTargetDomain,
                reason = "Default allow"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Firewall evaluation failed safely: ${e.message}. Failing open.", e)
            FirewallEvaluationResult(
                decision = FirewallDecision.ALLOW,
                uid = uid,
                packageName = packageName,
                matchedDomain = domain,
                reason = "Fail-open fallback: ${e.message}"
            )
        }
    }

    private fun isNetworkScopeMatching(ruleScope: String, networkType: NetworkInterfaceType): Boolean {
        if (ruleScope == "ANY" || networkType == NetworkInterfaceType.ANY) return true
        return ruleScope.equals(networkType.name, ignoreCase = true)
    }

    /**
     * Deterministic evaluation by UID and optional interface type.
     */
    fun evaluateUid(uid: Int, networkType: NetworkInterfaceType = NetworkInterfaceType.ANY): FirewallEvaluationResult {
        return try {
            val rule = blockedRulesByUid[uid]
            if (rule != null) {
                val isBlockedForNetwork = when (networkType) {
                    NetworkInterfaceType.MOBILE -> rule.blockMobile
                    NetworkInterfaceType.WIFI -> rule.blockWifi
                    NetworkInterfaceType.ANY -> rule.blockMobile || rule.blockWifi
                }

                if (isBlockedForNetwork) {
                    recordBlockEvent(uid, rule.packageName, rule.appName, rule.reason, networkType)
                    return FirewallEvaluationResult(
                        decision = FirewallDecision.BLOCK,
                        uid = uid,
                        packageName = rule.packageName,
                        reason = rule.reason
                    )
                }
            }

            FirewallEvaluationResult(
                decision = FirewallDecision.ALLOW,
                uid = uid,
                packageName = rule?.packageName,
                reason = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Firewall evaluation failed safely for UID $uid: ${e.message}. Failing open.", e)
            FirewallEvaluationResult(
                decision = FirewallDecision.ALLOW,
                uid = uid,
                packageName = null,
                reason = "Evaluation fallback (fail-open): ${e.message}"
            )
        }
    }

    /**
     * Deterministic evaluation by Package Name.
     */
    fun evaluatePackage(packageName: String, networkType: NetworkInterfaceType = NetworkInterfaceType.ANY): FirewallEvaluationResult {
        return try {
            val rule = blockedRulesByPackage[packageName]
            if (rule != null) {
                val isBlockedForNetwork = when (networkType) {
                    NetworkInterfaceType.MOBILE -> rule.blockMobile
                    NetworkInterfaceType.WIFI -> rule.blockWifi
                    NetworkInterfaceType.ANY -> rule.blockMobile || rule.blockWifi
                }

                if (isBlockedForNetwork) {
                    recordBlockEvent(rule.uid, packageName, rule.appName, rule.reason, networkType)
                    return FirewallEvaluationResult(
                        decision = FirewallDecision.BLOCK,
                        uid = rule.uid,
                        packageName = packageName,
                        reason = rule.reason
                    )
                }
            }

            FirewallEvaluationResult(
                decision = FirewallDecision.ALLOW,
                uid = rule?.uid ?: -1,
                packageName = packageName,
                reason = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Firewall evaluation failed safely for package $packageName: ${e.message}. Failing open.", e)
            FirewallEvaluationResult(
                decision = FirewallDecision.ALLOW,
                uid = -1,
                packageName = packageName,
                reason = "Evaluation fallback (fail-open): ${e.message}"
            )
        }
    }

    private fun recordBlockEvent(
        uid: Int,
        packageName: String,
        appName: String,
        reason: String,
        networkType: NetworkInterfaceType
    ) {
        val event = BlockedTrafficEventEntity(
            uid = uid,
            packageName = packageName,
            appName = appName,
            reason = reason,
            networkInterface = networkType.name,
            timestamp = System.currentTimeMillis()
        )
        _blockedEventFlow.tryEmit(event)
        repository?.let { repo ->
            scope.launch {
                try {
                    repo.insertBlockedTrafficEvent(event)
                    // Milestone 10: Trigger alert notification and history record
                    try {
                        com.example.dataguard.DataGuardApp.instance.dataPlanAlertManager.recordFirewallBlock(
                            appName = appName,
                            targetDomainOrReason = reason,
                            now = event.timestamp
                        )
                    } catch (_: Exception) {}
                } catch (e: Exception) {
                    Log.w(TAG, "Could not persist blocked traffic event: ${e.message}")
                }
            }
        }
    }

    fun isUidBlocked(uid: Int): Boolean {
        return evaluateUid(uid).decision == FirewallDecision.BLOCK
    }

    fun isPackageBlocked(packageName: String): Boolean {
        return evaluatePackage(packageName).decision == FirewallDecision.BLOCK
    }

    fun isDomainBlocked(
        domain: String,
        packageName: String? = null,
        networkType: NetworkInterfaceType = NetworkInterfaceType.ANY
    ): Boolean {
        return evaluateConnection(domain, packageName, networkType = networkType).decision == FirewallDecision.BLOCK
    }

    fun addBlockedUid(uid: Int, packageName: String? = null, appName: String = "App ($uid)", reason: String = "User firewall rule") {
        val effectivePackage = packageName ?: "uid.$uid"
        val rule = BlockedAppEntity(
            uid = uid,
            packageName = effectivePackage,
            appName = appName,
            blockedAt = System.currentTimeMillis(),
            reason = reason,
            blockMobile = true,
            blockWifi = true
        )
        blockedUids.add(uid)
        if (packageName != null) {
            blockedPackages.add(packageName)
            blockedRulesByPackage[packageName] = rule
        }
        blockedRulesByUid[uid] = rule
    }

    fun removeBlockedUid(uid: Int) {
        blockedUids.remove(uid)
        val rule = blockedRulesByUid.remove(uid)
        if (rule != null && rule.packageName != "uid.$uid") {
            blockedPackages.remove(rule.packageName)
            blockedRulesByPackage.remove(rule.packageName)
        }
    }

    fun addBlockedPackage(packageName: String, uid: Int = -1, appName: String = packageName, reason: String = "User firewall rule") {
        val rule = BlockedAppEntity(
            uid = uid,
            packageName = packageName,
            appName = appName,
            blockedAt = System.currentTimeMillis(),
            reason = reason,
            blockMobile = true,
            blockWifi = true
        )
        blockedPackages.add(packageName)
        blockedRulesByPackage[packageName] = rule
        if (uid > 0) {
            blockedUids.add(uid)
            blockedRulesByUid[uid] = rule
        }
    }

    fun removeBlockedPackage(packageName: String) {
        blockedPackages.remove(packageName)
        val rule = blockedRulesByPackage.remove(packageName)
        if (rule != null && rule.uid > 0) {
            blockedUids.remove(rule.uid)
            blockedRulesByUid.remove(rule.uid)
        }
    }

    fun addDomainRule(rule: DomainRuleEntity) {
        activeDomainRules.add(rule)
    }

    fun removeDomainRule(ruleId: Long) {
        activeDomainRules.removeIf { it.id == ruleId }
    }

    fun getBlockedPackages(): Set<String> {
        return blockedPackages.toSet()
    }

    fun getBlockedUids(): Set<Int> {
        return blockedUids.toSet()
    }

    fun getActiveDomainRules(): List<DomainRuleEntity> {
        return activeDomainRules.toList()
    }

    fun clear() {
        blockedPackages.clear()
        blockedUids.clear()
        blockedRulesByUid.clear()
        blockedRulesByPackage.clear()
        activeDomainRules.clear()
    }

    companion object {
        private const val TAG = "FirewallEngine"
    }
}
