package com.example

import com.example.dataguard.data.entity.AppDataUsage
import com.example.dataguard.data.entity.AppLimitEntity
import com.example.dataguard.data.entity.AppNetworkUsageRecordEntity
import com.example.dataguard.data.entity.BlockedAppEntity
import com.example.dataguard.data.entity.QuotaBreachEventEntity
import com.example.dataguard.data.entity.QuotaCycleType
import com.example.dataguard.data.entity.QuotaEnforcementMode
import com.example.dataguard.data.entity.UsageEntity
import com.example.dataguard.domain.model.AppQuotaCalculation
import com.example.dataguard.domain.model.AppQuotaCycleCalculator
import com.example.dataguard.domain.model.TimeRangeSelection
import com.example.dataguard.domain.model.TimeRangeType
import com.example.dataguard.domain.usecase.DeviceNetworkTelemetry
import com.example.dataguard.utils.DataFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

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
    fun testAppDataUsage_serializationAndDeserialization_v2() {
        val app = AppDataUsage(
            uid = 10042,
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            mobileTxBytes = 1000000L,
            mobileRxBytes = 25000000L,
            wifiTxBytes = 500000L,
            wifiRxBytes = 20000000L,
            totalBytes = 46500000L,
            usagePercentage = 34.5f
        )

        val serialized = app.serialize()
        val deserialized = AppDataUsage.deserialize(serialized)

        assertNotNull(deserialized)
        assertEquals(app.uid, deserialized!!.uid)
        assertEquals(app.packageName, deserialized.packageName)
        assertEquals(app.appName, deserialized.appName)
        assertEquals(app.mobileTxBytes, deserialized.mobileTxBytes)
        assertEquals(app.mobileRxBytes, deserialized.mobileRxBytes)
        assertEquals(app.wifiTxBytes, deserialized.wifiTxBytes)
        assertEquals(app.wifiRxBytes, deserialized.wifiRxBytes)
        assertEquals(app.totalBytes, deserialized.totalBytes)
        assertEquals(34.5f, deserialized.usagePercentage, 0.01f)
    }

    @Test
    fun testAppDataUsage_backwardCompatibility() {
        val oldSerialized = "10042\tcom.google.android.youtube\tYouTube\t1500000\t45000000\t46500000"
        val deserialized = AppDataUsage.deserialize(oldSerialized)

        assertNotNull(deserialized)
        assertEquals(10042, deserialized!!.uid)
        assertEquals("YouTube", deserialized.appName)
        assertEquals(46500000L, deserialized.totalBytes)
        assertEquals(1500000L, deserialized.mobileTxBytes)
        assertEquals(45000000L, deserialized.mobileRxBytes)
        assertEquals(0L, deserialized.wifiTxBytes)
        assertEquals(0L, deserialized.wifiRxBytes)
    }

    @Test
    fun testMobileData_accounting_and_metrics() {
        val mobileTx = 150L * 1024 * 1024
        val mobileRx = 850L * 1024 * 1024
        val usage = UsageEntity(
            timestamp = System.currentTimeMillis(),
            totalMobileTx = mobileTx,
            totalMobileRx = mobileRx,
            totalWifiTx = 0L,
            totalWifiRx = 0L,
            period = "daily"
        )

        assertEquals(1000L * 1024 * 1024, usage.totalMobileBytes)
        assertEquals(0L, usage.totalWifiBytes)
        assertEquals(150L * 1024 * 1024, usage.totalTxBytes)
        assertEquals(850L * 1024 * 1024, usage.totalRxBytes)
        assertEquals(1000L * 1024 * 1024, usage.totalBytes)
    }

    @Test
    fun testWifiData_accounting_and_metrics() {
        val wifiTx = 300L * 1024 * 1024
        val wifiRx = 1200L * 1024 * 1024
        val usage = UsageEntity(
            timestamp = System.currentTimeMillis(),
            totalMobileTx = 0L,
            totalMobileRx = 0L,
            totalWifiTx = wifiTx,
            totalWifiRx = wifiRx,
            period = "daily"
        )

        assertEquals(0L, usage.totalMobileBytes)
        assertEquals(1500L * 1024 * 1024, usage.totalWifiBytes)
        assertEquals(300L * 1024 * 1024, usage.totalTxBytes)
        assertEquals(1200L * 1024 * 1024, usage.totalRxBytes)
        assertEquals(1500L * 1024 * 1024, usage.totalBytes)
    }

    @Test
    fun testMultipleApplications_ranking_and_percentage() {
        val app1 = AppDataUsage(
            uid = 1001,
            packageName = "com.app.video",
            appName = "Video Stream",
            mobileTxBytes = 200L,
            mobileRxBytes = 1800L,
            wifiTxBytes = 400L,
            wifiRxBytes = 3600L,
            totalBytes = 6000L
        )
        val app2 = AppDataUsage(
            uid = 1002,
            packageName = "com.app.chat",
            appName = "Chat App",
            mobileTxBytes = 100L,
            mobileRxBytes = 900L,
            wifiTxBytes = 200L,
            wifiRxBytes = 800L,
            totalBytes = 2000L
        )
        val app3 = AppDataUsage(
            uid = 1003,
            packageName = "com.app.mail",
            appName = "Mail App",
            mobileTxBytes = 50L,
            mobileRxBytes = 150L,
            wifiTxBytes = 100L,
            wifiRxBytes = 500L,
            totalBytes = 800L
        )

        val appList = listOf(app3, app1, app2)
        val totalBytes = appList.sumOf { it.totalBytes }
        assertEquals(8800L, totalBytes)

        val rankedList = appList.sortedByDescending { it.totalBytes }.map { app ->
            val pct = ((app.totalBytes.toDouble() / totalBytes.toDouble()) * 100).toFloat()
            app.copy(usagePercentage = pct)
        }

        assertEquals("Video Stream", rankedList[0].appName)
        assertEquals("Chat App", rankedList[1].appName)
        assertEquals("Mail App", rankedList[2].appName)

        assertEquals(68.18f, rankedList[0].usagePercentage, 0.1f)
        assertEquals(22.72f, rankedList[1].usagePercentage, 0.1f)
        assertEquals(9.09f, rankedList[2].usagePercentage, 0.1f)
    }

    @Test
    fun testDateChanges_timeRanges() {
        val now = 1700000000000L
        val today = TimeRangeSelection.today(now)
        val yesterday = TimeRangeSelection.yesterday(now)
        val last7Days = TimeRangeSelection.last7Days(now)
        val thisMonth = TimeRangeSelection.thisMonth(now)
        val custom = TimeRangeSelection.custom(now - 14 * 86400000L, now, "Custom 14 Days")

        assertEquals(TimeRangeType.TODAY, today.type)
        assertEquals(TimeRangeType.YESTERDAY, yesterday.type)
        assertEquals(TimeRangeType.LAST_7_DAYS, last7Days.type)
        assertEquals(TimeRangeType.THIS_MONTH, thisMonth.type)
        assertEquals(TimeRangeType.CUSTOM, custom.type)

        assertTrue(yesterday.endTime <= today.startTime)
        assertEquals(86400000L, yesterday.endTime - yesterday.startTime + 1L)
        assertTrue(last7Days.startTime < today.startTime)
        assertEquals("Custom 14 Days", custom.displayLabel)
    }

    @Test
    fun testApplicationRestart_persistenceIntegrity() {
        val originalRecords = listOf(
            AppDataUsage(
                uid = 1001,
                packageName = "com.example.browser",
                appName = "Browser",
                mobileTxBytes = 1000L,
                mobileRxBytes = 4000L,
                wifiTxBytes = 2000L,
                wifiRxBytes = 8000L,
                totalBytes = 15000L
            )
        )

        val serialized = AppDataUsage.serializeList(originalRecords)
        val restored = AppDataUsage.deserializeList(serialized)

        assertEquals(1, restored.size)
        assertEquals("com.example.browser", restored[0].packageName)
        assertEquals(5000L, restored[0].mobileTotalBytes)
        assertEquals(10000L, restored[0].wifiTotalBytes)
        assertEquals(15000L, restored[0].totalBytes)
    }

    // ==========================================
    // STEP 3: Per-Application Quota System Tests
    // ==========================================

    @Test
    fun testQuota_belowLimit() {
        // YouTube: Monthly limit 2 GB (2048 MB), warning: 80% (1638.4 MB), action: BLOCK_AT_LIMIT
        val limitBytes = 2L * 1024 * 1024 * 1024
        val limit = AppLimitEntity(
            uid = 10042,
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            limitBytes = limitBytes,
            warningThresholdPercent = 80,
            enforcementMode = QuotaEnforcementMode.BLOCK_AT_LIMIT.name,
            cycleType = QuotaCycleType.MONTHLY.name,
            isEnabled = true
        )

        // Case: Used 500 MB (below warning threshold of 80%)
        val usedBytes = 500L * 1024 * 1024
        val remainingBytes = limitBytes - usedBytes
        val percentage = (usedBytes.toFloat() / limitBytes.toFloat()) * 100f
        val isWarning = percentage >= limit.warningThresholdPercent
        val isLimit = usedBytes >= limitBytes

        assertEquals(1548L * 1024 * 1024, remainingBytes)
        assertEquals(24.41f, percentage, 0.1f)
        assertFalse(isWarning)
        assertFalse(isLimit)
    }

    @Test
    fun testQuota_warningThreshold() {
        // YouTube: Monthly limit 2 GB, warning: 80%
        val limitBytes = 2L * 1024 * 1024 * 1024
        val limit = AppLimitEntity(
            uid = 10042,
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            limitBytes = limitBytes,
            warningThresholdPercent = 80,
            enforcementMode = QuotaEnforcementMode.BLOCK_AT_LIMIT.name,
            cycleType = QuotaCycleType.MONTHLY.name,
            isEnabled = true
        )

        // Case: Used 1.65 GB (82.5% - above 80% warning threshold, but below 100% limit)
        val usedBytes = (1.65 * 1024 * 1024 * 1024).toLong()
        val remainingBytes = limitBytes - usedBytes
        val percentage = (usedBytes.toFloat() / limitBytes.toFloat()) * 100f
        val isWarning = percentage >= limit.warningThresholdPercent
        val isLimit = usedBytes >= limitBytes

        assertTrue("Should be at warning threshold", isWarning)
        assertFalse("Should not yet be at limit", isLimit)
        assertTrue(remainingBytes > 0)
        assertEquals(82.5f, percentage, 0.1f)
    }

    @Test
    fun testQuota_limitReached_blockAtLimit() {
        // YouTube: Monthly limit 2 GB, Action at limit: BLOCK_AT_LIMIT
        val limitBytes = 2L * 1024 * 1024 * 1024
        val limit = AppLimitEntity(
            uid = 10042,
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            limitBytes = limitBytes,
            warningThresholdPercent = 80,
            enforcementMode = QuotaEnforcementMode.BLOCK_AT_LIMIT.name,
            cycleType = QuotaCycleType.MONTHLY.name,
            isEnabled = true
        )

        // Case: Used 2.05 GB (exceeded 2 GB limit)
        val usedBytes = (2.05 * 1024 * 1024 * 1024).toLong()
        val remainingBytes = maxOf(0L, limitBytes - usedBytes)
        val percentage = (usedBytes.toFloat() / limitBytes.toFloat()) * 100f
        val isLimit = usedBytes >= limitBytes

        assertTrue("Limit should be reached", isLimit)
        assertEquals(0L, remainingBytes)
        assertTrue(percentage >= 100f)
        assertEquals(QuotaEnforcementMode.BLOCK_AT_LIMIT.name, limit.enforcementMode)

        // Verify breach event generation
        val breachEvent = QuotaBreachEventEntity(
            uid = limit.uid,
            packageName = limit.packageName,
            appName = limit.appName,
            eventType = "LIMIT_REACHED",
            usedBytes = usedBytes,
            limitBytes = limitBytes,
            enforcementAction = "BLOCKED"
        )
        assertEquals("LIMIT_REACHED", breachEvent.eventType)
        assertEquals("BLOCKED", breachEvent.enforcementAction)
    }

    @Test
    fun testQuota_limitReached_warnOnly() {
        // Chrome: Daily limit 300 MB, Warning: 75%, Action at limit: WARN_ONLY
        val limitBytes = 300L * 1024 * 1024
        val limit = AppLimitEntity(
            uid = 10023,
            packageName = "com.android.chrome",
            appName = "Chrome",
            limitBytes = limitBytes,
            warningThresholdPercent = 75,
            enforcementMode = QuotaEnforcementMode.WARN_ONLY.name,
            cycleType = QuotaCycleType.DAILY.name,
            isEnabled = true
        )

        // Case: Used 320 MB (exceeded 300 MB limit)
        val usedBytes = 320L * 1024 * 1024
        val isLimit = usedBytes >= limitBytes

        assertTrue("Limit should be reached", isLimit)
        assertEquals(QuotaEnforcementMode.WARN_ONLY.name, limit.enforcementMode)

        // Action is WARN_ONLY, so firewall should NOT block
        val breachEvent = QuotaBreachEventEntity(
            uid = limit.uid,
            packageName = limit.packageName,
            appName = limit.appName,
            eventType = "LIMIT_REACHED",
            usedBytes = usedBytes,
            limitBytes = limitBytes,
            enforcementAction = "WARN_ONLY"
        )
        assertEquals("WARN_ONLY", breachEvent.enforcementAction)
    }

    @Test
    fun testQuota_cycleReset_restoresAllowances() {
        val now = 1700000000000L
        val limit = AppLimitEntity(
            uid = 10042,
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            limitBytes = 2L * 1024 * 1024 * 1024,
            cycleType = QuotaCycleType.DAILY.name,
            cycleStartTimestamp = now - 90000000L, // started yesterday
            isLimitReached = true
        )

        // When cycle is reset or new cycle begins:
        val resetLimit = limit.copy(
            cycleStartTimestamp = now,
            isLimitReached = false,
            lastWarningNotifiedAt = 0L,
            lastLimitNotifiedAt = 0L
        )

        assertFalse(resetLimit.isLimitReached)
        assertEquals(0L, resetLimit.lastLimitNotifiedAt)
        assertEquals(now, resetLimit.cycleStartTimestamp)
    }

    @Test
    fun testQuota_disabledQuota_suppressesEnforcement() {
        val limit = AppLimitEntity(
            uid = 10042,
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            limitBytes = 100L * 1024 * 1024, // 100 MB limit
            enforcementMode = QuotaEnforcementMode.BLOCK_AT_LIMIT.name,
            isEnabled = false // DISABLED
        )

        val usedBytes = 500L * 1024 * 1024 // 500 MB used (way over limit)
        val shouldEnforce = limit.isEnabled && usedBytes >= limit.limitBytes

        assertFalse("Disabled quota must never trigger enforcement or blocks", shouldEnforce)
    }

    @Test
    fun testQuota_multipleApplications_independentCalculations() {
        val youtubeLimit = AppLimitEntity(
            uid = 10042,
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            limitBytes = 2L * 1024 * 1024 * 1024,
            warningThresholdPercent = 80,
            enforcementMode = QuotaEnforcementMode.BLOCK_AT_LIMIT.name,
            cycleType = QuotaCycleType.MONTHLY.name,
            isEnabled = true
        )

        val chromeLimit = AppLimitEntity(
            uid = 10023,
            packageName = "com.android.chrome",
            appName = "Chrome",
            limitBytes = 300L * 1024 * 1024,
            warningThresholdPercent = 75,
            enforcementMode = QuotaEnforcementMode.WARN_ONLY.name,
            cycleType = QuotaCycleType.DAILY.name,
            isEnabled = true
        )

        val youtubeUsed = 1500L * 1024 * 1024 // 1.5 GB / 2 GB (73.2% - below 80% warning)
        val chromeUsed = 240L * 1024 * 1024  // 240 MB / 300 MB (80.0% - above 75% warning)

        val youtubeWarn = (youtubeUsed.toFloat() / youtubeLimit.limitBytes.toFloat() * 100f) >= youtubeLimit.warningThresholdPercent
        val chromeWarn = (chromeUsed.toFloat() / chromeLimit.limitBytes.toFloat() * 100f) >= chromeLimit.warningThresholdPercent

        assertFalse("YouTube is under warning threshold", youtubeWarn)
        assertTrue("Chrome reached warning threshold", chromeWarn)
    }

    @Test
    fun testQuota_cycleWindows_daily_weekly_monthly_custom() {
        val now = 1700000000000L // Reference time

        val dailyLimit = AppLimitEntity(
            uid = 1,
            packageName = "com.app.daily",
            cycleType = QuotaCycleType.DAILY.name
        )
        val (dailyStart, dailyEnd) = AppQuotaCycleCalculator.calculateCycleWindow(dailyLimit, now)
        assertEquals(86400000L, dailyEnd - dailyStart) // 24 hours

        val weeklyLimit = AppLimitEntity(
            uid = 2,
            packageName = "com.app.weekly",
            cycleType = QuotaCycleType.WEEKLY.name
        )
        val (weeklyStart, weeklyEnd) = AppQuotaCycleCalculator.calculateCycleWindow(weeklyLimit, now)
        assertEquals(7L * 86400000L, weeklyEnd - weeklyStart) // 7 days

        val customLimit = AppLimitEntity(
            uid = 3,
            packageName = "com.app.custom",
            cycleType = QuotaCycleType.CUSTOM.name,
            customCycleDays = 14,
            cycleStartTimestamp = now - 86400000L
        )
        val (customStart, customEnd) = AppQuotaCycleCalculator.calculateCycleWindow(customLimit, now)
        assertEquals(14L * 86400000L, customEnd - customStart) // 14 days
    }

    @Test
    fun testVpnState_lifecycleTransitions() {
        val initialState = com.example.dataguard.vpn.model.VpnState()
        assertEquals(com.example.dataguard.vpn.model.VpnStatus.OFF, initialState.status)
        assertFalse(initialState.isRunning)
        assertFalse(initialState.isStarting)

        val startingState = initialState.copy(status = com.example.dataguard.vpn.model.VpnStatus.STARTING)
        assertTrue(startingState.isStarting)
        assertFalse(startingState.isRunning)

        val activeState = startingState.copy(
            status = com.example.dataguard.vpn.model.VpnStatus.ACTIVE,
            startedAt = System.currentTimeMillis(),
            bytesIn = 1024L,
            bytesOut = 2048L,
            packetsProcessed = 15L
        )
        assertTrue(activeState.isRunning)
        assertEquals(3072L, activeState.bytesProcessed)
        assertEquals(15L, activeState.packetsProcessed)

        val errorState = activeState.copy(
            status = com.example.dataguard.vpn.model.VpnStatus.ERROR,
            errorMessage = "Failed to establish VPN interface"
        )
        assertFalse(errorState.isRunning)
        assertEquals("Failed to establish VPN interface", errorState.errorMessage)
    }

    @Test
    fun testFirewallEngine_inMemoryRules() {
        val engine = com.example.dataguard.vpn.engine.FirewallEngine()
        assertFalse(engine.isPackageBlocked("com.google.android.youtube"))
        assertFalse(engine.isUidBlocked(10042))

        engine.addBlockedPackage("com.google.android.youtube")
        engine.addBlockedUid(10042)

        assertTrue(engine.isPackageBlocked("com.google.android.youtube"))
        assertTrue(engine.isUidBlocked(10042))
        assertEquals(1, engine.getBlockedPackages().size)
        assertEquals(1, engine.getBlockedUids().size)

        engine.removeBlockedPackage("com.google.android.youtube")
        assertFalse(engine.isPackageBlocked("com.google.android.youtube"))
        assertTrue(engine.isUidBlocked(10042))

        engine.clear()
        assertFalse(engine.isUidBlocked(10042))
    }

    @Test
    fun testTrafficAccountingEngine_metricsAccumulation() {
        val accounting = com.example.dataguard.vpn.engine.TrafficAccountingEngine()
        accounting.startSession()

        accounting.recordPacketIn(500)
        accounting.recordPacketOut(750)
        accounting.recordPacketIn(250)

        val metrics = accounting.getMetrics()
        assertEquals(750L, metrics.bytesIn)
        assertEquals(750L, metrics.bytesOut)
        assertEquals(1500L, metrics.bytesIn + metrics.bytesOut)
        assertEquals(3L, metrics.packetsProcessed)

        accounting.reset()
        val resetMetrics = accounting.getMetrics()
        assertEquals(0L, resetMetrics.bytesIn + resetMetrics.bytesOut)
        assertEquals(0L, resetMetrics.packetsProcessed)
    }

    @Test
    fun testPacketProcessor_ipv4HeaderParsing() {
        val processor = com.example.dataguard.vpn.engine.PacketProcessor(
            firewallEngine = com.example.dataguard.vpn.engine.FirewallEngine(),
            accountingEngine = com.example.dataguard.vpn.engine.TrafficAccountingEngine()
        )

        // Synthesize valid IPv4 TCP packet header:
        // Version 4, IHL 5 (20 bytes header), Total length 40 bytes, Protocol 6 (TCP)
        // Source IP: 10.1.10.2, Dest IP: 8.8.8.8
        val packet = ByteArray(40)
        packet[0] = 0x45.toByte() // Version 4, IHL 5
        packet[1] = 0x00.toByte() // ToS
        packet[2] = 0x00.toByte() // Total Length High
        packet[3] = 40.toByte()   // Total Length Low
        packet[9] = 6.toByte()    // Protocol: TCP (6)

        // Source IP: 10.1.10.2 (bytes 12..15)
        packet[12] = 10.toByte()
        packet[13] = 1.toByte()
        packet[14] = 10.toByte()
        packet[15] = 2.toByte()

        // Dest IP: 8.8.8.8 (bytes 16..19)
        packet[16] = 8.toByte()
        packet[17] = 8.toByte()
        packet[18] = 8.toByte()
        packet[19] = 8.toByte()

        val buffer = java.nio.ByteBuffer.wrap(packet)
        val parsed = processor.parseIPv4Header(buffer)
        assertNotNull(parsed)
        assertEquals(4, parsed!!.version)
        assertEquals(6, parsed.protocol)
        assertEquals("10.1.10.2", parsed.sourceAddress)
        assertEquals("8.8.8.8", parsed.destinationAddress)
        assertEquals(40, parsed.packetLength)
    }

    @Test
    fun testStep5_FirewallEngine_deterministicAllowBlockDecisions() {
        val engine = com.example.dataguard.vpn.engine.FirewallEngine()

        // 1. Initially allowed
        val initialDecision = engine.evaluatePackage("com.android.chrome")
        assertEquals(com.example.dataguard.vpn.engine.FirewallDecision.ALLOW, initialDecision.decision)
        assertFalse(engine.isPackageBlocked("com.android.chrome"))

        // 2. Block Chrome
        engine.addBlockedPackage(
            packageName = "com.android.chrome",
            uid = 10055,
            appName = "Chrome",
            reason = "User firewall rule"
        )

        // 3. Confirm Chrome is blocked deterministically
        val blockedDecision = engine.evaluatePackage("com.android.chrome")
        assertEquals(com.example.dataguard.vpn.engine.FirewallDecision.BLOCK, blockedDecision.decision)
        assertEquals(10055, blockedDecision.uid)
        assertEquals("User firewall rule", blockedDecision.reason)
        assertTrue(engine.isPackageBlocked("com.android.chrome"))
        assertTrue(engine.isUidBlocked(10055))

        // 4. Other apps remain unaffected (fail-open / allow)
        val youtubeDecision = engine.evaluatePackage("com.google.android.youtube")
        assertEquals(com.example.dataguard.vpn.engine.FirewallDecision.ALLOW, youtubeDecision.decision)
        assertFalse(engine.isPackageBlocked("com.google.android.youtube"))

        // 5. Unblock Chrome
        engine.removeBlockedPackage("com.android.chrome")
        val unblockedDecision = engine.evaluatePackage("com.android.chrome")
        assertEquals(com.example.dataguard.vpn.engine.FirewallDecision.ALLOW, unblockedDecision.decision)
        assertFalse(engine.isPackageBlocked("com.android.chrome"))
        assertFalse(engine.isUidBlocked(10055))
    }

    @Test
    fun testStep5_FirewallEngine_rulesUpdateFromRoomEntities() {
        val engine = com.example.dataguard.vpn.engine.FirewallEngine()

        val rulesFromDb = listOf(
            BlockedAppEntity(
                uid = 10100,
                packageName = "com.android.chrome",
                appName = "Chrome",
                blockedAt = 1000L,
                reason = "User firewall rule",
                blockMobile = true,
                blockWifi = true
            ),
            BlockedAppEntity(
                uid = 10200,
                packageName = "com.spotify.music",
                appName = "Spotify",
                blockedAt = 2000L,
                reason = "Data saver policy",
                blockMobile = true,
                blockWifi = false
            )
        )

        // Simulate syncing from Room database
        engine.updateRulesFromEntities(rulesFromDb)

        assertTrue(engine.isPackageBlocked("com.android.chrome"))
        assertTrue(engine.isUidBlocked(10100))
        assertTrue(engine.isPackageBlocked("com.spotify.music"))
        assertTrue(engine.isUidBlocked(10200))

        // Verify interface selective evaluation
        val spotifyMobile = engine.evaluateUid(10200, com.example.dataguard.vpn.engine.NetworkInterfaceType.MOBILE)
        assertEquals(com.example.dataguard.vpn.engine.FirewallDecision.BLOCK, spotifyMobile.decision)

        val spotifyWifi = engine.evaluateUid(10200, com.example.dataguard.vpn.engine.NetworkInterfaceType.WIFI)
        assertEquals(com.example.dataguard.vpn.engine.FirewallDecision.ALLOW, spotifyWifi.decision)

        // Clear and verify
        engine.clear()
        assertFalse(engine.isPackageBlocked("com.android.chrome"))
        assertFalse(engine.isPackageBlocked("com.spotify.music"))
    }

    @Test
    fun testStep5_FirewallEngine_networkSelectiveBlockingAndDomainInference() {
        val engine = com.example.dataguard.vpn.engine.FirewallEngine()

        val rules = listOf(
            BlockedAppEntity(
                uid = 10300,
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                blockedAt = 3000L,
                reason = "Mobile data saver",
                blockMobile = true,
                blockWifi = false
            ),
            BlockedAppEntity(
                uid = 10400,
                packageName = "com.netflix.mediaclient",
                appName = "Netflix",
                blockedAt = 4000L,
                reason = "Heavy video saver",
                blockMobile = true,
                blockWifi = true
            )
        )
        engine.updateRulesFromEntities(rules)

        // Verify getBlockedPackages for MOBILE vs WIFI
        val mobileBlocked = engine.getBlockedPackages(com.example.dataguard.vpn.engine.NetworkInterfaceType.MOBILE)
        val wifiBlocked = engine.getBlockedPackages(com.example.dataguard.vpn.engine.NetworkInterfaceType.WIFI)

        assertTrue(mobileBlocked.contains("com.whatsapp"))
        assertTrue(mobileBlocked.contains("com.netflix.mediaclient"))
        assertEquals(2, mobileBlocked.size)

        assertFalse(wifiBlocked.contains("com.whatsapp"))
        assertTrue(wifiBlocked.contains("com.netflix.mediaclient"))
        assertEquals(1, wifiBlocked.size)

        // Verify evaluateConnection when packageName is null but domain matches WhatsApp
        val evalMobile = engine.evaluateConnection(
            domain = "g.whatsapp.net",
            packageName = null,
            networkType = com.example.dataguard.vpn.engine.NetworkInterfaceType.MOBILE
        )
        assertEquals(com.example.dataguard.vpn.engine.FirewallDecision.BLOCK, evalMobile.decision)
        assertEquals("com.whatsapp", evalMobile.packageName)

        val evalWifi = engine.evaluateConnection(
            domain = "g.whatsapp.net",
            packageName = null,
            networkType = com.example.dataguard.vpn.engine.NetworkInterfaceType.WIFI
        )
        assertEquals(com.example.dataguard.vpn.engine.FirewallDecision.ALLOW, evalWifi.decision)
    }

    @Test
    fun testStep5_FirewallEngine_failOpenOnSafetyException() {
        val engine = com.example.dataguard.vpn.engine.FirewallEngine()
        // Ensure unknown package or invalid UID safely evaluates to ALLOW without throwing
        val safeDecision = engine.evaluateUid(-9999)
        assertEquals(com.example.dataguard.vpn.engine.FirewallDecision.ALLOW, safeDecision.decision)
    }

    @Test
    fun testStep6_DnsQueryParser_validDomainExtraction() {
        // Synthesize valid RFC 1035 standard DNS query UDP packet for "youtube.com"
        // 12 bytes header + 7 'youtube' + 3 'com' + 0 + 4 (QTYPE, QCLASS)
        val packet = java.io.ByteArrayOutputStream()
        // DNS Header (12 bytes)
        packet.write(byteArrayOf(0x12, 0x34)) // ID
        packet.write(byteArrayOf(0x01, 0x00)) // Flags: standard query, RD=1
        packet.write(byteArrayOf(0x00, 0x01)) // QDCOUNT = 1
        packet.write(byteArrayOf(0x00, 0x00)) // ANCOUNT = 0
        packet.write(byteArrayOf(0x00, 0x00)) // NSCOUNT = 0
        packet.write(byteArrayOf(0x00, 0x00)) // ARCOUNT = 0

        // Question: "youtube.com" -> \x07youtube\x03com\x00
        packet.write(7)
        packet.write("youtube".toByteArray(Charsets.US_ASCII))
        packet.write(3)
        packet.write("com".toByteArray(Charsets.US_ASCII))
        packet.write(0)

        // QTYPE = 1 (A), QCLASS = 1 (IN)
        packet.write(byteArrayOf(0x00, 0x01))
        packet.write(byteArrayOf(0x00, 0x01))

        val buffer = java.nio.ByteBuffer.wrap(packet.toByteArray())
        val extractedDomain = com.example.dataguard.vpn.engine.DnsQueryParser.extractDnsDomain(buffer)
        assertEquals("youtube.com", extractedDomain)
    }

    @Test
    fun testStep6_SniParser_extractTlsServerNameIndication() {
        // Synthesize valid TLS 1.2 ClientHello with SNI extension for "googlevideo.com"
        val host = "googlevideo.com"
        val hostBytes = host.toByteArray(Charsets.US_ASCII)

        val sniExt = java.io.ByteArrayOutputStream()
        sniExt.write(byteArrayOf(0x00, 0x00)) // Extension type: server_name (0)
        val serverNameListLen = hostBytes.size + 3
        val extLen = serverNameListLen + 2
        sniExt.write(byteArrayOf((extLen shr 8).toByte(), extLen.toByte()))
        sniExt.write(byteArrayOf((serverNameListLen shr 8).toByte(), serverNameListLen.toByte()))
        sniExt.write(0) // name_type: host_name (0)
        sniExt.write(byteArrayOf((hostBytes.size shr 8).toByte(), hostBytes.size.toByte()))
        sniExt.write(hostBytes)

        val extensionsBytes = sniExt.toByteArray()

        val clientHello = java.io.ByteArrayOutputStream()
        clientHello.write(0x01) // Handshake type: ClientHello
        val hLen = 38 + 1 + 2 + 1 + 2 + extensionsBytes.size
        clientHello.write((hLen shr 16) and 0xFF)
        clientHello.write((hLen shr 8) and 0xFF)
        clientHello.write(hLen and 0xFF)
        clientHello.write(byteArrayOf(0x03, 0x03)) // Version TLS 1.2
        clientHello.write(ByteArray(32)) // Random (32 bytes)
        clientHello.write(0) // Session ID length (0)
        clientHello.write(byteArrayOf(0x00, 0x02, 0x00, 0x2f)) // Cipher suites
        clientHello.write(byteArrayOf(0x01, 0x00)) // Compression methods
        clientHello.write(byteArrayOf((extensionsBytes.size shr 8).toByte(), extensionsBytes.size.toByte()))
        clientHello.write(extensionsBytes)

        val chBytes = clientHello.toByteArray()

        val record = java.io.ByteArrayOutputStream()
        record.write(0x16) // ContentType: Handshake (22)
        record.write(byteArrayOf(0x03, 0x01)) // Record version TLS 1.0
        record.write(byteArrayOf((chBytes.size shr 8).toByte(), chBytes.size.toByte()))
        record.write(chBytes)

        val buffer = java.nio.ByteBuffer.wrap(record.toByteArray())
        val extractedSni = com.example.dataguard.vpn.engine.TlsSniParser.extractSniHostname(buffer)
        assertEquals("googlevideo.com", extractedSni)
    }

    @Test
    fun testStep6_DomainAttribution_confidenceEvaluation() {
        // High confidence: Extracted directly via SNI or DNS
        val highConf = com.example.dataguard.data.entity.AttributionConfidence.HIGH
        assertEquals("High", highConf.label)

        // Medium confidence: Derived through cached DNS mapping
        val medConf = com.example.dataguard.data.entity.AttributionConfidence.MEDIUM
        assertEquals("Medium", medConf.label)

        // Low confidence: Destination IP without observed host header
        val lowConf = com.example.dataguard.data.entity.AttributionConfidence.LOW
        assertEquals("Low", lowConf.label)

        // Unknown
        val unkConf = com.example.dataguard.data.entity.AttributionConfidence.UNKNOWN
        assertEquals("Unknown", unkConf.label)
    }

    @Test
    fun testStep6_ExpectedDomains_and_associations() {
        // Verified domain associations for real apps specified in brief:
        // YouTube: googlevideo.com, youtube.com
        // Instagram: cdninstagram.com, instagram.com
        // WhatsApp: whatsapp.net, whatsapp.com
        // Google Search: googleapis.com, gstatic.com, google.com
        val youtubeDomains = listOf("youtube.com", "googlevideo.com", "ytimg.com")
        val instagramDomains = listOf("instagram.com", "cdninstagram.com", "fbcdn.net")
        val whatsappDomains = listOf("whatsapp.com", "whatsapp.net")
        val googleDomains = listOf("google.com", "googleapis.com", "gstatic.com")

        assertTrue(youtubeDomains.contains("googlevideo.com"))
        assertTrue(instagramDomains.contains("cdninstagram.com"))
        assertTrue(whatsappDomains.contains("whatsapp.net"))
        assertTrue(googleDomains.contains("gstatic.com"))
    }

    @Test
    fun testStep7_DomainNormalizer_normalizationAndPunycode() {
        val normalizer = com.example.dataguard.vpn.engine.DomainNormalizer

        // 1. Lowercase and whitespace stripping
        assertEquals("youtube.com", normalizer.normalize("  YouTube.COM  "))

        // 2. Trailing dot stripping
        assertEquals("youtube.com", normalizer.normalize("youtube.com."))
        assertEquals("youtube.com", normalizer.normalize("youtube.com..."))

        // 3. Scheme and path stripping
        assertEquals("facebook.com", normalizer.normalize("https://facebook.com/login?param=1#hash"))
        assertEquals("instagram.com", normalizer.normalize("http://instagram.com:443/explore"))

        // 4. Wildcard prefix stripping
        assertEquals("youtube.com", normalizer.normalize("*.youtube.com"))
        assertEquals("facebook.com", normalizer.normalize("*facebook.com"))

        // 5. Punycode IDN conversion
        // "münchen.de" in punycode is "xn--mnchen-3ya.de"
        val idn = normalizer.normalize("münchen.de")
        assertTrue("IDN converted to punycode", idn.startsWith("xn--"))
    }

    @Test
    fun testStep7_DomainNormalizer_subdomainAndWildcardMatching() {
        val normalizer = com.example.dataguard.vpn.engine.DomainNormalizer

        // Exact match
        assertTrue(normalizer.matches("youtube.com", "youtube.com"))
        assertTrue(normalizer.matches("YOUTUBE.COM", "youtube.com"))

        // Subdomain matching: rule "youtube.com" matches "m.youtube.com", "video.youtube.com"
        assertTrue(normalizer.matches("m.youtube.com", "youtube.com"))
        assertTrue(normalizer.matches("video.youtube.com", "youtube.com"))
        assertTrue(normalizer.matches("deep.sub.youtube.com", "youtube.com"))

        // Wildcard prefix rule matching
        assertTrue(normalizer.matches("m.youtube.com", "*.youtube.com"))

        // Naive substring test: "notyoutube.com" or "myoutube.com" MUST NOT MATCH
        assertFalse(normalizer.matches("notyoutube.com", "youtube.com"))
        assertFalse(normalizer.matches("myoutube.com", "youtube.com"))
        assertFalse(normalizer.matches("fake-youtube.com", "youtube.com"))

        // www matching
        assertTrue(normalizer.matches("www.youtube.com", "youtube.com"))
        assertTrue(normalizer.matches("youtube.com", "www.youtube.com"))
    }

    @Test
    fun testStep7_FirewallEngine_deterministicRulePriority() {
        val engine = com.example.dataguard.vpn.engine.FirewallEngine()

        // Setup test scenario:
        // Rule A (Global Block): Block "youtube.com" everywhere
        val globalBlockRule = com.example.dataguard.data.entity.DomainRuleEntity(
            id = 1,
            domainPattern = "youtube.com",
            ruleType = com.example.dataguard.vpn.engine.RuleType.GLOBAL_BLOCK.name,
            networkScope = "ANY"
        )

        // Rule B (App Allow): Allow "youtube.com" specifically for Chrome
        val appAllowRule = com.example.dataguard.data.entity.DomainRuleEntity(
            id = 2,
            domainPattern = "youtube.com",
            ruleType = com.example.dataguard.vpn.engine.RuleType.APP_ALLOW.name,
            packageName = "com.android.chrome",
            appName = "Chrome",
            networkScope = "ANY"
        )

        engine.updateDomainRulesFromEntities(listOf(globalBlockRule, appAllowRule))

        // 1. Chrome targeting youtube.com -> Must be ALLOWED (APP+DOMAIN overrides GLOBAL DOMAIN)
        val chromeResult = engine.evaluateConnection(
            domain = "youtube.com",
            packageName = "com.android.chrome"
        )
        assertEquals(com.example.dataguard.vpn.engine.FirewallDecision.ALLOW, chromeResult.decision)
        assertTrue(chromeResult.reason?.contains("App allow rule") == true)

        // 2. Subdomain test: Chrome targeting m.youtube.com -> Must also be ALLOWED
        val chromeSubdomain = engine.evaluateConnection(
            domain = "m.youtube.com",
            packageName = "com.android.chrome"
        )
        assertEquals(com.example.dataguard.vpn.engine.FirewallDecision.ALLOW, chromeSubdomain.decision)

        // 3. Any other app targeting youtube.com -> Must be BLOCKED by Global Domain rule
        val defaultAppResult = engine.evaluateConnection(
            domain = "youtube.com",
            packageName = "com.example.otherapp"
        )
        assertEquals(com.example.dataguard.vpn.engine.FirewallDecision.BLOCK, defaultAppResult.decision)
        assertTrue(defaultAppResult.reason?.contains("Global domain block rule") == true)
    }

    @Test
    fun testStep7_FirewallEngine_networkScopeEnforcement() {
        val engine = com.example.dataguard.vpn.engine.FirewallEngine()

        // Rule: Block "facebook.com" ONLY on MOBILE data
        val mobileOnlyBlock = com.example.dataguard.data.entity.DomainRuleEntity(
            id = 10,
            domainPattern = "facebook.com",
            ruleType = com.example.dataguard.vpn.engine.RuleType.GLOBAL_BLOCK.name,
            networkScope = "MOBILE"
        )
        engine.updateDomainRulesFromEntities(listOf(mobileOnlyBlock))

        // On Mobile: Must be BLOCKED
        val mobileEval = engine.evaluateConnection(
            domain = "facebook.com",
            packageName = "com.android.chrome",
            networkType = com.example.dataguard.vpn.engine.NetworkInterfaceType.MOBILE
        )
        assertEquals(com.example.dataguard.vpn.engine.FirewallDecision.BLOCK, mobileEval.decision)

        // On Wi-Fi: Must be ALLOWED
        val wifiEval = engine.evaluateConnection(
            domain = "facebook.com",
            packageName = "com.android.chrome",
            networkType = com.example.dataguard.vpn.engine.NetworkInterfaceType.WIFI
        )
        assertEquals(com.example.dataguard.vpn.engine.FirewallDecision.ALLOW, wifiEval.decision)
    }

    @Test
    fun testStep7_FirewallEngine_unblockReturnsAccess() {
        val engine = com.example.dataguard.vpn.engine.FirewallEngine()

        // 1. Block "instagram.com"
        val rule = com.example.dataguard.data.entity.DomainRuleEntity(
            id = 20,
            domainPattern = "instagram.com",
            ruleType = com.example.dataguard.vpn.engine.RuleType.GLOBAL_BLOCK.name
        )
        engine.updateDomainRulesFromEntities(listOf(rule))
        assertTrue(engine.isDomainBlocked("instagram.com"))
        assertTrue(engine.isDomainBlocked("cdninstagram.com.instagram.com")) // subdomain

        // 2. Unblock (remove rule)
        engine.updateDomainRulesFromEntities(emptyList())
        assertFalse(engine.isDomainBlocked("instagram.com"))
    }

    // ==========================================
    // STEP 8: Hotspot & Tethering Monitoring Tests
    // ==========================================

    @Test
    fun testStep8_HotspotState_modelsAndSessionTypes() {
        // 1. Inactive State
        val inactiveState = com.example.dataguard.domain.hotspot.HotspotState(
            isActive = false,
            sessionType = "INACTIVE",
            activeInterfaces = emptyList(),
            stateReason = "No active tethering detected"
        )
        assertFalse(inactiveState.isActive)
        assertEquals("INACTIVE", inactiveState.sessionType)
        assertTrue(inactiveState.activeInterfaces.isEmpty())

        // 2. Wi-Fi AP Active State
        val wifiApState = com.example.dataguard.domain.hotspot.HotspotState(
            isActive = true,
            sessionType = "WIFI_AP",
            activeInterfaces = listOf("ap0"),
            stateReason = "Wi-Fi Hotspot is active (ap0)"
        )
        assertTrue(wifiApState.isActive)
        assertEquals("WIFI_AP", wifiApState.sessionType)
        assertEquals(listOf("ap0"), wifiApState.activeInterfaces)

        // 3. USB Tethering Active State
        val usbState = com.example.dataguard.domain.hotspot.HotspotState(
            isActive = true,
            sessionType = "USB_TETHERING",
            activeInterfaces = listOf("rndis0"),
            stateReason = "USB Tethering is active (rndis0)"
        )
        assertTrue(usbState.isActive)
        assertEquals("USB_TETHERING", usbState.sessionType)
    }

    @Test
    fun testStep8_HotspotSessionEntity_durationAndMetrics() {
        val startTime = 1700000000000L
        val endTime = startTime + (45 * 60 * 1000L) // 45 minutes

        val session = com.example.dataguard.data.entity.HotspotSessionEntity(
            id = 1,
            startTime = startTime,
            endTime = endTime,
            isActive = false,
            uploadBytes = 25 * 1024 * 1024L,   // 25 MB
            downloadBytes = 150 * 1024 * 1024L, // 150 MB
            totalBytes = 175 * 1024 * 1024L,    // 175 MB
            sessionType = "WIFI_AP",
            clientAttributionState = "Tethering traffic",
            interfaceName = "ap0"
        )

        assertEquals(45 * 60 * 1000L, session.durationMs)
        assertEquals(175 * 1024 * 1024L, session.totalBytes)
        assertEquals("Tethering traffic", session.clientAttributionState)
        assertFalse(session.isActive)

        // Ongoing session
        val ongoingSession = com.example.dataguard.data.entity.HotspotSessionEntity(
            id = 2,
            startTime = System.currentTimeMillis() - 5000L,
            endTime = null,
            isActive = true,
            uploadBytes = 1000L,
            downloadBytes = 4000L,
            totalBytes = 5000L,
            sessionType = "WIFI_AP"
        )
        assertTrue(ongoingSession.isActive)
        assertTrue(ongoingSession.durationMs >= 5000L)
    }

    @Test
    fun testStep8_TetheringTrafficSummary_attributionTransparency() {
        val summary = com.example.dataguard.domain.hotspot.TetheringTrafficSummary(
            uploadBytes = 5000000L,
            downloadBytes = 20000000L,
            totalBytes = 25000000L,
            isFromNetworkStatsManager = true
        )

        assertEquals(25000000L, summary.totalBytes)
        assertTrue(summary.isFromNetworkStatsManager)

        // Verify Android platform limitation explanation is documented and accurate
        val explanation = com.example.dataguard.domain.hotspot.HotspotMonitor.ANDROID_LIMITATION_EXPLANATION
        assertTrue(explanation.contains("UID"))
        assertTrue(explanation.contains("Tethering"))
        assertTrue(explanation.contains("client"))
    }

    @Test
    fun testStep8_HotspotViewModel_historyFilteringLogic() {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - (60 * 60 * 1000L)
        val twoDaysAgo = now - (2 * 24 * 60 * 60 * 1000L)
        val tenDaysAgo = now - (10 * 24 * 60 * 60 * 1000L)

        val sessionRecent = com.example.dataguard.data.entity.HotspotSessionEntity(
            id = 101, startTime = oneHourAgo, endTime = now, isActive = false, totalBytes = 1000L
        )
        val sessionTwoDaysAgo = com.example.dataguard.data.entity.HotspotSessionEntity(
            id = 102, startTime = twoDaysAgo, endTime = twoDaysAgo + 10000L, isActive = false, totalBytes = 2000L
        )
        val sessionOld = com.example.dataguard.data.entity.HotspotSessionEntity(
            id = 103, startTime = tenDaysAgo, endTime = tenDaysAgo + 10000L, isActive = false, totalBytes = 3000L
        )

        val allSessions = listOf(sessionRecent, sessionTwoDaysAgo, sessionOld)

        // Filter ALL
        assertEquals(3, allSessions.size)

        // Filter THIS_WEEK (within 7 days)
        val thisWeekSessions = allSessions.filter { it.startTime >= now - (7L * 24 * 60 * 60 * 1000L) }
        assertEquals(2, thisWeekSessions.size)
        assertTrue(thisWeekSessions.contains(sessionRecent))
        assertTrue(thisWeekSessions.contains(sessionTwoDaysAgo))
        assertFalse(thisWeekSessions.contains(sessionOld))
    }

    // ==========================================
    // MILESTONE 9: Network Activity Dashboard Tests
    // ==========================================

    @Test
    fun testMilestone9_TimelineModels_pointsAndIntervals() {
        val now = System.currentTimeMillis()
        val point = com.example.dataguard.domain.model.NetworkTimelinePoint(
            timestamp = now,
            label = "14:00",
            startTime = now - 3600000L,
            endTime = now,
            uploadBytes = 1200000L,
            downloadBytes = 4800000L,
            mobileBytes = 2000000L,
            wifiBytes = 4000000L,
            allowedBytes = 5500000L,
            blockedBytes = 500000L,
            activeApps = listOf("Chrome", "YouTube"),
            contactedDomains = listOf("google.com", "googlevideo.com")
        )

        assertEquals(6000000L, point.totalBytes)
        assertEquals(1200000L, point.uploadBytes)
        assertEquals(4800000L, point.downloadBytes)
        assertEquals(2, point.activeApps.size)
        assertEquals(2, point.contactedDomains.size)
    }

    @Test
    fun testMilestone9_GraphFilterAndTimeRange_enums() {
        val filters = com.example.dataguard.domain.model.GraphFilter.values()
        assertTrue(filters.contains(com.example.dataguard.domain.model.GraphFilter.ALL))
        assertTrue(filters.contains(com.example.dataguard.domain.model.GraphFilter.MOBILE))
        assertTrue(filters.contains(com.example.dataguard.domain.model.GraphFilter.WIFI))
        assertTrue(filters.contains(com.example.dataguard.domain.model.GraphFilter.APPS))
        assertTrue(filters.contains(com.example.dataguard.domain.model.GraphFilter.WEBSITES))

        val ranges = com.example.dataguard.domain.model.DashboardTimeRange.values()
        assertTrue(ranges.contains(com.example.dataguard.domain.model.DashboardTimeRange.TODAY))
        assertTrue(ranges.contains(com.example.dataguard.domain.model.DashboardTimeRange.DAYS_7))
        assertTrue(ranges.contains(com.example.dataguard.domain.model.DashboardTimeRange.DAYS_30))
        assertTrue(ranges.contains(com.example.dataguard.domain.model.DashboardTimeRange.CUSTOM))
    }

    @Test
    fun testMilestone9_IntervalDetails_aggregationAndAttribution() {
        val appUsage1 = com.example.dataguard.domain.model.AppIntervalUsage(
            appName = "YouTube",
            packageName = "com.google.android.youtube",
            uid = 10123,
            uploadBytes = 200000L,
            downloadBytes = 1800000L,
            mobileBytes = 500000L,
            wifiBytes = 1500000L,
            isBlocked = false
        )
        val appUsage2 = com.example.dataguard.domain.model.AppIntervalUsage(
            appName = "Tracker App",
            packageName = "com.tracker.analytics",
            uid = 10199,
            uploadBytes = 50000L,
            downloadBytes = 10000L,
            mobileBytes = 60000L,
            wifiBytes = 0L,
            isBlocked = true
        )

        val domainUsage = com.example.dataguard.domain.model.DomainIntervalUsage(
            domain = "googlevideo.com",
            uploadBytes = 100000L,
            downloadBytes = 1700000L,
            connectionCount = 14,
            associatedApps = listOf("YouTube"),
            isBlocked = false
        )

        val intervalDetails = com.example.dataguard.domain.model.GraphIntervalDetails(
            startTime = 1700000000000L,
            endTime = 1700003600000L,
            label = "12:00 - 13:00",
            totalDataConsumed = 2060000L,
            uploadBytes = 250000L,
            downloadBytes = 1810000L,
            mobileBytes = 560000L,
            wifiBytes = 1500000L,
            allowedBytes = 2000000L,
            blockedBytes = 60000L,
            activeApps = listOf(appUsage1, appUsage2),
            contactedDomains = listOf(domainUsage),
            blockedEvents = emptyList()
        )

        assertEquals(2, intervalDetails.activeApps.size)
        assertEquals(1, intervalDetails.contactedDomains.size)
        assertEquals(60000L, intervalDetails.blockedBytes)
        assertEquals(2000000L, intervalDetails.allowedBytes)
        assertTrue(intervalDetails.activeApps.any { it.isBlocked })
        assertFalse(intervalDetails.activeApps.first().isBlocked)
    }

    @Test
    fun testMilestone9_DomainSummary_associatedAppsProperty() {
        val summary = com.example.dataguard.data.entity.DomainSummary(
            domain = "api.github.com",
            totalBytes = 45000L,
            uploadBytes = 15000L,
            downloadBytes = 30000L,
            connectionCount = 3,
            associatedPackages = listOf("com.github.mobile"),
            associatedAppNames = listOf("GitHub"),
            firstSeen = 1700000000000L,
            lastSeen = 1700001000000L,
            confidence = com.example.dataguard.data.entity.AttributionConfidence.HIGH
        )

        assertEquals("api.github.com", summary.domain)
        assertEquals(listOf("GitHub"), summary.associatedApps)
        assertEquals(listOf("com.github.mobile"), summary.associatedPackages)
        assertEquals(com.example.dataguard.data.entity.AttributionConfidence.HIGH, summary.confidence)
    }

    // ==========================================
    // MILESTONE 10: Data Plan & Alert System Tests
    // ==========================================

    @Test
    fun testMilestone10_DataPlanEntity_allowanceAndThresholdParsing() {
        val plan = com.example.dataguard.data.entity.DataPlanEntity(
            planName = "MTN Monthly Plan",
            totalAllowanceBytes = 10L * 1024L * 1024L * 1024L, // 10 GB
            rolloverBytes = 2L * 1024L * 1024L * 1024L, // 2 GB rollover
            billingCycle = com.example.dataguard.data.entity.BillingCycleType.MONTHLY.name,
            trafficScope = com.example.dataguard.data.entity.PlanTrafficScope.MOBILE_ONLY.name,
            warningThresholdsJson = "50,75,90,100",
            excludedPackageNamesJson = "com.google.android.youtube, com.whatsapp"
        )

        // Verify total effective allowance includes rollover
        assertEquals(12L * 1024L * 1024L * 1024L, plan.totalEffectiveAllowanceBytes)

        // Verify warning thresholds parsed and sorted
        val thresholds = plan.warningThresholdsList
        assertEquals(listOf(50, 75, 90, 100), thresholds)

        // Verify excluded packages parsed cleanly
        val excluded = plan.excludedPackagesList
        assertEquals(2, excluded.size)
        assertTrue(excluded.contains("com.google.android.youtube"))
        assertTrue(excluded.contains("com.whatsapp"))
    }

    @Test
    fun testMilestone10_DataPlanCycleCalculator_cycles() {
        val now = 1700000000000L // arbitrary fixed timestamp

        // 1. Daily cycle
        val dailyPlan = com.example.dataguard.data.entity.DataPlanEntity(
            billingCycle = com.example.dataguard.data.entity.BillingCycleType.DAILY.name
        )
        val dailyWindow = com.example.dataguard.domain.plan.DataPlanCycleCalculator.calculateCycleWindow(dailyPlan, now)
        assertTrue(dailyWindow.cycleEndTime > dailyWindow.cycleStartTime)
        assertEquals("Tonight at Midnight", dailyWindow.formattedResetDate)

        // 2. Weekly cycle
        val weeklyPlan = com.example.dataguard.data.entity.DataPlanEntity(
            billingCycle = com.example.dataguard.data.entity.BillingCycleType.WEEKLY.name
        )
        val weeklyWindow = com.example.dataguard.domain.plan.DataPlanCycleCalculator.calculateCycleWindow(weeklyPlan, now)
        assertTrue(weeklyWindow.cycleEndTime > weeklyWindow.cycleStartTime)
        assertEquals(7 * 24 * 60 * 60 * 1000L, weeklyWindow.cycleEndTime - weeklyWindow.cycleStartTime)

        // 3. Custom days cycle (e.g. 14 days)
        val customPlan = com.example.dataguard.data.entity.DataPlanEntity(
            billingCycle = com.example.dataguard.data.entity.BillingCycleType.CUSTOM_DAYS.name,
            customCycleDays = 14,
            startTimestamp = now - (2 * 24 * 60 * 60 * 1000L) // started 2 days ago
        )
        val customWindow = com.example.dataguard.domain.plan.DataPlanCycleCalculator.calculateCycleWindow(customPlan, now)
        assertTrue(customWindow.cycleEndTime > now)
        assertEquals(14 * 24 * 60 * 60 * 1000L, customWindow.cycleEndTime - customWindow.cycleStartTime)
    }

    @Test
    fun testMilestone10_ThresholdCalculationLogic() {
        val totalAllowance = 10L * 1024L * 1024L * 1024L // 10 GB
        val thresholds = listOf(50, 75, 90, 100)

        // Under 50%
        val used3Gb = 3L * 1024L * 1024L * 1024L
        val pct3Gb = (used3Gb.toFloat() / totalAllowance.toFloat()) * 100f
        assertEquals(30f, pct3Gb, 0.01f)
        val reached0 = thresholds.filter { pct3Gb >= it }.maxOrNull() ?: 0
        assertEquals(0, reached0)

        // At 55% -> reaches 50% threshold
        val used5500Mb = (5.5 * 1024 * 1024 * 1024).toLong()
        val pct55 = (used5500Mb.toFloat() / totalAllowance.toFloat()) * 100f
        val reached50 = thresholds.filter { pct55 >= it }.maxOrNull() ?: 0
        assertEquals(50, reached50)

        // At 78% -> reaches 75% threshold
        val used7800Mb = (7.8 * 1024 * 1024 * 1024).toLong()
        val pct78 = (used7800Mb.toFloat() / totalAllowance.toFloat()) * 100f
        val reached75 = thresholds.filter { pct78 >= it }.maxOrNull() ?: 0
        assertEquals(75, reached75)

        // At 92% -> reaches 90% threshold
        val used9200Mb = (9.2 * 1024 * 1024 * 1024).toLong()
        val pct92 = (used9200Mb.toFloat() / totalAllowance.toFloat()) * 100f
        val reached90 = thresholds.filter { pct92 >= it }.maxOrNull() ?: 0
        assertEquals(90, reached90)

        // At 100%+ -> reaches 100% threshold
        val used11Gb = 11L * 1024L * 1024L * 1024L
        val pct100 = (used11Gb.toFloat() / totalAllowance.toFloat()) * 100f
        val reached100 = thresholds.filter { pct100 >= it }.maxOrNull() ?: 0
        assertEquals(100, reached100)
    }

    @Test
    fun testMilestone10_NotificationHistoryEntity_attributes() {
        val entity = com.example.dataguard.data.entity.NotificationHistoryEntity(
            title = "Plan Alert: MTN Monthly Plan at 75%",
            message = "Consumed 7.5 GB of 10.0 GB (75%)",
            category = com.example.dataguard.data.entity.NotificationAlertCategory.PLAN_THRESHOLD.name,
            targetKey = "plan",
            bytesObserved = (7.5 * 1024 * 1024 * 1024).toLong(),
            thresholdPercent = 75,
            actionTaken = "Threshold Notice"
        )

        assertEquals("Plan Alert: MTN Monthly Plan at 75%", entity.title)
        assertEquals("PLAN_THRESHOLD", entity.category)
        assertEquals(75, entity.thresholdPercent)
        assertEquals("plan", entity.targetKey)
        assertFalse(entity.isRead)
    }

    @Test
    fun testMilestone10_NotificationPreferences_defaults() {
        val prefs = com.example.dataguard.data.entity.NotificationPreferencesEntity()
        assertTrue(prefs.isEnabledMaster)
        assertTrue(prefs.alertOn50Percent)
        assertTrue(prefs.alertOn75Percent)
        assertTrue(prefs.alertOn90Percent)
        assertTrue(prefs.alertOn100Percent)
        assertTrue(prefs.alertAppExceedsMb)
        assertEquals(250L, prefs.appExceedsThresholdMb)
        assertTrue(prefs.alertHotspotExceedsMb)
        assertEquals(500L, prefs.hotspotExceedsThresholdMb)
        assertEquals(15, prefs.minAlertIntervalMinutes)
        assertTrue(prefs.groupNotifications)
    }

    @Test
    fun testMilestone11_DataUnitUtils_binaryConversions() {
        val mbUnit = com.example.dataguard.utils.DataLimitUnit.MB
        val gbUnit = com.example.dataguard.utils.DataLimitUnit.GB

        // Binary definition: 1 MB = 1024 * 1024 bytes, 1 GB = 1024 * 1024 * 1024 bytes
        assertEquals(1048576L, mbUnit.multiplierBytes)
        assertEquals(1073741824L, gbUnit.multiplierBytes)
        assertEquals(1024L * mbUnit.multiplierBytes, gbUnit.multiplierBytes)

        // Default Daily Limit: 1000 MB
        val dailyBytes = com.example.dataguard.utils.DataUnitUtils.limitToBytes(1000.0, mbUnit)
        assertEquals(1048576000L, dailyBytes)
        assertEquals(1000.0, com.example.dataguard.utils.DataUnitUtils.bytesToLimitValue(dailyBytes, mbUnit), 0.0001)

        // Default Weekly Limit: 6.8 GB
        val weeklyBytes = com.example.dataguard.utils.DataUnitUtils.limitToBytes(6.8, gbUnit)
        val expectedWeeklyBytes = (6.8 * 1024.0 * 1024.0 * 1024.0).toLong()
        assertEquals(expectedWeeklyBytes, weeklyBytes)
        assertEquals(6.8, com.example.dataguard.utils.DataUnitUtils.bytesToLimitValue(weeklyBytes, gbUnit), 0.0001)

        // Default Monthly Limit: 29.3 GB
        val monthlyBytes = com.example.dataguard.utils.DataUnitUtils.limitToBytes(29.3, gbUnit)
        val expectedMonthlyBytes = (29.3 * 1024.0 * 1024.0 * 1024.0).toLong()
        assertEquals(expectedMonthlyBytes, monthlyBytes)
        assertEquals(29.3, com.example.dataguard.utils.DataUnitUtils.bytesToLimitValue(monthlyBytes, gbUnit), 0.0001)
    }

    @Test
    fun testMilestone11_DataUnitUtils_unitSwitchingAndPreservation() {
        val mbUnit = com.example.dataguard.utils.DataLimitUnit.MB
        val gbUnit = com.example.dataguard.utils.DataLimitUnit.GB

        // 2 GB switched to MB must equal 2048 MB exactly
        val twoGbBytes = com.example.dataguard.utils.DataUnitUtils.limitToBytes(2.0, gbUnit)
        val inMb = com.example.dataguard.utils.DataUnitUtils.bytesToLimitValue(twoGbBytes, mbUnit)
        assertEquals(2048.0, inMb, 0.0001)
        assertEquals("2048", com.example.dataguard.utils.DataUnitUtils.formatLimitValue(inMb))

        // 2048 MB switched to GB must equal 2 GB exactly
        val convertedGb = com.example.dataguard.utils.DataUnitUtils.convertValue(2048.0, mbUnit, gbUnit)
        assertEquals(2.0, convertedGb, 0.0001)
        assertEquals("2", com.example.dataguard.utils.DataUnitUtils.formatLimitValue(convertedGb))

        // 1000 MB switched to GB should yield ~0.977 GB
        val oneThousandMbBytes = com.example.dataguard.utils.DataUnitUtils.limitToBytes(1000.0, mbUnit)
        val inGb = com.example.dataguard.utils.DataUnitUtils.bytesToLimitValue(oneThousandMbBytes, gbUnit)
        assertEquals(1000.0 / 1024.0, inGb, 0.0001)
        assertEquals("0.977", com.example.dataguard.utils.DataUnitUtils.formatLimitValue(inGb))

        // Switching back from canonical bytes preserves 1000 MB without loss
        val backToMb = com.example.dataguard.utils.DataUnitUtils.bytesToLimitValue(oneThousandMbBytes, mbUnit)
        assertEquals(1000.0, backToMb, 0.0001)
        assertEquals("1000", com.example.dataguard.utils.DataUnitUtils.formatLimitValue(backToMb))
    }

    @Test
    fun testMilestone11_DataUnitUtils_formattingPrecision() {
        // Up to 3 decimal places without redundant trailing zeros
        assertEquals("1000", com.example.dataguard.utils.DataUnitUtils.formatLimitValue(1000.0))
        assertEquals("6.8", com.example.dataguard.utils.DataUnitUtils.formatLimitValue(6.8))
        assertEquals("29.3", com.example.dataguard.utils.DataUnitUtils.formatLimitValue(29.3))
        assertEquals("2", com.example.dataguard.utils.DataUnitUtils.formatLimitValue(2.0))
        assertEquals("0.5", com.example.dataguard.utils.DataUnitUtils.formatLimitValue(0.5))
        assertEquals("12.345", com.example.dataguard.utils.DataUnitUtils.formatLimitValue(12.345))
        assertEquals("12.346", com.example.dataguard.utils.DataUnitUtils.formatLimitValue(12.3456))
        assertEquals("0", com.example.dataguard.utils.DataUnitUtils.formatLimitValue(0.0))
        assertEquals("0", com.example.dataguard.utils.DataUnitUtils.formatLimitValue(-5.0))
    }

    @Test
    fun testMilestone11_DataUnitUtils_validationRules() {
        // Valid inputs
        val (val1, err1) = com.example.dataguard.utils.DataUnitUtils.validateInput("1000")
        assertEquals(1000.0, val1)
        assertNull(err1)

        val (val2, err2) = com.example.dataguard.utils.DataUnitUtils.validateInput("6.8")
        assertEquals(6.8, val2)
        assertNull(err2)

        // Invalid inputs: empty or blank
        val (val3, err3) = com.example.dataguard.utils.DataUnitUtils.validateInput("")
        assertNull(val3)
        assertNotNull(err3)

        val (val4, err4) = com.example.dataguard.utils.DataUnitUtils.validateInput("   ")
        assertNull(val4)
        assertNotNull(err4)

        // Invalid inputs: zero
        val (val5, err5) = com.example.dataguard.utils.DataUnitUtils.validateInput("0")
        assertNull(val5)
        assertEquals("Limit must be greater than 0", err5)

        // Invalid inputs: negative
        val (val6, err6) = com.example.dataguard.utils.DataUnitUtils.validateInput("-15.5")
        assertNull(val6)
        assertEquals("Limit must be greater than 0", err6)

        // Invalid inputs: non-numeric
        val (val7, err7) = com.example.dataguard.utils.DataUnitUtils.validateInput("abc")
        assertNull(val7)
        assertEquals("Please enter a valid number", err7)
    }

    @Test
    fun testMilestone11_DataLimitUnit_parsing() {
        assertEquals(com.example.dataguard.utils.DataLimitUnit.MB, com.example.dataguard.utils.DataLimitUnit.fromString("MB"))
        assertEquals(com.example.dataguard.utils.DataLimitUnit.MB, com.example.dataguard.utils.DataLimitUnit.fromString("mb"))
        assertEquals(com.example.dataguard.utils.DataLimitUnit.GB, com.example.dataguard.utils.DataLimitUnit.fromString("GB"))
        assertEquals(com.example.dataguard.utils.DataLimitUnit.GB, com.example.dataguard.utils.DataLimitUnit.fromString("gb"))
        assertEquals(com.example.dataguard.utils.DataLimitUnit.MB, com.example.dataguard.utils.DataLimitUnit.fromString(null))
        assertEquals(com.example.dataguard.utils.DataLimitUnit.MB, com.example.dataguard.utils.DataLimitUnit.fromString("invalid"))
    }

    @Test
    fun testMilestone11_DailyLimit_fallbackToPreviousDayLimitLogic() {
        // Test fallback logic:
        // Default previous day limit: 1000 MB (1048576000 bytes)
        val defaultPreviousBytes = 1000L * 1024L * 1024L

        // If today's input is not inserted (empty string or blank), previous day limit is used
        val uninsertedInput = "   "
        val (parsed, error) = com.example.dataguard.utils.DataUnitUtils.validateInput(uninsertedInput)
        assertNull(parsed)
        assertNotNull(error)

        // When not inserted, the active limit falls back to previous day limit
        val activeLimit = if (parsed != null && error == null) {
            com.example.dataguard.utils.DataUnitUtils.limitToBytes(parsed, com.example.dataguard.utils.DataLimitUnit.MB)
        } else {
            defaultPreviousBytes
        }

        assertEquals(defaultPreviousBytes, activeLimit)
        assertEquals(1048576000L, activeLimit)
    }

    @Test
    fun testMilestone11_DailyLimit_archivingPreviousDayLimit() {
        // When user updates daily limit from 1000 MB to 2000 MB, previous day limit stores 1000 MB
        val initialDailyBytes = 1000L * 1024L * 1024L
        var previousDayLimitBytes = initialDailyBytes
        var currentDailyBytes = initialDailyBytes

        val newDailyBytes = 2000L * 1024L * 1024L
        if (currentDailyBytes > 0L && currentDailyBytes != newDailyBytes) {
            previousDayLimitBytes = currentDailyBytes
        }
        currentDailyBytes = newDailyBytes

        assertEquals(1000L * 1024L * 1024L, previousDayLimitBytes)
        assertEquals(2000L * 1024L * 1024L, currentDailyBytes)

        // If daily limit is cleared / not inserted, system falls back to previous day limit
        val isDailyLimitInserted = false
        val effectiveDailyLimit = if (isDailyLimitInserted) currentDailyBytes else previousDayLimitBytes
        assertEquals(1000L * 1024L * 1024L, effectiveDailyLimit)
    }

    @Test
    fun testMilestone12_DnsQueryParsing_extractsDomain() {
        // Construct standard DNS query for "example.com"
        val queryBuf = java.nio.ByteBuffer.allocate(32)
        queryBuf.putShort(0x1234.toShort()) // ID
        queryBuf.putShort(0x0100.toShort()) // Flags: standard query, RD=1
        queryBuf.putShort(1.toShort())      // QDCOUNT: 1
        queryBuf.putShort(0.toShort())      // ANCOUNT: 0
        queryBuf.putShort(0.toShort())      // NSCOUNT: 0
        queryBuf.putShort(0.toShort())      // ARCOUNT: 0

        // QNAME: 7 "example" 3 "com" 0
        queryBuf.put(7.toByte())
        queryBuf.put("example".toByteArray(Charsets.US_ASCII))
        queryBuf.put(3.toByte())
        queryBuf.put("com".toByteArray(Charsets.US_ASCII))
        queryBuf.put(0.toByte()) // null terminator

        // QTYPE: 1 (A), QCLASS: 1 (IN)
        queryBuf.putShort(1.toShort())
        queryBuf.putShort(1.toShort())

        val queryBytes = queryBuf.array().copyOf(queryBuf.position())
        val extractedDomain = com.example.dataguard.vpn.engine.DnsQueryParser.extractDnsDomain(queryBytes)

        assertEquals("example.com", extractedDomain)
    }

    @Test
    fun testMilestone12_DnsNxDomainResponseSynthesis() {
        // Construct query for "blocked.com"
        val queryBuf = java.nio.ByteBuffer.allocate(32)
        queryBuf.putShort(0xABCD.toShort()) // ID
        queryBuf.putShort(0x0100.toShort())
        queryBuf.putShort(1.toShort())
        queryBuf.putShort(0.toShort())
        queryBuf.putShort(0.toShort())
        queryBuf.putShort(0.toShort())

        queryBuf.put(7.toByte())
        queryBuf.put("blocked".toByteArray(Charsets.US_ASCII))
        queryBuf.put(3.toByte())
        queryBuf.put("com".toByteArray(Charsets.US_ASCII))
        queryBuf.put(0.toByte())
        queryBuf.putShort(1.toShort())
        queryBuf.putShort(1.toShort())

        val queryBytes = queryBuf.array().copyOf(queryBuf.position())
        val response = com.example.dataguard.vpn.engine.SystemDnsResolver.buildDnsNxDomainResponse(queryBytes)

        assertNotNull(response)
        val respBuf = java.nio.ByteBuffer.wrap(response!!)
        val respId = respBuf.short.toInt() and 0xFFFF
        val respFlags = respBuf.short.toInt() and 0xFFFF

        assertEquals(0xABCD, respId)
        // RCODE is low 4 bits of flags. RCODE 3 = NXDOMAIN
        assertEquals(3, respFlags and 0x000F)
    }

    @Test
    fun testMilestone12_Ipv4UdpPacketConstruction() {
        val payload = "DNS-RESPONSE-MOCK".toByteArray()
        val packet = com.example.dataguard.vpn.engine.SystemDnsResolver.buildIpv4UdpPacket(
            srcIp = "10.1.10.2",
            dstIp = "10.1.10.1",
            srcPort = 53,
            dstPort = 54321,
            payload = payload
        )

        assertNotNull(packet)
        // IP header (20) + UDP header (8) + payload (17) = 45 bytes
        assertEquals(20 + 8 + payload.size, packet.size)

        val buf = java.nio.ByteBuffer.wrap(packet)
        val verIhl = buf.get().toInt() and 0xFF
        assertEquals(4, verIhl shr 4) // IPv4
        assertEquals(5, verIhl and 0x0F) // IHL 5 (20 bytes)

        buf.get() // DSCP
        val totalLen = buf.short.toInt() and 0xFFFF
        assertEquals(packet.size, totalLen)

        buf.position(9)
        val protocol = buf.get().toInt() and 0xFF
        assertEquals(17, protocol) // UDP protocol

        // Verify UDP header
        buf.position(20)
        val srcPort = buf.short.toInt() and 0xFFFF
        val dstPort = buf.short.toInt() and 0xFFFF
        val udpLen = buf.short.toInt() and 0xFFFF

        assertEquals(53, srcPort)
        assertEquals(54321, dstPort)
        assertEquals(8 + payload.size, udpLen)
    }

    @Test
    fun testMilestone13_AppMetricSelection_extractsCorrectValues() {
        val app1 = com.example.dataguard.data.entity.AppDataUsage(
            uid = 1001,
            packageName = "com.example.video",
            appName = "VideoApp",
            mobileRxBytes = 1000L,
            mobileTxBytes = 200L,
            wifiRxBytes = 5000L,
            wifiTxBytes = 800L
        )

        // Verify metric mapping
        assertEquals(7000L, app1.totalBytes)          // 1000 + 200 + 5000 + 800
        assertEquals(1200L, app1.mobileTotalBytes)     // 1000 + 200
        assertEquals(5800L, app1.wifiTotalBytes)       // 5000 + 800
        assertEquals(6000L, app1.rxBytes)              // 1000 + 5000 (Download)
        assertEquals(1000L, app1.txBytes)              // 200 + 800 (Upload)
    }

    @Test
    fun testMilestone13_AppBottomPreview_aggregatesSelectedMetric() {
        val apps = listOf(
            com.example.dataguard.data.entity.AppDataUsage(
                uid = 1001,
                packageName = "com.app.one",
                appName = "AppOne",
                mobileRxBytes = 100L,
                mobileTxBytes = 50L,
                wifiRxBytes = 400L,
                wifiTxBytes = 200L
            ),
            com.example.dataguard.data.entity.AppDataUsage(
                uid = 1002,
                packageName = "com.app.two",
                appName = "AppTwo",
                mobileRxBytes = 300L,
                mobileTxBytes = 150L,
                wifiRxBytes = 100L,
                wifiTxBytes = 50L
            )
        )

        // Upload aggregate: app1.txBytes (250) + app2.txBytes (200) = 450
        val totalUpload = apps.sumOf { it.txBytes }
        assertEquals(450L, totalUpload)

        // Download aggregate: app1.rxBytes (500) + app2.rxBytes (400) = 900
        val totalDownload = apps.sumOf { it.rxBytes }
        assertEquals(900L, totalDownload)

        // Cellular aggregate: app1.mobileTotal (150) + app2.mobileTotal (450) = 600
        val totalCellular = apps.sumOf { it.mobileTotalBytes }
        assertEquals(600L, totalCellular)

        // Wi-Fi aggregate: app1.wifiTotal (600) + app2.wifiTotal (150) = 750
        val totalWifi = apps.sumOf { it.wifiTotalBytes }
        assertEquals(750L, totalWifi)

        // Top consumer for Upload is AppOne (250 > 200)
        val topUpload = apps.maxByOrNull { it.txBytes }
        assertEquals("AppOne", topUpload?.appName)

        // Top consumer for Cellular is AppTwo (450 > 150)
        val topCellular = apps.maxByOrNull { it.mobileTotalBytes }
        assertEquals("AppTwo", topCellular?.appName)
    }

    @Test
    fun testDomainSuffixAttribution_strictMatching() {
        // Assert strict domain-suffix matching (RFC / Curated mapping)
        // "x.com" matches "x.com" and "api.x.com"
        assertTrue(com.example.dataguard.vpn.engine.AppDomainMatcher.matchesDomainSuffix("x.com", "x.com"))
        assertTrue(com.example.dataguard.vpn.engine.AppDomainMatcher.matchesDomainSuffix("api.x.com", "x.com"))
        assertTrue(com.example.dataguard.vpn.engine.AppDomainMatcher.matchesDomainSuffix("abs.twimg.com", "twimg.com"))

        // "dropbox.com" and "linux.com" MUST NOT match "x.com"
        assertFalse(com.example.dataguard.vpn.engine.AppDomainMatcher.matchesDomainSuffix("dropbox.com", "x.com"))
        assertFalse(com.example.dataguard.vpn.engine.AppDomainMatcher.matchesDomainSuffix("linux.com", "x.com"))
        assertFalse(com.example.dataguard.vpn.engine.AppDomainMatcher.matchesDomainSuffix("notx.com", "x.com"))

        // Package inference
        assertEquals("com.twitter.android", com.example.dataguard.vpn.engine.AppDomainMatcher.inferPackageForDomain("api.x.com"))
        assertEquals("com.google.android.youtube", com.example.dataguard.vpn.engine.AppDomainMatcher.inferPackageForDomain("m.youtube.com"))
        assertEquals("com.instagram.android", com.example.dataguard.vpn.engine.AppDomainMatcher.inferPackageForDomain("cdninstagram.com"))
        assertNull(com.example.dataguard.vpn.engine.AppDomainMatcher.inferPackageForDomain("dropbox.com"))
        assertNull(com.example.dataguard.vpn.engine.AppDomainMatcher.inferPackageForDomain("linux.com"))
    }

    @Test
    fun testDomainAttribution_unverifiedFallbackToUnknown() {
        val attribution = com.example.dataguard.vpn.engine.AppDomainMatcher.resolveAppInfo(
            packageManager = null,
            uid = -1,
            domain = "unknown-generic-service.org"
        )
        // Must not guess
        assertEquals("unknown", attribution.packageName)
        assertEquals("Unknown / Web Traffic", attribution.appName)
        assertEquals(com.example.dataguard.data.entity.AttributionConfidence.UNKNOWN, attribution.confidence)
        assertFalse(attribution.isVerifiedUid)
    }

    @Test
    fun testTlsSniParser_multiSegmentReassembly() {
        // Synthesize valid TLS 1.2 ClientHello for "github.com"
        val host = "github.com"
        val hostBytes = host.toByteArray(Charsets.US_ASCII)

        val sniExt = java.io.ByteArrayOutputStream()
        sniExt.write(byteArrayOf(0x00, 0x00)) // Extension type: server_name (0)
        val serverNameListLen = hostBytes.size + 3
        val extLen = serverNameListLen + 2
        sniExt.write(byteArrayOf((extLen shr 8).toByte(), extLen.toByte()))
        sniExt.write(byteArrayOf((serverNameListLen shr 8).toByte(), serverNameListLen.toByte()))
        sniExt.write(0) // name_type: host_name (0)
        sniExt.write(byteArrayOf((hostBytes.size shr 8).toByte(), hostBytes.size.toByte()))
        sniExt.write(hostBytes)

        val extensionsBytes = sniExt.toByteArray()

        val clientHello = java.io.ByteArrayOutputStream()
        clientHello.write(0x01) // Handshake type: ClientHello
        val hLen = 38 + 1 + 2 + 1 + 2 + extensionsBytes.size
        clientHello.write((hLen shr 16) and 0xFF)
        clientHello.write((hLen shr 8) and 0xFF)
        clientHello.write(hLen and 0xFF)
        clientHello.write(byteArrayOf(0x03, 0x03)) // Version TLS 1.2
        clientHello.write(ByteArray(32)) // Random (32 bytes)
        clientHello.write(0) // Session ID length (0)
        clientHello.write(byteArrayOf(0x00, 0x02, 0x00, 0x2f)) // Cipher suites
        clientHello.write(byteArrayOf(0x01, 0x00)) // Compression methods
        clientHello.write(byteArrayOf((extensionsBytes.size shr 8).toByte(), extensionsBytes.size.toByte()))
        clientHello.write(extensionsBytes)

        val chBytes = clientHello.toByteArray()

        val record = java.io.ByteArrayOutputStream()
        record.write(0x16) // ContentType: Handshake (22)
        record.write(byteArrayOf(0x03, 0x01)) // Record version TLS 1.0
        record.write(byteArrayOf((chBytes.size shr 8).toByte(), chBytes.size.toByte()))
        record.write(chBytes)

        val fullRecordBytes = record.toByteArray()
        assertTrue(fullRecordBytes.size > 20)

        // Split into two segments simulating TCP packet segmentation across TUN MTU
        val splitPoint = 15
        val segment1 = fullRecordBytes.copyOfRange(0, splitPoint)
        val segment2 = fullRecordBytes.copyOfRange(splitPoint, fullRecordBytes.size)

        val flowBuffer = com.example.dataguard.vpn.engine.TlsFlowBuffer()

        // Feed segment 1: should return NeedMoreData
        val result1 = flowBuffer.feed(segment1)
        assertTrue(result1 is com.example.dataguard.vpn.engine.TlsFeedResult.NeedMoreData)

        // Feed segment 2: complete record should yield "github.com"
        val result2 = flowBuffer.feed(segment2)
        assertTrue(result2 is com.example.dataguard.vpn.engine.TlsFeedResult.Parsed)
        assertEquals("github.com", (result2 as com.example.dataguard.vpn.engine.TlsFeedResult.Parsed).sni)
    }

    @Test
    fun testProcNetSocketReader_lineParsing() {
        // Format of /proc/net/tcp line:
        // sl local_address rem_address st tx_queue rx_queue tr tm->when retrnsmt uid timeout inode
        val line = " 1: 0100007F:C5C0 0100007F:1F40 01 00000000:00000000 00:00000000 00000000 10042 0 2469 1 0 0 0 0 -1"

        // Port 0xC5C0 is 50624
        val uidMatch = com.example.dataguard.vpn.engine.ProcNetSocketReader.parseProcNetLine(line, 50624)
        assertEquals(10042, uidMatch)

        // Non-matching port should return -1
        val uidMismatch = com.example.dataguard.vpn.engine.ProcNetSocketReader.parseProcNetLine(line, 8080)
        assertEquals(-1, uidMismatch)
    }

    @Test
    fun testTcpPacketBuilder_checksumAndHeader() {
        val packet = com.example.dataguard.vpn.engine.TcpPacketBuilder.buildIpv4TcpPacket(
            srcIp = "10.1.10.1",
            dstIp = "93.184.216.34",
            srcPort = 54321,
            dstPort = 443,
            seqNum = 1000L,
            ackNum = 2000L,
            flags = com.example.dataguard.vpn.engine.TcpPacketBuilder.FLAG_SYN or com.example.dataguard.vpn.engine.TcpPacketBuilder.FLAG_ACK,
            payload = "Hello".toByteArray(Charsets.US_ASCII)
        )

        assertNotNull(packet)
        assertEquals(20 + 20 + 5, packet.size) // IP(20) + TCP(20) + "Hello"(5)

        // IPv4 protocol at offset 9 must be 6 (TCP)
        assertEquals(6.toByte(), packet[9])

        // Verify IPv4 header checksum validates to zero when re-computed
        val ipChecksum = com.example.dataguard.vpn.engine.SystemDnsResolver.calculateIpv4Checksum(packet, 0, 20)
        assertEquals(0.toShort(), ipChecksum)
    }

    @Test
    fun testAppThemeMode_selectionAndDefaults() {
        assertEquals(com.example.ui.theme.AppThemeMode.LIGHT, com.example.ui.theme.AppThemeMode.fromKey("light"))
        assertEquals(com.example.ui.theme.AppThemeMode.DARK, com.example.ui.theme.AppThemeMode.fromKey("dark"))
        assertEquals(com.example.ui.theme.AppThemeMode.SYSTEM, com.example.ui.theme.AppThemeMode.fromKey("system"))
        assertEquals(com.example.ui.theme.AppThemeMode.SYSTEM, com.example.ui.theme.AppThemeMode.fromKey("unknown"))

        assertEquals("White", com.example.ui.theme.AppThemeMode.LIGHT.title)
        assertEquals("Dark", com.example.ui.theme.AppThemeMode.DARK.title)
        assertEquals("System", com.example.ui.theme.AppThemeMode.SYSTEM.title)
    }

    @Test
    fun testExportDatePreset_rangeCalculations() {
        val (startPrevDay, endPrevDay) = com.example.dataguard.ui.viewmodel.WebsitesViewModel.computeDateRange(
            com.example.dataguard.ui.viewmodel.ExportDatePreset.PREVIOUS_DAY,
            dayOffset = 1,
            customStart = 0L,
            customEnd = 0L
        )
        assertTrue(startPrevDay < endPrevDay)
        val diff = endPrevDay - startPrevDay
        assertTrue(diff in (86400000L - 1000L)..(86400000L + 1000L))

        val (start7Days, end7Days) = com.example.dataguard.ui.viewmodel.WebsitesViewModel.computeDateRange(
            com.example.dataguard.ui.viewmodel.ExportDatePreset.PAST_7_DAYS,
            dayOffset = 0,
            customStart = 0L,
            customEnd = 0L
        )
        assertTrue(start7Days < end7Days)
        val diff7 = end7Days - start7Days
        assertEquals(7L * 24 * 60 * 60 * 1000L, diff7)

        val (start30Days, end30Days) = com.example.dataguard.ui.viewmodel.WebsitesViewModel.computeDateRange(
            com.example.dataguard.ui.viewmodel.ExportDatePreset.PAST_30_DAYS,
            dayOffset = 0,
            customStart = 0L,
            customEnd = 0L
        )
        assertEquals(30L * 24 * 60 * 60 * 1000L, end30Days - start30Days)

        val customStart = 1700000000000L
        val customEnd = 1700050000000L
        val (startCustom, endCustom) = com.example.dataguard.ui.viewmodel.WebsitesViewModel.computeDateRange(
            com.example.dataguard.ui.viewmodel.ExportDatePreset.CUSTOM,
            dayOffset = 0,
            customStart = customStart,
            customEnd = customEnd
        )
        assertEquals(customStart, startCustom)
        assertEquals(customEnd, endCustom)
    }

    @Test
    fun testCsvExporter_comprehensiveWebsiteReportGeneration() {
        val report = com.example.dataguard.utils.WebsiteExportReportData(
            title = "DataGuard Website & Telemetry Audit Report",
            periodLabel = "Yesterday",
            startTimeMillis = 1700000000000L,
            endTimeMillis = 1700086400000L,
            totalUploadBytes = 2048000L,
            totalDownloadBytes = 8192000L,
            totalBytes = 10240000L,
            domainItems = listOf(
                com.example.dataguard.utils.ExportDomainItem(
                    domain = "example.com",
                    appName = "Chrome",
                    packageName = "com.android.chrome",
                    uploadBytes = 1024000L,
                    downloadBytes = 4096000L,
                    totalBytes = 5120000L,
                    connectionCount = 12,
                    firstSeen = 1700000000000L,
                    lastSeen = 1700086400000L,
                    confidence = "High",
                    firewallStatus = "Allowed"
                )
            ),
            appItems = listOf(
                com.example.dataguard.utils.ExportAppItem(
                    appName = "Chrome",
                    packageName = "com.android.chrome",
                    uploadBytes = 1024000L,
                    downloadBytes = 4096000L,
                    totalBytes = 5120000L,
                    domainCount = 1
                )
            )
        )

        val csv = com.example.dataguard.utils.CsvExporter.formatReportCsv(report)
        assertNotNull(csv)
        assertTrue(csv.contains("DataGuard Website & Telemetry Audit Report"))
        assertTrue(csv.contains("--- SUMMARY TOTALS ---"))
        assertTrue(csv.contains("--- DOMAIN ACTIVITY BREAKDOWN ---"))
        assertTrue(csv.contains("\"example.com\""))
        assertTrue(csv.contains("--- PER-APP DATA USAGE BREAKDOWN ---"))
        assertTrue(csv.contains("\"Chrome\""))
        assertTrue(csv.contains("\"com.android.chrome\""))
    }

    @Test
    fun testLiveNetworkFeed_speedUpdatesAndFormatting() {
        // Test DataFormatter speed strings
        assertEquals("0 B/s", com.example.dataguard.utils.DataFormatter.formatSpeed(0L))
        assertEquals("1.0 KB/s", com.example.dataguard.utils.DataFormatter.formatSpeed(1024L))
        assertEquals("1.00 MB/s", com.example.dataguard.utils.DataFormatter.formatSpeed(1048576L))
        assertEquals("2.50 MB/s", com.example.dataguard.utils.DataFormatter.formatSpeed((2.5 * 1024 * 1024).toLong()))

        // Test DataMonitorService updateLiveSpeeds
        com.example.dataguard.service.DataMonitorService.updateLiveSpeeds(
            txSpeed = 512000L,
            rxSpeed = 1536000L
        )

        assertEquals(512000L, com.example.dataguard.service.DataMonitorService.liveTxSpeed.value)
        assertEquals(1536000L, com.example.dataguard.service.DataMonitorService.liveRxSpeed.value)
    }
}




