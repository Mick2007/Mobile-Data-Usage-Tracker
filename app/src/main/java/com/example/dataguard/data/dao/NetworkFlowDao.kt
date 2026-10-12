package com.example.dataguard.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.dataguard.data.entity.NetworkFlowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkFlowDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(flow: NetworkFlowEntity): Long

    @Query("SELECT * FROM network_flow_records WHERE flowKey = :flowKey LIMIT 1")
    suspend fun getFlowByKey(flowKey: String): NetworkFlowEntity?

    @Query("SELECT * FROM network_flow_records WHERE lastSeen >= :sinceTimestamp ORDER BY totalBytes DESC")
    fun getFlowsSince(sinceTimestamp: Long): Flow<List<NetworkFlowEntity>>

    @Query("SELECT * FROM network_flow_records ORDER BY totalBytes DESC")
    fun getAllFlows(): Flow<List<NetworkFlowEntity>>

    @Query("SELECT * FROM network_flow_records WHERE domain = :domain ORDER BY lastSeen DESC")
    fun getFlowsForDomain(domain: String): Flow<List<NetworkFlowEntity>>

    @Query("SELECT * FROM network_flow_records WHERE domain = :domain")
    suspend fun getFlowListForDomain(domain: String): List<NetworkFlowEntity>

    @Query("SELECT * FROM network_flow_records WHERE packageName = :packageName OR uid = :uid ORDER BY lastSeen DESC")
    fun getFlowsForAppFlow(packageName: String, uid: Int): Flow<List<NetworkFlowEntity>>

    @Query("SELECT * FROM network_flow_records WHERE packageName = :packageName OR uid = :uid ORDER BY lastSeen DESC")
    suspend fun getFlowsForApp(packageName: String, uid: Int): List<NetworkFlowEntity>

    @Query("SELECT * FROM network_flow_records WHERE lastSeen BETWEEN :startTime AND :endTime ORDER BY totalBytes DESC")
    fun getFlowsInTimeRange(startTime: Long, endTime: Long): Flow<List<NetworkFlowEntity>>

    @Query("SELECT * FROM network_flow_records WHERE lastSeen BETWEEN :startTime AND :endTime ORDER BY totalBytes DESC")
    suspend fun getFlowsInTimeRangeList(startTime: Long, endTime: Long): List<NetworkFlowEntity>

    @Transaction
    suspend fun upsertTraffic(
        flowKey: String,
        domain: String,
        uid: Int,
        packageName: String,
        appName: String,
        destinationIp: String,
        protocol: String,
        uploadDelta: Long,
        downloadDelta: Long,
        networkType: String,
        confidence: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val existing = getFlowByKey(flowKey)
        if (existing != null) {
            val updated = existing.copy(
                uploadBytes = existing.uploadBytes + uploadDelta,
                downloadBytes = existing.downloadBytes + downloadDelta,
                totalBytes = existing.totalBytes + uploadDelta + downloadDelta,
                connectionCount = existing.connectionCount + 1,
                lastSeen = timestamp,
                destinationIp = if (destinationIp.isNotEmpty()) destinationIp else existing.destinationIp
            )
            insertOrUpdate(updated)
        } else {
            val total = uploadDelta + downloadDelta
            val newRecord = NetworkFlowEntity(
                flowKey = flowKey,
                domain = domain,
                uid = uid,
                packageName = packageName,
                appName = appName,
                destinationIp = destinationIp,
                protocol = protocol,
                uploadBytes = uploadDelta,
                downloadBytes = downloadDelta,
                totalBytes = total,
                connectionCount = 1,
                networkType = networkType,
                firstSeen = timestamp,
                lastSeen = timestamp,
                confidence = confidence
            )
            insertOrUpdate(newRecord)
        }
    }

    @Query("DELETE FROM network_flow_records")
    suspend fun clearAll()
}
