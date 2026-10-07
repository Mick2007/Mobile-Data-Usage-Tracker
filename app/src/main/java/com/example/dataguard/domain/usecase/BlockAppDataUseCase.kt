package com.example.dataguard.domain.usecase

import android.content.Context
import android.os.Build
import com.example.dataguard.data.entity.BlockedAppEntity
import com.example.dataguard.data.repository.DataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BlockAppDataUseCase(
    private val context: Context,
    private val repository: DataRepository
) {
    private val networkPolicyManager by lazy {
        try {
            context.getSystemService("netpolicy")
        } catch (e: Exception) {
            null
        }
    }

    suspend operator fun invoke(
        uid: Int,
        packageName: String,
        appName: String,
        block: Boolean
    ): Result = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                applyNetworkPolicy(uid, block)
            }

            if (block) {
                repository.insertBlockedApp(
                    BlockedAppEntity(
                        uid = uid,
                        packageName = packageName,
                        appName = appName,
                        blockedAt = System.currentTimeMillis()
                    )
                )
            } else {
                repository.deleteBlockedApp(
                    BlockedAppEntity(
                        uid = uid,
                        packageName = packageName,
                        appName = appName
                    )
                )
            }

            Result.Success
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to update app policy")
        }
    }

    private fun applyNetworkPolicy(uid: Int, block: Boolean) {
        try {
            val npm = networkPolicyManager ?: return
            val getMethod = npm.javaClass.getMethod("getUidPolicy", Int::class.java)
            val currentPolicy = getMethod.invoke(npm, uid) as? Int ?: 0

            val newPolicy = if (block) {
                currentPolicy or 0x01 // POLICY_REJECT_METERED_BACKGROUND
            } else {
                currentPolicy and 0x01.inv()
            }

            val setMethod = npm.javaClass.getMethod("setUidPolicy", Int::class.java, Int::class.java)
            setMethod.invoke(npm, uid, newPolicy)
        } catch (ignored: Exception) {
            // On unrooted non-system devices, setting policy via reflection fails gracefully
        }
    }

    sealed class Result {
        object Success : Result()
        data class Error(val message: String) : Result()
    }
}
