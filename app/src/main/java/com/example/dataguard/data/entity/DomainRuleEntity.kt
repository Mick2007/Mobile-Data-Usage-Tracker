package com.example.dataguard.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.dataguard.vpn.engine.DomainNormalizer

@Entity(
    tableName = "domain_rules",
    indices = [
        Index(value = ["normalizedDomain"]),
        Index(value = ["packageName"]),
        Index(value = ["ruleType"])
    ]
)
data class DomainRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val domainPattern: String, // e.g. "youtube.com", "*.facebook.com"
    val normalizedDomain: String = DomainNormalizer.normalize(domainPattern),
    val ruleType: String, // "GLOBAL_BLOCK", "GLOBAL_ALLOW", "APP_BLOCK", "APP_ALLOW"
    val packageName: String? = null, // for APP_BLOCK / APP_ALLOW
    val appName: String? = null,
    val uid: Int? = null,
    val networkScope: String = "ANY", // "ANY", "MOBILE", "WIFI"
    val createdAt: Long = System.currentTimeMillis(),
    val isEnabled: Boolean = true,
    val notes: String = ""
)
