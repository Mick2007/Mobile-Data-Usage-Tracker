package com.example.dataguard.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.dataguard.data.entity.AppLimitEntity
import com.example.dataguard.data.entity.BlockedAppEntity
import com.example.dataguard.data.entity.DataToggleHistoryEntity
import com.example.dataguard.data.entity.UsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {
    @Insert
    suspend fun insertUsage(usage: UsageEntity): Long

    @Query("SELECT * FROM usage_stats WHERE period = :period ORDER BY timestamp DESC LIMIT :limit")
    fun getUsageByPeriod(period: String, limit: Int = 100): Flow<List<UsageEntity>>

    @Query("SELECT * FROM usage_stats WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun getUsageInTimeRange(startTime: Long, endTime: Long): Flow<List<UsageEntity>>

    @Query("SELECT * FROM usage_stats ORDER BY timestamp DESC LIMIT 1")
    fun getLatestUsage(): Flow<UsageEntity?>

    @Query("SELECT * FROM usage_stats ORDER BY timestamp DESC LIMIT :limit")
    fun getAllRecentUsage(limit: Int = 200): Flow<List<UsageEntity>>

    @Query("DELETE FROM usage_stats WHERE timestamp < :cutoffTime")
    suspend fun deleteOldUsage(cutoffTime: Long)

    @Query("DELETE FROM usage_stats")
    suspend fun clearAllUsage()
}

@Dao
interface BlockedAppDao {
    @Insert
    suspend fun insertBlockedApp(app: BlockedAppEntity)

    @Update
    suspend fun updateBlockedApp(app: BlockedAppEntity)

    @Delete
    suspend fun deleteBlockedApp(app: BlockedAppEntity)

    @Query("SELECT * FROM blocked_apps ORDER BY blockedAt DESC")
    fun getAllBlockedApps(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps WHERE uid = :uid")
    suspend fun getBlockedApp(uid: Int): BlockedAppEntity?

    @Query("DELETE FROM blocked_apps")
    suspend fun clearAllBlockedApps()
}

@Dao
interface AppLimitDao {
    @Insert
    suspend fun insertAppLimit(limit: AppLimitEntity)

    @Update
    suspend fun updateAppLimit(limit: AppLimitEntity)

    @Delete
    suspend fun deleteAppLimit(limit: AppLimitEntity)

    @Query("SELECT * FROM app_limits ORDER BY packageName ASC")
    fun getAllAppLimits(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limits WHERE uid = :uid")
    suspend fun getAppLimit(uid: Int): AppLimitEntity?

    @Query("DELETE FROM app_limits")
    suspend fun clearAllAppLimits()
}

@Dao
interface DataToggleHistoryDao {
    @Insert
    suspend fun insertToggleHistory(history: DataToggleHistoryEntity)

    @Query("SELECT * FROM data_toggle_history ORDER BY timestamp DESC LIMIT :limit")
    fun getToggleHistory(limit: Int = 100): Flow<List<DataToggleHistoryEntity>>

    @Query("DELETE FROM data_toggle_history WHERE timestamp < :cutoffTime")
    suspend fun deleteOldHistory(cutoffTime: Long)

    @Query("DELETE FROM data_toggle_history")
    suspend fun clearAllHistory()
}
