package com.example.dataguard.domain.hotspot

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Build
import com.example.dataguard.data.entity.HotspotSessionEntity
import com.example.dataguard.data.repository.DataRepository
import com.example.dataguard.utils.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.NetworkInterface
import java.util.Calendar

/**
 * Hotspot and Tethering status data model.
 */
data class HotspotState(
    val isActive: Boolean = false,
    val sessionType: String = "INACTIVE", // "WIFI_AP", "USB_TETHERING", "BLUETOOTH_TETHERING", "INACTIVE"
    val activeInterfaces: List<String> = emptyList(),
    val stateReason: String = "Hotspot is inactive"
)

data class TetheringTrafficSummary(
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isFromNetworkStatsManager: Boolean = true
)

/**
 * Hotspot and Tethering Monitor.
 *
 * TECHNICAL REALITY:
 * 1. Android kernel routes tethering packets between the client interface (e.g., ap0, rndis0)
 *    and the upstream WAN interface (e.g., rmnet_data0) via kernel NAT/ip_forward.
 * 2. NetworkStatsManager aggregates all tethering traffic under UID_TETHERING (-5).
 * 3. Individual client attribution (per-connected-device MAC/IP usage) is NOT exposed to
 *    third-party non-root applications. On Android 10+ (API 29+), /proc/net/arp is also restricted.
 * 4. This class accurately measures total tethering traffic, sessions, and status without
 *    fabricating client data, clearly indicating "Tethering traffic".
 */
