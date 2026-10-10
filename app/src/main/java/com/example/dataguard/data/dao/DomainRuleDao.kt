package com.example.dataguard.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.dataguard.data.entity.DomainRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(rule: DomainRuleEntity): Long

    @Update
    suspend fun update(rule: DomainRuleEntity)

    @Delete
    suspend fun delete(rule: DomainRuleEntity)

    @Query("DELETE FROM domain_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM domain_rules WHERE normalizedDomain = :normalizedDomain AND packageName IS NULL")
    suspend fun deleteGlobalDomainRule(normalizedDomain: String)

    @Query("DELETE FROM domain_rules WHERE normalizedDomain = :normalizedDomain AND packageName = :packageName")
    suspend fun deleteAppDomainRule(normalizedDomain: String, packageName: String)

    @Query("SELECT * FROM domain_rules ORDER BY createdAt DESC")
    fun getAllRulesFlow(): Flow<List<DomainRuleEntity>>

    @Query("SELECT * FROM domain_rules ORDER BY createdAt DESC")
    suspend fun getAllRules(): List<DomainRuleEntity>

    @Query("SELECT * FROM domain_rules WHERE isEnabled = 1")
    fun getActiveRulesFlow(): Flow<List<DomainRuleEntity>>

    @Query("SELECT * FROM domain_rules WHERE isEnabled = 1")
    suspend fun getActiveRules(): List<DomainRuleEntity>

    @Query("SELECT * FROM domain_rules WHERE normalizedDomain = :normalizedDomain")
    suspend fun getRulesForDomain(normalizedDomain: String): List<DomainRuleEntity>

    @Query("SELECT * FROM domain_rules WHERE normalizedDomain = :normalizedDomain")
    fun getRulesForDomainFlow(normalizedDomain: String): Flow<List<DomainRuleEntity>>

    @Query("DELETE FROM domain_rules")
    suspend fun clearAll()
}
