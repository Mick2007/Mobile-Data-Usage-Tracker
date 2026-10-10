package com.example.dataguard

import android.app.Application
import com.example.dataguard.data.database.DataGuardDatabase
import com.example.dataguard.data.repository.DataRepository
import com.example.dataguard.domain.usecase.AppQuotaManager
import com.example.dataguard.domain.usecase.BlockAppDataUseCase
import com.example.dataguard.domain.usecase.DetectIdleUseCase
import com.example.dataguard.domain.usecase.GetUsageStatsUseCase
import com.example.dataguard.domain.usecase.ToggleMobileDataUseCase
import com.example.dataguard.service.DataMonitorService
import com.example.dataguard.utils.CsvExporter
import com.example.dataguard.utils.NotificationHelper
import com.example.dataguard.utils.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DataGuardApp : Application() {

    lateinit var database: DataGuardDatabase
        private set

    lateinit var repository: DataRepository
        private set

    lateinit var notificationHelper: NotificationHelper
        private set

    lateinit var permissionHelper: PermissionHelper
        private set

    lateinit var csvExporter: CsvExporter
        private set

    lateinit var getUsageStatsUseCase: GetUsageStatsUseCase
        private set

    val networkStatsHelper: com.example.dataguard.domain.usecase.NetworkStatsHelper
        get() = getUsageStatsUseCase.networkStatsHelper

    lateinit var toggleMobileDataUseCase: ToggleMobileDataUseCase
        private set

    lateinit var blockAppDataUseCase: BlockAppDataUseCase
        private set

    lateinit var detectIdleUseCase: DetectIdleUseCase
        private set

    lateinit var appQuotaManager: AppQuotaManager
        private set

    lateinit var firewallEngine: com.example.dataguard.vpn.engine.FirewallEngine
        private set

    lateinit var domainFlowTracker: com.example.dataguard.vpn.engine.DomainFlowTracker
        private set

    lateinit var trafficAccountingEngine: com.example.dataguard.vpn.engine.TrafficAccountingEngine
        private set

    lateinit var packetProcessor: com.example.dataguard.vpn.engine.PacketProcessor
        private set

    lateinit var vpnSessionManager: com.example.dataguard.vpn.VpnSessionManager
        private set

    var dailyLimitMB: Long = 1000L
    var weeklyLimitMB: Long = 7000L
    var monthlyLimitMB: Long = 30000L
    var isAutoIdleSaverEnabled: Boolean = true

    val dailyLimitBytes: Long
        get() = dailyLimitMB * 1024L * 1024L

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = DataGuardDatabase.getInstance(this)
        repository = DataRepository(
            usageDao = database.usageDao(),
            appNetworkUsageDao = database.appNetworkUsageDao(),
            blockedAppDao = database.blockedAppDao(),
            appLimitDao = database.appLimitDao(),
            dataToggleHistoryDao = database.dataToggleHistoryDao(),
            quotaBreachEventDao = database.quotaBreachEventDao(),
            blockedTrafficEventDao = database.blockedTrafficEventDao(),
            networkFlowDao = database.networkFlowDao(),
            domainRuleDao = database.domainRuleDao()
        )

        notificationHelper = NotificationHelper(this)
        permissionHelper = PermissionHelper(this)
        csvExporter = CsvExporter(this)

        getUsageStatsUseCase = GetUsageStatsUseCase(this, repository, permissionHelper)
        toggleMobileDataUseCase = ToggleMobileDataUseCase(this, repository)
        blockAppDataUseCase = BlockAppDataUseCase(this, repository)
        detectIdleUseCase = DetectIdleUseCase(this)

        appQuotaManager = AppQuotaManager(
            context = this,
            repository = repository,
            networkStatsHelper = networkStatsHelper,
            blockAppDataUseCase = blockAppDataUseCase,
            notificationHelper = notificationHelper
        )

        firewallEngine = com.example.dataguard.vpn.engine.FirewallEngine(repository = repository)
        trafficAccountingEngine = com.example.dataguard.vpn.engine.TrafficAccountingEngine()
        domainFlowTracker = com.example.dataguard.vpn.engine.DomainFlowTracker(
            context = this,
            repository = repository
        )
        packetProcessor = com.example.dataguard.vpn.engine.PacketProcessor(
            accountingEngine = trafficAccountingEngine,
            firewallEngine = firewallEngine,
            flowTracker = domainFlowTracker
        )
        vpnSessionManager = com.example.dataguard.vpn.VpnSessionManager(
            context = this,
            firewallEngine = firewallEngine,
            trafficAccountingEngine = trafficAccountingEngine,
            packetProcessor = packetProcessor
        )

        // Capture initial telemetry on startup
        CoroutineScope(Dispatchers.IO).launch {
            getUsageStatsUseCase(com.example.dataguard.domain.model.TimeRangeSelection.today())
            appQuotaManager.checkAndEnforceAllLimits()
        }

        // Start background monitor service
        DataMonitorService.start(this)
    }

    companion object {
        lateinit var instance: DataGuardApp
            private set
    }
}
