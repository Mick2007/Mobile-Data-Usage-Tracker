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

    @Test
    fun `test data plan and notification preferences database persistence`() = runBlocking {
        val plan = com.example.dataguard.data.entity.DataPlanEntity(
            id = 1L,
            planName = "MTN Monthly Plan",
            totalAllowanceBytes = 10L * 1024L * 1024L * 1024L,
            rolloverBytes = 1L * 1024L * 1024L * 1024L,
            billingCycle = com.example.dataguard.data.entity.BillingCycleType.MONTHLY.name,
            trafficScope = com.example.dataguard.data.entity.PlanTrafficScope.MOBILE_ONLY.name,
            warningThresholdsJson = "50,75,90,100"
        )
        db.dataPlanDao().insertOrUpdatePlan(plan)
        val fetchedPlan = db.dataPlanDao().getPlan()
        assertNotNull(fetchedPlan)
        assertEquals("MTN Monthly Plan", fetchedPlan!!.planName)
        assertEquals(11L * 1024L * 1024L * 1024L, fetchedPlan.totalEffectiveAllowanceBytes)

        val prefs = com.example.dataguard.data.entity.NotificationPreferencesEntity(
            id = 1L,
            alertOn50Percent = true,
            alertOn75Percent = true,
            alertOn90Percent = true,
            alertOn100Percent = true,
            appExceedsThresholdMb = 300L
        )
        db.notificationPreferencesDao().insertOrUpdatePreferences(prefs)
        val fetchedPrefs = db.notificationPreferencesDao().getPreferences()
        assertNotNull(fetchedPrefs)
        assertEquals(300L, fetchedPrefs!!.appExceedsThresholdMb)

        val historyItem = com.example.dataguard.data.entity.NotificationHistoryEntity(
            title = "Threshold 75% Breached",
            message = "Consumed 7.5 GB of 10 GB",
            category = com.example.dataguard.data.entity.NotificationAlertCategory.PLAN_THRESHOLD.name,
            thresholdPercent = 75
        )
        db.notificationHistoryDao().insertHistory(historyItem)
        val historyList = db.notificationHistoryDao().getAllHistoryFlow().first()
        assertEquals(1, historyList.size)
        assertEquals(75, historyList[0].thresholdPercent)
    }
}

