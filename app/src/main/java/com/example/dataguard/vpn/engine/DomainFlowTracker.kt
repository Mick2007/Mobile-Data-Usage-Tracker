package com.example.dataguard.vpn.engine

import android.content.Context
import android.content.pm.PackageManager
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
import java.util.Collections
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

data class DomainMappingEntry(
    val domain: String,
    val expireTimestamp: Long
)

class DomainFlowTracker(
    private val context: Context,
    private val repository: DataRepository?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val packageManager: PackageManager = context.packageManager

    // Cache of IP -> Set of Observed Domains (supports multiple domains on shared CDN IPs) with DNS TTL
    private val ipToDomainMap = ConcurrentHashMap<String, MutableSet<DomainMappingEntry>>()

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
    fun recordDnsQuery(
        queriedDomain: String,
        clientIp: String,
        uploadBytes: Long = 64L,
        downloadBytes: Long = 128L
    ) {
        val cleanDomain = queriedDomain.trim().lowercase()
        if (cleanDomain.isEmpty()) return

        // Register domain observation
        recordObservation(
            RawFlowObservation(
                domain = cleanDomain,
                destinationIp = "DNS-Query",
                protocol = "DNS",
                uploadBytes = uploadBytes,
                downloadBytes = downloadBytes,
                networkType = "ANY"
            ),
            forcedConfidence = AttributionConfidence.HIGH
        )
    }

    /**
     * Maps an IP address to a known domain honoring authoritative DNS TTL (in seconds).
     * Multi-domain set per IP prevents "last writer wins" corruption on shared CDN IPs.
     */
    fun registerIpDomainMapping(ip: String, domain: String, ttlSeconds: Long = 300L) {
        if (ip.isBlank() || domain.isBlank()) return
        val cleanDomain = domain.trim().lowercase()
        val ttlMillis = ttlSeconds.coerceIn(30L, 86400L) * 1000L
        val expireTime = System.currentTimeMillis() + ttlMillis
        val entry = DomainMappingEntry(cleanDomain, expireTime)

        ipToDomainMap.compute(ip) { _, existingSet ->
            val set = existingSet ?: Collections.synchronizedSet(mutableSetOf<DomainMappingEntry>())
            // Remove previous entry for the exact same domain if present to update TTL
            set.removeIf { it.domain == cleanDomain }
            set.add(entry)
            set
        }
    }

    /**
     * Returns all active, unexpired domains associated with an IP.
     */
    fun getDomainsForIp(ip: String): Set<String> {
        val now = System.currentTimeMillis()
        val entries = ipToDomainMap[ip] ?: return emptySet()
        synchronized(entries) {
            return entries.filter { it.expireTimestamp > now }.map { it.domain }.toSet()
        }
    }

    /**
     * Returns the primary (most recently updated active) domain for an IP, or null.
     */
    fun getPrimaryDomainForIp(ip: String): String? {
        val now = System.currentTimeMillis()
        val entries = ipToDomainMap[ip] ?: return null
        synchronized(entries) {
            return entries.filter { it.expireTimestamp > now }
                .maxByOrNull { it.expireTimestamp }
                ?.domain
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
                destIp.isNotEmpty() && destIp != "unknown" -> {
                    val primaryDomain = getPrimaryDomainForIp(destIp)
                    if (primaryDomain != null) {
                        Pair(primaryDomain, forcedConfidence ?: AttributionConfidence.MEDIUM)
                    } else {
                        Pair("Unknown / IP-only ($destIp)", AttributionConfidence.LOW)
                    }
                }
                else -> {
                    Pair("Unknown / Unattributed traffic", AttributionConfidence.UNKNOWN)
                }
            }

            // Cache mapping if domain was detected
            if (!observation.domain.isNullOrBlank() && destIp.isNotEmpty() && destIp != "unknown" && destIp != "DNS-Query") {
                registerIpDomainMapping(destIp, observation.domain)
            }

            // 2. Strict Application Attribution (Consolidated AppDomainMatcher)
            val attribution = AppDomainMatcher.resolveAppInfo(packageManager, observation.uid, resolvedDomain)
            val finalConfidence = forcedConfidence ?: if (attribution.isVerifiedUid) {
                AttributionConfidence.HIGH
            } else if (confidence == AttributionConfidence.HIGH) {
                AttributionConfidence.HIGH
            } else {
                attribution.confidence
            }

            // 3. Compose aggregation flow key
            val flowKey = "$resolvedDomain::${attribution.packageName}::${observation.protocol}::${observation.networkType}"

            val delta = FlowDelta(
                domain = resolvedDomain,
                destinationIp = destIp,
                protocol = observation.protocol,
                networkType = observation.networkType,
                uid = observation.uid,
                packageName = attribution.packageName,
                appName = attribution.appName,
                uploadDelta = observation.uploadBytes,
                downloadDelta = observation.downloadBytes,
                confidence = finalConfidence,
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
     * Atomically flushes pending deltas to Room repository without dropping observations.
     * Uses atomic per-key removal instead of copy-then-clear to ensure zero lost updates.
     */
    suspend fun flushPendingDeltas() {
        if (pendingDeltas.isEmpty()) return

        val repo = repository ?: return
        val keys = pendingDeltas.keys()

        for (flowKey in keys) {
            val delta = pendingDeltas.remove(flowKey) ?: continue
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

                // Trigger alert evaluations for newly observed apps/domains and thresholds
                try {
                    com.example.dataguard.DataGuardApp.instance.let { app ->
                        if (delta.packageName != "unknown" && delta.appName != "Unknown / Web Traffic") {
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
            val set = entry.value
            synchronized(set) {
                set.removeIf { it.expireTimestamp < now }
                if (set.isEmpty()) {
                    iterator.remove()
                }
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
