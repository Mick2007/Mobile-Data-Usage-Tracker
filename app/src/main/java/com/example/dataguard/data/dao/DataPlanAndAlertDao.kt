package com.example.dataguard.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.dataguard.data.entity.DataPlanEntity
import com.example.dataguard.data.entity.NotificationHistoryEntity
import com.example.dataguard.data.entity.NotificationPreferencesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DataPlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePlan(plan: DataPlanEntity)

    @Update
    suspend fun updatePlan(plan: DataPlanEntity)

    @Query("SELECT * FROM data_plans WHERE id = 1 LIMIT 1")
    fun getPlanFlow(): Flow<DataPlanEntity?>

    @Query("SELECT * FROM data_plans WHERE id = 1 LIMIT 1")
    suspend fun getPlan(): DataPlanEntity?

    @Query("DELETE FROM data_plans")
    suspend fun clearPlan()
}

@Dao
interface NotificationHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: NotificationHistoryEntity): Long

    @Query("SELECT * FROM notification_history ORDER BY timestamp DESC")
    fun getAllHistoryFlow(): Flow<List<NotificationHistoryEntity>>

    @Query("SELECT * FROM notification_history WHERE category = :category ORDER BY timestamp DESC")
    fun getHistoryByCategoryFlow(category: String): Flow<List<NotificationHistoryEntity>>

    @Query("SELECT * FROM notification_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistoryList(limit: Int = 100): List<NotificationHistoryEntity>

    @Query("UPDATE notification_history SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllAsRead()

    @Query("DELETE FROM notification_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM notification_history")
    suspend fun clearAllHistory()
}

@Dao
interface NotificationPreferencesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePreferences(preferences: NotificationPreferencesEntity)

    @Query("SELECT * FROM notification_preferences WHERE id = 1 LIMIT 1")
    fun getPreferencesFlow(): Flow<NotificationPreferencesEntity?>

    @Query("SELECT * FROM notification_preferences WHERE id = 1 LIMIT 1")
    suspend fun getPreferences(): NotificationPreferencesEntity?
}
