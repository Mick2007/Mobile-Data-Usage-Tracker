package com.example.dataguard.data.repository

import com.example.dataguard.data.dao.AppLimitDao
import com.example.dataguard.data.dao.AppNetworkUsageDao
import com.example.dataguard.data.dao.BlockedAppDao
import com.example.dataguard.data.dao.BlockedTrafficEventDao
import com.example.dataguard.data.dao.DataToggleHistoryDao
import com.example.dataguard.data.dao.DomainRuleDao
import com.example.dataguard.data.dao.NetworkFlowDao
import com.example.dataguard.data.dao.QuotaBreachEventDao
import com.example.dataguard.data.dao.UsageDao
import com.example.dataguard.data.entity.AppLimitEntity
import com.example.dataguard.data.entity.AppNetworkUsageRecordEntity
import com.example.dataguard.data.entity.BlockedAppEntity
import com.example.dataguard.data.entity.BlockedTrafficEventEntity
import com.example.dataguard.data.entity.DataToggleHistoryEntity
import com.example.dataguard.data.entity.DomainRuleEntity
import com.example.dataguard.data.entity.NetworkFlowEntity
import com.example.dataguard.data.entity.QuotaBreachEventEntity
import com.example.dataguard.data.entity.UsageEntity
import kotlinx.coroutines.flow.Flow

