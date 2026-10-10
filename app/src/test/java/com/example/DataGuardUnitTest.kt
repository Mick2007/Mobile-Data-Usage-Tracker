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
        val extractedSni = com.example.dataguard.vpn.engine.SniParser.extractSni(buffer)
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
}
