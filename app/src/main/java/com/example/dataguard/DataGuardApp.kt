package com.example.dataguard

import android.app.Application
import com.example.dataguard.data.database.DataGuardDatabase
import com.example.dataguard.data.repository.DataRepository
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

    lateinit var toggleMobileDataUseCase: ToggleMobileDataUseCase
        private set

    lateinit var blockAppDataUseCase: BlockAppDataUseCase
        private set

    lateinit var detectIdleUseCase: DetectIdleUseCase
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
            blockedAppDao = database.blockedAppDao(),
            appLimitDao = database.appLimitDao(),
            dataToggleHistoryDao = database.dataToggleHistoryDao()
        )

        notificationHelper = NotificationHelper(this)
        permissionHelper = PermissionHelper(this)
        csvExporter = CsvExporter(this)

        getUsageStatsUseCase = GetUsageStatsUseCase(this, repository)
        toggleMobileDataUseCase = ToggleMobileDataUseCase(this, repository)
        blockAppDataUseCase = BlockAppDataUseCase(this, repository)
        detectIdleUseCase = DetectIdleUseCase(this)

        // Seed realistic historical records if first run
        CoroutineScope(Dispatchers.IO).launch {
            getUsageStatsUseCase.seedHistoricalDataIfEmpty()
            getUsageStatsUseCase("daily")
        }

        // Start background monitor service
        DataMonitorService.start(this)
    }

    companion object {
        lateinit var instance: DataGuardApp
            private set
    }
}
