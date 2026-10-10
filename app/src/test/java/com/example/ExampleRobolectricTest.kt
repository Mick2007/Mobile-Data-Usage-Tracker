package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.dataguard.data.database.DataGuardDatabase
import com.example.dataguard.data.entity.AppLimitEntity
import com.example.dataguard.data.entity.QuotaBreachEventEntity
import com.example.dataguard.data.entity.QuotaCycleType
import com.example.dataguard.data.entity.QuotaEnforcementMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var db: DataGuardDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DataGuardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("DataGuard", appName)
    }

    @Test
    fun `test app limit insertion and retrieval in database`() = runBlocking {
        val limit = AppLimitEntity(
            uid = 10042,
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            limitBytes = 2L * 1024 * 1024 * 1024,
            warningThresholdPercent = 80,
            enforcementMode = QuotaEnforcementMode.BLOCK_AT_LIMIT.name,
            cycleType = QuotaCycleType.MONTHLY.name,
            cycleStartDay = 1,
            isEnabled = true
        )

        db.appLimitDao().insertAppLimit(limit)
        val fetched = db.appLimitDao().getAppLimit(10042)

        assertNotNull(fetched)
        assertEquals("YouTube", fetched!!.appName)
        assertEquals("com.google.android.youtube", fetched.packageName)
        assertEquals(2L * 1024 * 1024 * 1024, fetched.limitBytes)
        assertEquals(80, fetched.warningThresholdPercent)
        assertEquals("BLOCK_AT_LIMIT", fetched.enforcementMode)
        assertTrue(fetched.isEnabled)
    }

    @Test
    fun `test quota breach event recording and retrieval`() = runBlocking {
        val event = QuotaBreachEventEntity(
            uid = 10042,
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            eventType = "LIMIT_REACHED",
            usedBytes = 2100000000L,
            limitBytes = 2000000000L,
            enforcementAction = "BLOCKED"
        )

        db.quotaBreachEventDao().insertEvent(event)
        val events = db.quotaBreachEventDao().getEventsForApp(10042).first()

        assertEquals(1, events.size)
        assertEquals("LIMIT_REACHED", events[0].eventType)
        assertEquals("BLOCKED", events[0].enforcementAction)
        assertEquals(2100000000L, events[0].usedBytes)
    }
}
