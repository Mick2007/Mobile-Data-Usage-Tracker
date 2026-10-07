package com.example.dataguard.domain.usecase

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.example.dataguard.data.entity.DataToggleHistoryEntity
import com.example.dataguard.data.repository.DataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ToggleMobileDataUseCase(
    private val context: Context,
    private val repository: DataRepository
) {
    suspend operator fun invoke(enabled: Boolean, reason: String = "manual"): Result =
        withContext(Dispatchers.Main) {
            try {
                // Open cellular data settings intent on Android 8+
                val opened = openDataSettings()

                repository.insertToggleHistory(
                    DataToggleHistoryEntity(
                        timestamp = System.currentTimeMillis(),
                        enabled = enabled,
                        reason = reason
                    )
                )

                if (opened) {
                    Result.Success(if (enabled) "Cellular settings opened to enable data" else "Cellular settings opened to disable data")
                } else {
                    Result.Error("Unable to open device network settings")
                }
            } catch (e: Exception) {
                Result.Error(e.message ?: "Failed to execute toggle")
            }
        }

    private fun openDataSettings(): Boolean {
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            } else {
                Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
            }.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    sealed class Result {
        data class Success(val message: String) : Result()
        data class Error(val message: String) : Result()
    }
}
