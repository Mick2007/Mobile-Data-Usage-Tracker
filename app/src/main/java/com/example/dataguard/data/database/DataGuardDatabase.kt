package com.example.dataguard.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.dataguard.data.dao.AppLimitDao
import com.example.dataguard.data.dao.BlockedAppDao
import com.example.dataguard.data.dao.DataToggleHistoryDao
import com.example.dataguard.data.dao.UsageDao
import com.example.dataguard.data.entity.AppLimitEntity
import com.example.dataguard.data.entity.BlockedAppEntity
import com.example.dataguard.data.entity.DataToggleHistoryEntity
import com.example.dataguard.data.entity.UsageEntity

@Database(
    entities = [
        UsageEntity::class,
        BlockedAppEntity::class,
        AppLimitEntity::class,
        DataToggleHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DataGuardDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun appLimitDao(): AppLimitDao
    abstract fun dataToggleHistoryDao(): DataToggleHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: DataGuardDatabase? = null

        fun getInstance(context: Context): DataGuardDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DataGuardDatabase::class.java,
                    "dataguard_db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
