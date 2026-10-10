package com.example.dataguard.domain.usecase

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.dataguard.data.entity.BlockedAppEntity
import com.example.dataguard.data.repository.DataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BlockAppDataUseCase(
    private val context: Context,
    private val repository: DataRepository
) {
    suspend operator fun invoke(
        uid: Int,
        packageName: String,
        appName: String,
        block: Boolean,
        reason: String = "User firewall rule",
        blockMobile: Boolean = true,
        blockWifi: Boolean = true
    ): Result = withContext(Dispatchers.IO) {
        try {
            if (block) {
                repository.insertBlockedApp(
                    BlockedAppEntity(
                        uid = uid,
                        packageName = packageName,
                        appName = appName,
                        blockedAt = System.currentTimeMillis(),
                        reason = reason,
                        blockMobile = blockMobile,
                        blockWifi = blockWifi
                    )
                )
                // Log block event in history
                repository.insertBlockedTrafficEvent(
                    com.example.dataguard.data.entity.BlockedTrafficEventEntity(
                        uid = uid,
                        packageName = packageName,
                        appName = appName,
                        reason = reason,
                        networkInterface = if (blockMobile && blockWifi) "ALL" else if (blockMobile) "MOBILE" else "WIFI"
                    )
                )
            } else {
                repository.deleteBlockedAppByUid(uid)
            }

            Result.Success
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to update app policy")
        }
    }

    /**
     * Opens official Android Settings screen for user to review background data restrictions
     * without using restricted/hidden platform reflection APIs.
     */
    fun openSystemAppRestrictions(packageName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val intent = Intent(
                    Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
                    Uri.parse("package:$packageName")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } else {
                openAppDetailsSettings(packageName)
            }
        } catch (e: Exception) {
            openAppDetailsSettings(packageName)
        }
    }

    private fun openAppDetailsSettings(packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (ignored: Exception) {
        }
    }

    sealed class Result {
        object Success : Result()
        data class Error(val message: String) : Result()
    }
}
