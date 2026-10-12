package com.example.dataguard.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.dataguard.data.entity.HotspotSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HotspotSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: HotspotSessionEntity): Long

    @Update
    suspend fun updateSession(session: HotspotSessionEntity)

    @Query("SELECT * FROM hotspot_sessions WHERE isActive = 1 ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveSession(): HotspotSessionEntity?

    @Query("SELECT * FROM hotspot_sessions WHERE isActive = 1 ORDER BY startTime DESC LIMIT 1")
    fun getActiveSessionFlow(): Flow<HotspotSessionEntity?>

    @Query("SELECT * FROM hotspot_sessions ORDER BY startTime DESC")
    fun getAllSessionsFlow(): Flow<List<HotspotSessionEntity>>

    @Query("SELECT * FROM hotspot_sessions ORDER BY startTime DESC")
    suspend fun getAllSessions(): List<HotspotSessionEntity>

    @Query("SELECT * FROM hotspot_sessions WHERE startTime >= :since ORDER BY startTime DESC")
    fun getSessionsSinceFlow(since: Long): Flow<List<HotspotSessionEntity>>

    @Query("SELECT * FROM hotspot_sessions WHERE startTime >= :since ORDER BY startTime DESC")
    suspend fun getSessionsSince(since: Long): List<HotspotSessionEntity>

    @Query("SELECT * FROM hotspot_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): HotspotSessionEntity?

    @Query("SELECT SUM(totalBytes) FROM hotspot_sessions WHERE startTime >= :since")
    fun getTotalBytesSinceFlow(since: Long): Flow<Long?>

    @Query("SELECT SUM(uploadBytes) FROM hotspot_sessions WHERE startTime >= :since")
    fun getUploadBytesSinceFlow(since: Long): Flow<Long?>

    @Query("SELECT SUM(downloadBytes) FROM hotspot_sessions WHERE startTime >= :since")
    fun getDownloadBytesSinceFlow(since: Long): Flow<Long?>

    @Query("UPDATE hotspot_sessions SET isActive = 0, endTime = :endTime WHERE isActive = 1")
    suspend fun closeAllActiveSessions(endTime: Long = System.currentTimeMillis())

    @Query("DELETE FROM hotspot_sessions")
    suspend fun clearAll()
}