class HotspotMonitor(
    private val context: Context,
    private val repository: DataRepository,
    private val permissionHelper: PermissionHelper,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager

    private val _hotspotState = MutableStateFlow(HotspotState())
    val hotspotState: StateFlow<HotspotState> = _hotspotState.asStateFlow()

    private val _activeSession = MutableStateFlow<HotspotSessionEntity?>(null)
    val activeSession: StateFlow<HotspotSessionEntity?> = _activeSession.asStateFlow()

    private val sessionMutex = Mutex()
    private var isReceiverRegistered = false

    companion object {
        const val UID_TETHERING = -5 // android.net.TrafficStats.UID_TETHERING
        const val WIFI_AP_STATE_DISABLING = 10
        const val WIFI_AP_STATE_DISABLED = 11
        const val WIFI_AP_STATE_ENABLING = 12
        const val WIFI_AP_STATE_ENABLED = 13
        const val WIFI_AP_STATE_FAILED = 14
        const val ACTION_WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
        const val EXTRA_WIFI_AP_STATE = "wifi_state"

        const val ANDROID_LIMITATION_EXPLANATION =
            "Android Platform Architecture: The Android OS aggregates all forwarded Wi-Fi hotspot and " +
            "tethering bandwidth at the kernel level under system Tethering UID (-5). Individual client MAC/IP " +
            "data attribution is technically not exposed by Android public APIs to non-root applications. " +
            "All metrics represent actual network data routed through this device."
    }

    private val apStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == ACTION_WIFI_AP_STATE_CHANGED) {
                scope.launch {
                    syncHotspotStatus()
                }
            }
        }
    }

    init {
        registerBroadcastReceiver()
        scope.launch {
            // Restore any active session from DB on start
            val currentInDb = repository.getActiveHotspotSession()
            _activeSession.value = currentInDb
            syncHotspotStatus()
        }
    }

    fun registerBroadcastReceiver() {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter(ACTION_WIFI_AP_STATE_CHANGED)
                context.registerReceiver(apStateReceiver, filter)
                isReceiverRegistered = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun unregisterBroadcastReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(apStateReceiver)
                isReceiverRegistered = false
            } catch (_: Exception) {}
        }
    }

    /**
     * Inspects actual device network interfaces and Wi-Fi AP state to detect active tethering.
     */
    fun detectCurrentState(): HotspotState {
        var isWifiApActive = false
        val activeIfaces = mutableListOf<String>()
        var primaryType = "INACTIVE"
        var reason = "No active tethering detected"

        // 1. Inspect Wi-Fi AP via WifiManager reflection
        try {
            val isApMethod = wifiManager?.javaClass?.getDeclaredMethod("isWifiApEnabled")
            isApMethod?.isAccessible = true
            val isEnabled = isApMethod?.invoke(wifiManager) as? Boolean
            if (isEnabled == true) {
                isWifiApActive = true
            }
        } catch (_: Exception) {}

        // 2. Check getWifiApState reflection
        try {
            val getApStateMethod = wifiManager?.javaClass?.getDeclaredMethod("getWifiApState")
            getApStateMethod?.isAccessible = true
            val state = getApStateMethod?.invoke(wifiManager) as? Int
            if (state == WIFI_AP_STATE_ENABLED) {
                isWifiApActive = true
            }
        } catch (_: Exception) {}

        // 3. Inspect hardware Network Interfaces
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isUp && !iface.isLoopback) {
                    val name = iface.name.lowercase()
                    when {
                        name.startsWith("ap") || name.startsWith("softap") || name.startsWith("swlan") || name == "wlan1" -> {
                            isWifiApActive = true
                            activeIfaces.add(iface.name)
                            if (primaryType == "INACTIVE") primaryType = "WIFI_AP"
                        }
                        name.startsWith("rndis") || name.startsWith("ncm") || (name.startsWith("usb") && !name.contains("tun")) -> {
                            activeIfaces.add(iface.name)
                            if (primaryType == "INACTIVE") primaryType = "USB_TETHERING"
                        }
                        name.startsWith("bt-pan") || name.startsWith("pan") -> {
                            activeIfaces.add(iface.name)
                            if (primaryType == "INACTIVE") primaryType = "BLUETOOTH_TETHERING"
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 4. ConnectivityManager tethered interfaces inspection
        try {
            val getTetheredMethod = connectivityManager?.javaClass?.getDeclaredMethod("getTetheredIfaces")
            getTetheredMethod?.isAccessible = true
            val tethered = getTetheredMethod?.invoke(connectivityManager) as? Array<*>
            if (tethered != null && tethered.isNotEmpty()) {
                for (item in tethered) {
                    val ifaceName = item.toString()
                    if (!activeIfaces.contains(ifaceName)) {
                        activeIfaces.add(ifaceName)
                    }
                }
                if (primaryType == "INACTIVE") {
                    primaryType = "TETHERING"
                }
            }
        } catch (_: Exception) {}

        val isActive = isWifiApActive || activeIfaces.isNotEmpty()
        if (isActive) {
            if (primaryType == "INACTIVE") {
                primaryType = if (isWifiApActive) "WIFI_AP" else "TETHERING"
            }
            reason = when (primaryType) {
                "WIFI_AP" -> "Wi-Fi Hotspot is active (${activeIfaces.joinToString().ifEmpty { "ap0" }})"
                "USB_TETHERING" -> "USB Tethering is active (${activeIfaces.joinToString()})"
                "BLUETOOTH_TETHERING" -> "Bluetooth Tethering is active (${activeIfaces.joinToString()})"
                else -> "Tethering is active (${activeIfaces.joinToString()})"
            }
        }

        return HotspotState(
            isActive = isActive,
            sessionType = if (isActive) primaryType else "INACTIVE",
            activeInterfaces = activeIfaces,
            stateReason = reason
        )
    }

    /**
     * Synchronizes hotspot state with the active database session.
     * Starts a new session on transition to active, updates on heartbeat, closes on transition to inactive.
     */
    suspend fun syncHotspotStatus(): HotspotState = sessionMutex.withLock {
        val currentState = detectCurrentState()
        _hotspotState.value = currentState

        val now = System.currentTimeMillis()
        val currentSession = repository.getActiveHotspotSession()

        if (currentState.isActive) {
            if (currentSession == null) {
                // Hotspot just turned ON -> Start a new session
                val newSession = HotspotSessionEntity(
                    startTime = now,
                    endTime = null,
                    isActive = true,
                    uploadBytes = 0L,
                    downloadBytes = 0L,
                    totalBytes = 0L,
                    sessionType = currentState.sessionType,
                    clientAttributionState = "Tethering traffic",
                    attributionExplanation = ANDROID_LIMITATION_EXPLANATION,
                    interfaceName = currentState.activeInterfaces.firstOrNull() ?: "ap0",
                    lastUpdated = now
                )
                val newId = repository.insertHotspotSession(newSession)
                val savedSession = newSession.copy(id = newId)
                _activeSession.value = savedSession
            } else {
                // Hotspot remains ON -> Query updated bandwidth for this session
                val stats = queryTetheringUsage(currentSession.startTime, now)
                val updatedSession = currentSession.copy(
                    uploadBytes = maxOf(currentSession.uploadBytes, stats.uploadBytes),
                    downloadBytes = maxOf(currentSession.downloadBytes, stats.downloadBytes),
                    totalBytes = maxOf(currentSession.totalBytes, stats.totalBytes),
                    sessionType = currentState.sessionType,
                    lastUpdated = now
                )
                repository.updateHotspotSession(updatedSession)
                _activeSession.value = updatedSession
            }
        } else {
            if (currentSession != null) {
                // Hotspot just turned OFF -> Close active session
                val finalStats = queryTetheringUsage(currentSession.startTime, now)
                val closedSession = currentSession.copy(
                    endTime = now,
                    isActive = false,
                    uploadBytes = maxOf(currentSession.uploadBytes, finalStats.uploadBytes),
                    downloadBytes = maxOf(currentSession.downloadBytes, finalStats.downloadBytes),
                    totalBytes = maxOf(currentSession.totalBytes, finalStats.totalBytes),
                    lastUpdated = now
                )
                repository.updateHotspotSession(closedSession)
                _activeSession.value = null
            }
        }

        return@withLock currentState
    }

    /**
     * Queries actual tethering data consumption from NetworkStatsManager (UID_TETHERING = -5).
     */
    fun queryTetheringUsage(startTime: Long, endTime: Long): TetheringTrafficSummary {
        val nsm = networkStatsManager
        val hasPermission = permissionHelper.hasPackageUsageStatsPermission()

        val validStart = minOf(startTime, endTime)
        val validEnd = maxOf(startTime, endTime).let { if (it <= validStart) validStart + 1000L else it }

        var tx = 0L
        var rx = 0L
        var matchedNsm = false

        if (hasPermission && nsm != null) {
            // 1. Mobile interface query for UID -5
            try {
                val mobileSummary = nsm.querySummary(
                    ConnectivityManager.TYPE_MOBILE,
                    null,
                    validStart,
                    validEnd
                )
                val bucket = NetworkStats.Bucket()
                while (mobileSummary.hasNextBucket()) {
                    mobileSummary.getNextBucket(bucket)
                    if (bucket.uid == UID_TETHERING) {
                        tx += bucket.txBytes.coerceAtLeast(0L)
                        rx += bucket.rxBytes.coerceAtLeast(0L)
                        matchedNsm = true
                    }
                }
                mobileSummary.close()
            } catch (_: Exception) {}

            // 2. Wi-Fi interface query for UID -5 (Wi-Fi sharing / USB tethering)
            try {
                val wifiSummary = nsm.querySummary(
                    ConnectivityManager.TYPE_WIFI,
                    null,
                    validStart,
                    validEnd
                )
                val bucket = NetworkStats.Bucket()
                while (wifiSummary.hasNextBucket()) {
                    wifiSummary.getNextBucket(bucket)
                    if (bucket.uid == UID_TETHERING) {
                        tx += bucket.txBytes.coerceAtLeast(0L)
                        rx += bucket.rxBytes.coerceAtLeast(0L)
                        matchedNsm = true
                    }
                }
                wifiSummary.close()
            } catch (_: Exception) {}

            // 3. Fallback to queryDetailsForUid if summary didn't find UID -5
            if (!matchedNsm) {
                try {
                    val mobileDetails = nsm.queryDetailsForUid(
                        ConnectivityManager.TYPE_MOBILE,
                        null,
                        validStart,
                        validEnd,
                        UID_TETHERING
                    )
                    val bucket = NetworkStats.Bucket()
                    while (mobileDetails.hasNextBucket()) {
                        mobileDetails.getNextBucket(bucket)
                        tx += bucket.txBytes.coerceAtLeast(0L)
                        rx += bucket.rxBytes.coerceAtLeast(0L)
                        matchedNsm = true
                    }
                    mobileDetails.close()
                } catch (_: Exception) {}
            }
        }

        return TetheringTrafficSummary(
            uploadBytes = tx,
            downloadBytes = rx,
            totalBytes = tx + rx,
            isFromNetworkStatsManager = matchedNsm
        )
    }

    /**
     * Calculates start of today (midnight) in ms.
     */
    fun getTodayStartTimestamp(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * Calculates start of current billing cycle (defaults to day 1 of current month).
     */
    fun getBillingCycleStartTimestamp(cycleStartDay: Int = 1): Long {
        val cal = Calendar.getInstance().apply {
            val currentDay = get(Calendar.DAY_OF_MONTH)
            if (currentDay < cycleStartDay) {
                add(Calendar.MONTH, -1)
            }
            val maxDays = getActualMaximum(Calendar.DAY_OF_MONTH)
            set(Calendar.DAY_OF_MONTH, cycleStartDay.coerceIn(1, maxDays))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * Queries total tethering usage for today, combining active session, recorded sessions,
     * and NetworkStatsManager hardware counters.
     */
    suspend fun getTodayTetheringUsage(): TetheringTrafficSummary {
        val todayStart = getTodayStartTimestamp()
        val now = System.currentTimeMillis()

        // 1. Check NetworkStatsManager directly
        val nsmStats = queryTetheringUsage(todayStart, now)

        // 2. Query sessions in DB since midnight
        val sessions = repository.getHotspotSessionsSince(todayStart)
        val sessionTx = sessions.sumOf { it.uploadBytes }
        val sessionRx = sessions.sumOf { it.downloadBytes }

        // Use the maximum of hardware counters and recorded sessions to guarantee accuracy
        val totalTx = maxOf(nsmStats.uploadBytes, sessionTx)
        val totalRx = maxOf(nsmStats.downloadBytes, sessionRx)

        return TetheringTrafficSummary(
            uploadBytes = totalTx,
            downloadBytes = totalRx,
            totalBytes = totalTx + totalRx,
            isFromNetworkStatsManager = nsmStats.isFromNetworkStatsManager
        )
    }

    /**
     * Queries total tethering usage for the current billing cycle.
     */
    suspend fun getBillingCycleTetheringUsage(cycleStartDay: Int = 1): TetheringTrafficSummary {
        val cycleStart = getBillingCycleStartTimestamp(cycleStartDay)
        val now = System.currentTimeMillis()

        // 1. Check NetworkStatsManager directly
        val nsmStats = queryTetheringUsage(cycleStart, now)

        // 2. Query sessions in DB since cycle start
        val sessions = repository.getHotspotSessionsSince(cycleStart)
        val sessionTx = sessions.sumOf { it.uploadBytes }
        val sessionRx = sessions.sumOf { it.downloadBytes }

        val totalTx = maxOf(nsmStats.uploadBytes, sessionTx)
        val totalRx = maxOf(nsmStats.downloadBytes, sessionRx)

        return TetheringTrafficSummary(
            uploadBytes = totalTx,
            downloadBytes = totalRx,
            totalBytes = totalTx + totalRx,
            isFromNetworkStatsManager = nsmStats.isFromNetworkStatsManager
        )
    }

    /**
     * Record a completed or test tethering session (useful for tests and manual logs).
     */
    suspend fun recordSession(
        startTime: Long,
        endTime: Long,
        uploadBytes: Long,
        downloadBytes: Long,
        sessionType: String = "WIFI_AP"
    ): Long {
        val entity = HotspotSessionEntity(
            startTime = startTime,
            endTime = endTime,
            isActive = false,
            uploadBytes = uploadBytes,
            downloadBytes = downloadBytes,
            totalBytes = uploadBytes + downloadBytes,
            sessionType = sessionType,
            clientAttributionState = "Tethering traffic",
            attributionExplanation = ANDROID_LIMITATION_EXPLANATION,
            interfaceName = "ap0",
            lastUpdated = endTime
        )
        return repository.insertHotspotSession(entity)
    }
}