class DataRepository(
    private val usageDao: UsageDao,
    private val appNetworkUsageDao: AppNetworkUsageDao,
    private val blockedAppDao: BlockedAppDao,
    private val appLimitDao: AppLimitDao,
    private val dataToggleHistoryDao: DataToggleHistoryDao,
    private val quotaBreachEventDao: QuotaBreachEventDao,
    private val blockedTrafficEventDao: BlockedTrafficEventDao? = null,
    private val networkFlowDao: NetworkFlowDao? = null,
    private val domainRuleDao: DomainRuleDao? = null
) {
    // Usage operations
    suspend fun insertUsage(usage: UsageEntity): Long = usageDao.insertUsage(usage)

    fun getUsageByPeriod(period: String, limit: Int = 100): Flow<List<UsageEntity>> =
        usageDao.getUsageByPeriod(period, limit)

    fun getUsageInTimeRange(startTime: Long, endTime: Long): Flow<List<UsageEntity>> =
        usageDao.getUsageInTimeRange(startTime, endTime)

    fun getLatestUsage(): Flow<UsageEntity?> = usageDao.getLatestUsage()

    fun getAllRecentUsage(limit: Int = 200): Flow<List<UsageEntity>> =
        usageDao.getAllRecentUsage(limit)

    suspend fun deleteOldUsage(cutoffTime: Long) = usageDao.deleteOldUsage(cutoffTime)

    suspend fun clearAllUsage() = usageDao.clearAllUsage()

    // Normalized App Network Usage Records
    suspend fun insertOrUpdateAppRecords(records: List<AppNetworkUsageRecordEntity>) =
        appNetworkUsageDao.insertOrUpdateRecords(records)

    fun getAppRecordsForDay(epochDay: Long): Flow<List<AppNetworkUsageRecordEntity>> =
        appNetworkUsageDao.getRecordsForDay(epochDay)

    fun getAppRecordsInDayRange(startEpochDay: Long, endEpochDay: Long): Flow<List<AppNetworkUsageRecordEntity>> =
        appNetworkUsageDao.getRecordsInDayRange(startEpochDay, endEpochDay)

    fun getAppHistory(uid: Int, startEpochDay: Long, endEpochDay: Long): Flow<List<AppNetworkUsageRecordEntity>> =
        appNetworkUsageDao.getAppHistory(uid, startEpochDay, endEpochDay)

    suspend fun clearAllAppRecords() = appNetworkUsageDao.clearAll()

    // Blocked app operations
    suspend fun insertBlockedApp(app: BlockedAppEntity) = blockedAppDao.insertBlockedApp(app)

    suspend fun updateBlockedApp(app: BlockedAppEntity) = blockedAppDao.updateBlockedApp(app)

    suspend fun deleteBlockedApp(app: BlockedAppEntity) = blockedAppDao.deleteBlockedApp(app)

    suspend fun deleteBlockedAppByUid(uid: Int) = blockedAppDao.deleteBlockedAppByUid(uid)

    fun getAllBlockedApps(): Flow<List<BlockedAppEntity>> = blockedAppDao.getAllBlockedApps()

    suspend fun getBlockedApp(uid: Int): BlockedAppEntity? = blockedAppDao.getBlockedApp(uid)

    suspend fun clearAllBlockedApps() = blockedAppDao.clearAllBlockedApps()

    // Blocked Traffic Events operations
    suspend fun insertBlockedTrafficEvent(event: BlockedTrafficEventEntity): Long =
        blockedTrafficEventDao?.insertEvent(event) ?: -1L

    fun getRecentBlockedTrafficEvents(limit: Int = 100): Flow<List<BlockedTrafficEventEntity>> =
        blockedTrafficEventDao?.getRecentBlockedEvents(limit) ?: kotlinx.coroutines.flow.flowOf(emptyList())

    fun getBlockedTrafficEventsForApp(uid: Int): Flow<List<BlockedTrafficEventEntity>> =
        blockedTrafficEventDao?.getEventsForApp(uid) ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun clearAllBlockedTrafficEvents() {
        blockedTrafficEventDao?.clearAllEvents()
    }

    // App limit / Quota operations
    suspend fun insertAppLimit(limit: AppLimitEntity) = appLimitDao.insertAppLimit(limit)

    suspend fun updateAppLimit(limit: AppLimitEntity) = appLimitDao.updateAppLimit(limit)

    suspend fun deleteAppLimit(limit: AppLimitEntity) = appLimitDao.deleteAppLimit(limit)

    suspend fun deleteAppLimitByUid(uid: Int) = appLimitDao.deleteAppLimitByUid(uid)

    fun getAllAppLimits(): Flow<List<AppLimitEntity>> = appLimitDao.getAllAppLimits()

    suspend fun getAppLimit(uid: Int): AppLimitEntity? = appLimitDao.getAppLimit(uid)

    fun getAppLimitFlow(uid: Int): Flow<AppLimitEntity?> = appLimitDao.getAppLimitFlow(uid)

    suspend fun clearAllAppLimits() = appLimitDao.clearAllAppLimits()

    // Quota breach events operations
    suspend fun insertQuotaBreachEvent(event: QuotaBreachEventEntity): Long =
        quotaBreachEventDao.insertEvent(event)

    fun getRecentQuotaBreachEvents(limit: Int = 50): Flow<List<QuotaBreachEventEntity>> =
        quotaBreachEventDao.getRecentEvents(limit)

    fun getQuotaBreachEventsForApp(uid: Int): Flow<List<QuotaBreachEventEntity>> =
        quotaBreachEventDao.getEventsForApp(uid)

    suspend fun clearAllQuotaBreachEvents() = quotaBreachEventDao.clearAllEvents()

    // Data toggle history operations
    suspend fun insertToggleHistory(history: DataToggleHistoryEntity) =
        dataToggleHistoryDao.insertToggleHistory(history)

    fun getToggleHistory(limit: Int = 100): Flow<List<DataToggleHistoryEntity>> =
        dataToggleHistoryDao.getToggleHistory(limit)

    suspend fun deleteOldHistory(cutoffTime: Long) = dataToggleHistoryDao.deleteOldHistory(cutoffTime)

    suspend fun clearAllHistory() = dataToggleHistoryDao.clearAllHistory()

    // Network Flow / Observed Domain operations
    suspend fun upsertTrafficFlow(
        flowKey: String,
        domain: String,
        uid: Int,
        packageName: String,
        appName: String,
        destinationIp: String,
        protocol: String,
        uploadDelta: Long,
        downloadDelta: Long,
        networkType: String,
        confidence: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        networkFlowDao?.upsertTraffic(
            flowKey = flowKey,
            domain = domain,
            uid = uid,
            packageName = packageName,
            appName = appName,
            destinationIp = destinationIp,
            protocol = protocol,
            uploadDelta = uploadDelta,
            downloadDelta = downloadDelta,
            networkType = networkType,
            confidence = confidence,
            timestamp = timestamp
        )
    }

    fun getFlowsSince(sinceTimestamp: Long): Flow<List<NetworkFlowEntity>> =
        networkFlowDao?.getFlowsSince(sinceTimestamp) ?: kotlinx.coroutines.flow.flowOf(emptyList())

    fun getAllFlows(): Flow<List<NetworkFlowEntity>> =
        networkFlowDao?.getAllFlows() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    fun getFlowsForDomain(domain: String): Flow<List<NetworkFlowEntity>> =
        networkFlowDao?.getFlowsForDomain(domain) ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun getFlowListForDomain(domain: String): List<NetworkFlowEntity> =
        networkFlowDao?.getFlowListForDomain(domain) ?: emptyList()

    suspend fun clearAllFlows() {
        networkFlowDao?.clearAll()
    }

    // Domain Firewall Rules operations
    suspend fun insertOrUpdateDomainRule(rule: DomainRuleEntity): Long =
        domainRuleDao?.insertOrUpdate(rule) ?: -1L

    suspend fun updateDomainRule(rule: DomainRuleEntity) {
        domainRuleDao?.update(rule)
    }

    suspend fun deleteDomainRule(rule: DomainRuleEntity) {
        domainRuleDao?.delete(rule)
    }

    suspend fun deleteDomainRuleById(id: Long) {
        domainRuleDao?.deleteById(id)
    }

    suspend fun deleteGlobalDomainRule(normalizedDomain: String) {
        domainRuleDao?.deleteGlobalDomainRule(normalizedDomain)
    }

    suspend fun deleteAppDomainRule(normalizedDomain: String, packageName: String) {
        domainRuleDao?.deleteAppDomainRule(normalizedDomain, packageName)
    }

    fun getAllDomainRulesFlow(): Flow<List<DomainRuleEntity>> =
        domainRuleDao?.getAllRulesFlow() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun getAllDomainRules(): List<DomainRuleEntity> =
        domainRuleDao?.getAllRules() ?: emptyList()

    fun getActiveDomainRulesFlow(): Flow<List<DomainRuleEntity>> =
        domainRuleDao?.getActiveRulesFlow() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun getActiveDomainRules(): List<DomainRuleEntity> =
        domainRuleDao?.getActiveRules() ?: emptyList()

    fun getRulesForDomainFlow(normalizedDomain: String): Flow<List<DomainRuleEntity>> =
        domainRuleDao?.getRulesForDomainFlow(normalizedDomain) ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun getRulesForDomain(normalizedDomain: String): List<DomainRuleEntity> =
        domainRuleDao?.getRulesForDomain(normalizedDomain) ?: emptyList()

    suspend fun clearAllDomainRules() {
        domainRuleDao?.clearAll()
    }
}
