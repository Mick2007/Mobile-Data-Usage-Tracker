package com.example.dataguard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.dataguard.DataGuardApp

object ViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val app = DataGuardApp.instance
        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                DashboardViewModel(
                    getUsageStatsUseCase = app.getUsageStatsUseCase,
                    toggleMobileDataUseCase = app.toggleMobileDataUseCase,
                    repository = app.repository
                ) as T
            }
            modelClass.isAssignableFrom(AppsViewModel::class.java) -> {
                AppsViewModel(
                    getUsageStatsUseCase = app.getUsageStatsUseCase,
                    blockAppDataUseCase = app.blockAppDataUseCase,
                    repository = app.repository,
                    appQuotaManager = app.appQuotaManager
                ) as T
            }
            modelClass.isAssignableFrom(StatsViewModel::class.java) -> {
                StatsViewModel(
                    repository = app.repository,
                    csvExporter = app.csvExporter,
                    networkStatsHelper = app.networkStatsHelper,
                    permissionHelper = app.permissionHelper
                ) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(
                    repository = app.repository,
                    permissionHelper = app.permissionHelper
                ) as T
            }
            modelClass.isAssignableFrom(FirewallViewModel::class.java) -> {
                FirewallViewModel(
                    repository = app.repository,
                    blockAppDataUseCase = app.blockAppDataUseCase,
                    getUsageStatsUseCase = app.getUsageStatsUseCase
                ) as T
            }
            modelClass.isAssignableFrom(WebsitesViewModel::class.java) -> {
                WebsitesViewModel(
                    repository = app.repository
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
