package com.example.dataguard.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.dataguard.data.dao.AppLimitDao
import com.example.dataguard.data.dao.AppNetworkUsageDao
import com.example.dataguard.data.dao.BlockedAppDao
import com.example.dataguard.data.dao.BlockedTrafficEventDao
import com.example.dataguard.data.dao.DataToggleHistoryDao
import com.example.dataguard.data.dao.DomainRuleDao
import com.example.dataguard.data.dao.NetworkFlowDao
import com.example.dataguard.data.dao.QuotaBreachEventDao
import com.example.dataguard.data.dao.UsageDao
import com.example.dataguard.data.entity.AppLimitEntity
import com.example.dataguard.data.entity.AppNetworkUsageRecordEntity
import com.example.dataguard.data.entity.BlockedAppEntity
import com.example.dataguard.data.entity.BlockedTrafficEventEntity
import com.example.dataguard.data.entity.DataToggleHistoryEntity
import com.example.dataguard.data.entity.DomainRuleEntity
import com.example.dataguard.data.entity.NetworkFlowEntity
import com.example.dataguard.data.entity.QuotaBreachEventEntity
import com.example.dataguard.data.entity.UsageEntity

@Database(
    entities = [
        UsageEntity::class,
        AppNetworkUsageRecordEntity::class,
        BlockedAppEntity::class,
        BlockedTrafficEventEntity::class,
        AppLimitEntity::class,
        DataToggleHistoryEntity::class,
        QuotaBreachEventEntity::class,
        NetworkFlowEntity::class,
        DomainRuleEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class DataGuardDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun appNetworkUsageDao(): AppNetworkUsageDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun blockedTrafficEventDao(): BlockedTrafficEventDao
    abstract fun appLimitDao(): AppLimitDao
    abstract fun dataToggleHistoryDao(): DataToggleHistoryDao
    abstract fun quotaBreachEventDao(): QuotaBreachEventDao
    abstract fun networkFlowDao(): NetworkFlowDao
    abstract fun domainRuleDao(): DomainRuleDao

    companion object {
        @Volatile
        private var INSTANCE: DataGuardDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add Wi-Fi columns to existing usage_stats table preserving all user records
                db.execSQL("ALTER TABLE usage_stats ADD COLUMN totalWifiTx INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE usage_stats ADD COLUMN totalWifiRx INTEGER NOT NULL DEFAULT 0")

                // Create normalized per-app network usage table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_network_usage_records (
                        uid INTEGER NOT NULL,
                        packageName TEXT NOT NULL,
                        appName TEXT NOT NULL,
                        dateEpochDay INTEGER NOT NULL,
                        mobileTxBytes INTEGER NOT NULL,
                        mobileRxBytes INTEGER NOT NULL,
                        wifiTxBytes INTEGER NOT NULL,
                        wifiRxBytes INTEGER NOT NULL,
                        lastUpdated INTEGER NOT NULL,
                        PRIMARY KEY(uid, dateEpochDay)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add extended quota & policy columns to existing app_limits table
                db.execSQL("ALTER TABLE app_limits ADD COLUMN appName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_limits ADD COLUMN limitBytes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_limits ADD COLUMN warningThresholdPercent INTEGER NOT NULL DEFAULT 80")
                db.execSQL("ALTER TABLE app_limits ADD COLUMN enforcementMode TEXT NOT NULL DEFAULT 'WARN_ONLY'")
                db.execSQL("ALTER TABLE app_limits ADD COLUMN cycleType TEXT NOT NULL DEFAULT 'MONTHLY'")
                db.execSQL("ALTER TABLE app_limits ADD COLUMN cycleStartDay INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_limits ADD COLUMN cycleStartTimestamp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_limits ADD COLUMN customCycleDays INTEGER NOT NULL DEFAULT 30")
                db.execSQL("ALTER TABLE app_limits ADD COLUMN isEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_limits ADD COLUMN lastWarningNotifiedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_limits ADD COLUMN lastLimitNotifiedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_limits ADD COLUMN isLimitReached INTEGER NOT NULL DEFAULT 0")

                // Populate limitBytes from legacy MB columns if present
                db.execSQL(
                    """
                    UPDATE app_limits SET limitBytes = CASE
                        WHEN monthlyLimitMB > 0 THEN monthlyLimitMB * 1048576
                        WHEN weeklyLimitMB > 0 THEN weeklyLimitMB * 1048576
                        WHEN dailyLimitMB > 0 THEN dailyLimitMB * 1048576
                        ELSE 0
                    END WHERE limitBytes = 0
                    """.trimIndent()
                )

                // Create table for quota warning and breach records
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS quota_breach_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        uid INTEGER NOT NULL,
                        packageName TEXT NOT NULL,
                        appName TEXT NOT NULL,
                        eventType TEXT NOT NULL,
                        usedBytes INTEGER NOT NULL,
                        limitBytes INTEGER NOT NULL,
                        enforcementAction TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Upgrade blocked_apps with firewall reason and per-interface toggle flags
                db.execSQL("ALTER TABLE blocked_apps ADD COLUMN reason TEXT NOT NULL DEFAULT 'User firewall rule'")
                db.execSQL("ALTER TABLE blocked_apps ADD COLUMN blockMobile INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE blocked_apps ADD COLUMN blockWifi INTEGER NOT NULL DEFAULT 1")

                // Create table for logging blocked traffic events
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS blocked_traffic_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        uid INTEGER NOT NULL,
                        packageName TEXT NOT NULL,
                        appName TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        networkInterface TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS network_flow_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        flowKey TEXT NOT NULL,
                        domain TEXT NOT NULL,
                        uid INTEGER NOT NULL,
                        packageName TEXT NOT NULL,
                        appName TEXT NOT NULL,
                        destinationIp TEXT NOT NULL,
                        protocol TEXT NOT NULL,
                        uploadBytes INTEGER NOT NULL,
                        downloadBytes INTEGER NOT NULL,
                        totalBytes INTEGER NOT NULL,
                        connectionCount INTEGER NOT NULL,
                        networkType TEXT NOT NULL,
                        firstSeen INTEGER NOT NULL,
                        lastSeen INTEGER NOT NULL,
                        confidence TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_network_flow_records_domain ON network_flow_records(domain)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_network_flow_records_packageName ON network_flow_records(packageName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_network_flow_records_uid ON network_flow_records(uid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_network_flow_records_lastSeen ON network_flow_records(lastSeen)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_network_flow_records_flowKey ON network_flow_records(flowKey)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS domain_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        domainPattern TEXT NOT NULL,
                        normalizedDomain TEXT NOT NULL,
                        ruleType TEXT NOT NULL,
                        packageName TEXT,
                        appName TEXT,
                        uid INTEGER,
                        networkScope TEXT NOT NULL DEFAULT 'ANY',
                        createdAt INTEGER NOT NULL,
                        isEnabled INTEGER NOT NULL DEFAULT 1,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_domain_rules_normalizedDomain ON domain_rules(normalizedDomain)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_domain_rules_packageName ON domain_rules(packageName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_domain_rules_ruleType ON domain_rules(ruleType)")
            }
        }

        fun getInstance(context: Context): DataGuardDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DataGuardDatabase::class.java,
                    "dataguard_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
