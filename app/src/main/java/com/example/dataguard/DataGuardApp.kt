package com.example.dataguard

import android.app.Application
import android.content.Context
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
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    lateinit var hotspotMonitor: com.example.dataguard.domain.hotspot.HotspotMonitor
        private set

    lateinit var dataPlanAlertManager: com.example.dataguard.domain.plan.DataPlanAlertManager
        private set

    private val prefs by lazy { getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE) }

    var previousDayLimitBytes: Long
        get() = prefs.getLong(KEY_PREVIOUS_DAY_LIMIT_BYTES, DEFAULT_DAILY_LIMIT_BYTES)
        set(value) = prefs.edit().putLong(KEY_PREVIOUS_DAY_LIMIT_BYTES, value).apply()

    var dailyLimitBytes: Long
        get() {
            val stored = prefs.getLong(KEY_DAILY_LIMIT_BYTES, 0L)
            return if (stored > 0L) stored else previousDayLimitBytes
        }
        set(value) {
            val current = prefs.getLong(KEY_DAILY_LIMIT_BYTES, 0L)
            if (current > 0L && current != value) {
                previousDayLimitBytes = current
            }
            prefs.edit().putLong(KEY_DAILY_LIMIT_BYTES, value).apply()
        }

    fun clearDailyLimit() {
        val current = prefs.getLong(KEY_DAILY_LIMIT_BYTES, 0L)
        if (current > 0L) {
            previousDayLimitBytes = current
        }
        prefs.edit().remove(KEY_DAILY_LIMIT_BYTES).apply()
    }

    var weeklyLimitBytes: Long
        get() = prefs.getLong(KEY_WEEKLY_LIMIT_BYTES, DEFAULT_WEEKLY_LIMIT_BYTES)
        set(value) = prefs.edit().putLong(KEY_WEEKLY_LIMIT_BYTES, value).apply()

    var monthlyLimitBytes: Long
        get() = prefs.getLong(KEY_MONTHLY_LIMIT_BYTES, DEFAULT_MONTHLY_LIMIT_BYTES)
        set(value) = prefs.edit().putLong(KEY_MONTHLY_LIMIT_BYTES, value).apply()

    var dailyLimitUnit: String
        get() = prefs.getString(KEY_DAILY_LIMIT_UNIT, "MB") ?: "MB"
        set(value) = prefs.edit().putString(KEY_DAILY_LIMIT_UNIT, value).apply()

    var weeklyLimitUnit: String
        get() = prefs.getString(KEY_WEEKLY_LIMIT_UNIT, "GB") ?: "GB"
        set(value) = prefs.edit().putString(KEY_WEEKLY_LIMIT_UNIT, value).apply()

    var monthlyLimitUnit: String
        get() = prefs.getString(KEY_MONTHLY_LIMIT_UNIT, "GB") ?: "GB"
        set(value) = prefs.edit().putString(KEY_MONTHLY_LIMIT_UNIT, value).apply()

    var isAutoIdleSaverEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_IDLE_SAVER, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_IDLE_SAVER, value).apply()

    var dailyLimitMB: Long
        get() = dailyLimitBytes / (1024L * 1024L)
        set(value) {
            dailyLimitBytes = value * 1024L * 1024L
        }

    var weeklyLimitMB: Long
        get() = weeklyLimitBytes / (1024L * 1024L)
        set(value) {
            weeklyLimitBytes = value * 1024L * 1024L
        }

    var monthlyLimitMB: Long
        get() = monthlyLimitBytes / (1024L * 1024L)
        set(value) {
            monthlyLimitBytes = value * 1024L * 1024L
        }

    private val _appThemeMode by lazy {
        MutableStateFlow(
            AppThemeMode.fromKey(
                prefs.getString(KEY_APP_THEME, AppThemeMode.SYSTEM.key) ?: AppThemeMode.SYSTEM.key
            )
        )
    }
    val appThemeMode: StateFlow<AppThemeMode>
        get() = _appThemeMode.asStateFlow()

    fun setAppThemeMode(mode: AppThemeMode) {
        _appThemeMode.value = mode
        prefs.edit().putString(KEY_APP_THEME, mode.key).apply()
    }

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
            domainRuleDao = database.domainRuleDao(),
            hotspotSessionDao = database.hotspotSessionDao(),
            dataPlanDao = database.dataPlanDao(),
            notificationHistoryDao = database.notificationHistoryDao(),
            notificationPreferencesDao = database.notificationPreferencesDao()
        )

        notificationHelper = NotificationHelper(this)
        permissionHelper = PermissionHelper(this)
        csvExporter = CsvExporter(this)

        getUsageStatsUseCase = GetUsageStatsUseCase(this, repository, permissionHelper)
        hotspotMonitor = com.example.dataguard.domain.hotspot.HotspotMonitor(
            context = this,
            repository = repository,
            permissionHelper = permissionHelper
        )
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

        dataPlanAlertManager = com.example.dataguard.domain.plan.DataPlanAlertManager(
            context = this,
            repository = repository,
            networkStatsHelper = networkStatsHelper,
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

        const val PREFS_SETTINGS = "dataguard_settings_prefs"
        const val KEY_DAILY_LIMIT_BYTES = "daily_limit_bytes"
        const val KEY_PREVIOUS_DAY_LIMIT_BYTES = "previous_day_limit_bytes"
        const val KEY_WEEKLY_LIMIT_BYTES = "weekly_limit_bytes"
        const val KEY_MONTHLY_LIMIT_BYTES = "monthly_limit_bytes"
        const val KEY_DAILY_LIMIT_UNIT = "daily_limit_unit"
        const val KEY_WEEKLY_LIMIT_UNIT = "weekly_limit_unit"
        const val KEY_MONTHLY_LIMIT_UNIT = "monthly_limit_unit"
        const val KEY_AUTO_IDLE_SAVER = "auto_idle_saver"
        const val KEY_APP_THEME = "app_theme_mode"

        const val DEFAULT_DAILY_LIMIT_BYTES: Long = 1000L * 1024L * 1024L // 1000 MB
        val DEFAULT_WEEKLY_LIMIT_BYTES: Long = (6.8 * 1024.0 * 1024.0 * 1024.0).toLong() // 6.8 GB (7,301,444,403 bytes)
        val DEFAULT_MONTHLY_LIMIT_BYTES: Long = (29.3 * 1024.0 * 1024.0 * 1024.0).toLong() // 29.3 GB (31,460,635,443 bytes)
    }
}
