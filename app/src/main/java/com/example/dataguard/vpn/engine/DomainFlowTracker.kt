package com.example.dataguard.vpn.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.dataguard.data.entity.AttributionConfidence
import com.example.dataguard.data.entity.NetworkFlowEntity
import com.example.dataguard.data.repository.DataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

data class RawFlowObservation(
    val domain: String?,
    val destinationIp: String,
    val protocol: String,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val networkType: String,
    val uid: Int = -1,
    val timestamp: Long = System.currentTimeMillis()
)

class DomainFlowTracker(
    private val context: Context,
    private val repository: DataRepository?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val packageManager: PackageManager = context.packageManager

    // Cache of IP -> Observed Domain (from DNS query or SNI) with TTL
    private val ipToDomainMap = ConcurrentHashMap<String, Pair<String, Long>>() // IP -> (domain, expireTimestamp)

    // Flow key -> In-memory delta aggregation
    private data class FlowDelta(
        val domain: String,
        val destinationIp: String,
        val protocol: String,
        val networkType: String,
        val uid: Int,
        val packageName: String,
        val appName: String,
        val uploadDelta: Long,
        val downloadDelta: Long,
        val confidence: AttributionConfidence,
        val timestamp: Long
    )

    private val pendingDeltas = ConcurrentHashMap<String, FlowDelta>()

    private val _liveObservationFlow = MutableSharedFlow<NetworkFlowEntity>(extraBufferCapacity = 64)
    val liveObservationFlow: SharedFlow<NetworkFlowEntity> = _liveObservationFlow.asSharedFlow()

    init {
        // Periodic batch persistence loop (every 3 seconds)
        scope.launch {
            while (isActive) {
                delay(3000)
                flushPendingDeltas()
                cleanupExpiredDnsCache()
            }
        }
    }

    /**
     * Records a DNS query observation (RFC 1035 UDP 53).
     */
    fun recordDnsQuery(queriedDomain: String, clientIp: String) {
        val cleanDomain = queriedDomain.trim().lowercase()
        if (cleanDomain.isEmpty()) return

        // Register domain observation
        recordObservation(
            RawFlowObservation(
                domain = cleanDomain,
                destinationIp = "DNS-Query",
                protocol = "DNS",
                uploadBytes = 64L,
                downloadBytes = 0L,
                networkType = "ANY"
            ),
            forcedConfidence = AttributionConfidence.HIGH
        )
    }

    /**
     * Maps an IP address to a known domain (e.g. from DNS responses or reverse lookups).
     */
    fun registerIpDomainMapping(ip: String, domain: String, ttlMillis: Long = 300_000L) {
        if (ip.isNotEmpty() && domain.isNotEmpty()) {
            ipToDomainMap[ip] = Pair(domain.trim().lowercase(), System.currentTimeMillis() + ttlMillis)
        }
    }

    /**
     * Ingests packet telemetry observation from TUN processor.
     */
    fun recordObservation(observation: RawFlowObservation, forcedConfidence: AttributionConfidence? = null) {
        try {
            val destIp = observation.destinationIp

            // 1. Determine Domain and Confidence Level
            val (resolvedDomain, confidence) = when {
                !observation.domain.isNullOrBlank() -> {
                    Pair(observation.domain.trim().lowercase(), forcedConfidence ?: AttributionConfidence.HIGH)
                }
                ipToDomainMap.containsKey(destIp) -> {
                    val mapped = ipToDomainMap[destIp]
                    if (mapped != null && mapped.second > System.currentTimeMillis()) {
                        Pair(mapped.first, forcedConfidence ?: AttributionConfidence.MEDIUM)
                    } else {
                        Pair("Unknown / IP-only ($destIp)", AttributionConfidence.LOW)
                    }
                }
                destIp.isNotEmpty() && destIp != "unknown" -> {
                    Pair("Unknown / IP-only ($destIp)", AttributionConfidence.LOW)
                }
                else -> {
                    Pair("Unknown / Unattributed traffic", AttributionConfidence.UNKNOWN)
                }
            }

            // Cache mapping if domain was detected
            if (!observation.domain.isNullOrBlank() && destIp.isNotEmpty() && destIp != "unknown") {
                registerIpDomainMapping(destIp, observation.domain)
            }

            // 2. Attribute Application / Package Name
            val (pkgName, appName) = resolveAppInfo(observation.uid, resolvedDomain)

            // 3. Compose aggregation flow key
            val flowKey = "$resolvedDomain::$pkgName::${observation.protocol}::${observation.networkType}"

            val delta = FlowDelta(
                domain = resolvedDomain,
                destinationIp = destIp,
                protocol = observation.protocol,
                networkType = observation.networkType,
                uid = observation.uid,
                packageName = pkgName,
                appName = appName,
                uploadDelta = observation.uploadBytes,
                downloadDelta = observation.downloadBytes,
                confidence = confidence,
                timestamp = observation.timestamp
            )

            pendingDeltas.compute(flowKey) { _, existing ->
                if (existing == null) {
                    delta
                } else {
                    existing.copy(
                        uploadDelta = existing.uploadDelta + delta.uploadDelta,
                        downloadDelta = existing.downloadDelta + delta.downloadDelta,
                        destinationIp = if (delta.destinationIp.isNotEmpty()) delta.destinationIp else existing.destinationIp,
                        timestamp = maxOf(existing.timestamp, delta.timestamp)
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error processing flow observation: ${e.message}")
        }
    }

    /**
     * Resolves Application UID / package name with heuristics for known services.
     */
    private fun resolveAppInfo(uid: Int, domain: String): Pair<String, String> {
        if (uid > 0) {
            val packages = try {
                packageManager.getPackagesForUid(uid)
            } catch (_: Exception) {
                null
            }

            if (!packages.isNullOrEmpty()) {
                val pkg = packages[0]
                val appName = try {
                    val info = packageManager.getApplicationInfo(pkg, 0)
                    packageManager.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                    pkg
                }
                return Pair(pkg, appName)
            }
        }

        // Domain heuristic association for prominent services
        val lowerDomain = domain.lowercase()
        return when {
            lowerDomain.contains("youtube") || lowerDomain.contains("googlevideo") ->
                Pair("com.google.android.youtube", "YouTube")
            lowerDomain.contains("instagram") || lowerDomain.contains("cdninstagram") ->
                Pair("com.instagram.android", "Instagram")
            lowerDomain.contains("whatsapp") ->
                Pair("com.whatsapp", "WhatsApp")
            lowerDomain.contains("google") || lowerDomain.contains("gstatic") || lowerDomain.contains("googleapis") ->
                Pair("com.google.android.googlequicksearchbox", "Google")
            lowerDomain.contains("facebook") || lowerDomain.contains("fbcdn") ->
                Pair("com.facebook.katana", "Facebook")
            lowerDomain.contains("netflix") || lowerDomain.contains("nflxvideo") ->
                Pair("com.netflix.mediaclient", "Netflix")
            lowerDomain.contains("spotify") || lowerDomain.contains("scdn.co") ->
                Pair("com.spotify.music", "Spotify")
            else ->
                Pair("unknown", "System / Network Stack")
        }
    }

    private suspend fun flushPendingDeltas() {
        if (pendingDeltas.isEmpty()) return

        val repo = repository ?: return
        val snapshot = HashMap(pendingDeltas)
        pendingDeltas.clear()

        for ((flowKey, delta) in snapshot) {
            try {
                repo.upsertTrafficFlow(
                    flowKey = flowKey,
                    domain = delta.domain,
                    uid = delta.uid,
                    packageName = delta.packageName,
                    appName = delta.appName,
                    destinationIp = delta.destinationIp,
                    protocol = delta.protocol,
                    uploadDelta = delta.uploadDelta,
                    downloadDelta = delta.downloadDelta,
                    networkType = delta.networkType,
                    confidence = delta.confidence.name,
                    timestamp = delta.timestamp
                )

                // Emit live observation for UI telemetry
                _liveObservationFlow.tryEmit(
                    NetworkFlowEntity(
                        flowKey = flowKey,
                        domain = delta.domain,
                        uid = delta.uid,
                        packageName = delta.packageName,
                        appName = delta.appName,
                        destinationIp = delta.destinationIp,
                        protocol = delta.protocol,
                        uploadBytes = delta.uploadDelta,
                        downloadBytes = delta.downloadDelta,
                        totalBytes = delta.uploadDelta + delta.downloadDelta,
                        connectionCount = 1,
                        networkType = delta.networkType,
                        firstSeen = delta.timestamp,
                        lastSeen = delta.timestamp,
                        confidence = delta.confidence.name
                    )
                )

                // Milestone 10: Trigger alert evaluations for newly observed apps/domains and thresholds
                try {
                    com.example.dataguard.DataGuardApp.instance.let { app ->
                        if (delta.packageName != "unknown" && delta.appName != "Unknown") {
                            app.dataPlanAlertManager.notifyNewAppObserved(delta.appName, delta.packageName, delta.timestamp)
                            app.dataPlanAlertManager.checkAppExceedsMb(delta.appName, delta.packageName, delta.uploadDelta + delta.downloadDelta, delta.timestamp)
                        }
                        if (!delta.domain.startsWith("Unknown")) {
                            app.dataPlanAlertManager.notifyNewDomainObserved(delta.domain, delta.appName, delta.timestamp)
                            app.dataPlanAlertManager.checkDomainExceedsMb(delta.domain, delta.uploadDelta + delta.downloadDelta, delta.timestamp)
                        }
                    }
                } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.w(TAG, "Error upserting traffic flow: ${e.message}")
            }
        }
    }

    private fun cleanupExpiredDnsCache() {
        val now = System.currentTimeMillis()
        val iterator = ipToDomainMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.second < now) {
                iterator.remove()
            }
        }
    }

    fun clear() {
        pendingDeltas.clear()
        ipToDomainMap.clear()
    }

    companion object {
        private const val TAG = "DomainFlowTracker"
    }
}
