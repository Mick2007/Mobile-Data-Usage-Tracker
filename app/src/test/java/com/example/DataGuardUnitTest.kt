package com.example

import com.example.dataguard.data.entity.AppDataUsage
import com.example.dataguard.utils.DataFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataGuardUnitTest {

    @Test
    fun testDataFormatter_bytesConversion() {
        assertEquals("500 B", DataFormatter.formatBytes(500))
        assertEquals("1.0 KB", DataFormatter.formatBytes(1024))
        assertEquals("10.00 MB", DataFormatter.formatBytes(10L * 1024 * 1024))
        assertEquals("2.50 GB", DataFormatter.formatBytes((2.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun testDataFormatter_speedFormatting() {
        assertEquals("0 B/s", DataFormatter.formatSpeed(0))
        assertEquals("256.0 KB/s", DataFormatter.formatSpeed(256 * 1024))
        assertEquals("1.50 MB/s", DataFormatter.formatSpeed((1.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun testAppDataUsage_serializationAndDeserialization() {
        val app = AppDataUsage(
            uid = 10042,
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            txBytes = 1500000L,
            rxBytes = 45000000L,
            totalBytes = 46500000L
        )

        val serialized = app.serialize()
        val deserialized = AppDataUsage.deserialize(serialized)

        assertNotNull(deserialized)
        assertEquals(app.uid, deserialized!!.uid)
        assertEquals(app.packageName, deserialized.packageName)
        assertEquals(app.appName, deserialized.appName)
        assertEquals(app.totalBytes, deserialized.totalBytes)
    }

    @Test
    fun testAppDataUsageList_serialization() {
        val apps = listOf(
            AppDataUsage(1001, "com.app.one", "One", 100, 200, 300),
            AppDataUsage(1002, "com.app.two", "Two", 400, 500, 900)
        )

        val serializedList = AppDataUsage.serializeList(apps)
        val deserializedList = AppDataUsage.deserializeList(serializedList)

        assertEquals(2, deserializedList.size)
        assertEquals("One", deserializedList[0].appName)
        assertEquals("Two", deserializedList[1].appName)
    }

    @Test
    fun testDataFormatter_timestamps() {
        val now = System.currentTimeMillis()
        val startOfDay = DataFormatter.getStartOfDay(now)
        val endOfDay = DataFormatter.getEndOfDay(now)

        assertTrue(startOfDay <= now)
        assertTrue(endOfDay >= now)
        assertTrue(endOfDay - startOfDay <= 24 * 60 * 60 * 1000L)
    }
}
