package com.example.dataguard.vpn.engine

import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.dataguard.data.entity.AttributionConfidence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.IOException
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Basic representation of parsed IP packet metadata.
 */
data class PacketMetadata(
    val version: Int,
    val protocol: Int, // 6: TCP, 17: UDP, etc.
    val sourceAddress: String,
    val destinationAddress: String,
    val packetLength: Int,
    val headerLength: Int
)

/**
 * Processes packets from the TUN virtual interface.
 * Implements non-blocking, safe packet processing with robust error handling
 * and graceful cancellation to prevent resource leaks.
 *
 * Inspects TECHNICAL METADATA ONLY:
 * - DNS query names (UDP 53)
 * - TLS SNI hostnames (TCP 443 ClientHello)
 * - Destination IP & port
 * NEVER decrypts TLS, intercepts certificates, or inspects HTTP bodies/cookies.
 */
class PacketProcessor(
    private val accountingEngine: TrafficAccountingEngine,
    private val firewallEngine: FirewallEngine,
    private val flowTracker: DomainFlowTracker? = null
) {

    private var workerJob: Job? = null
    @Volatile
    private var isRunning = false

    fun start(pfd: ParcelFileDescriptor, scope: CoroutineScope) {
        if (isRunning) {
            Log.w(TAG, "PacketProcessor already running")
            return
        }

        isRunning = true
        workerJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteBuffer.allocate(32767)
            val inputStream = FileInputStream(pfd.fileDescriptor)

            try {
                Log.i(TAG, "Packet processing loop started with domain & SNI inspection")
                while (isActive && isRunning) {
                    buffer.clear()
                    val bytesRead = try {
                        inputStream.read(buffer.array())
                    } catch (e: IOException) {
                        if (isRunning) {
                            Log.e(TAG, "IOException in packet loop: ${e.message}")
                        }
                        break
                    }

                    if (bytesRead <= 0) {
                        Thread.sleep(10)
                        continue
                    }

                    buffer.limit(bytesRead)
                    val metadata = parseIPv4Header(buffer)
                    if (metadata != null) {
                        accountingEngine.recordPacketIn(bytesRead.toLong())

                        // Extract technical domain / SNI / DNS metadata safely without decrypting
                        inspectTransportPayload(buffer, metadata, bytesRead.toLong())
                    }
                }
            } catch (t: Throwable) {
                if (isRunning) {
                    Log.e(TAG, "Unexpected error in PacketProcessor loop: ${t.message}", t)
                }
            } finally {
                isRunning = false
                Log.i(TAG, "Packet processing loop terminated cleanly")
            }
        }
    }

    fun stop() {
        isRunning = false
        workerJob?.cancel()
        workerJob = null
    }

    /**
     * Inspects transport layer payload (DNS UDP 53 or TLS ClientHello TCP 443).
     */
    fun inspectTransportPayload(buffer: ByteBuffer, ipMeta: PacketMetadata, totalBytes: Long) {
        val tracker = flowTracker ?: return

        try {
            // Position buffer after IPv4 header
            val ipHeaderLen = ipMeta.headerLength
            if (buffer.limit() <= ipHeaderLen) return

            buffer.position(ipHeaderLen)

            when (ipMeta.protocol) {
                // UDP (17) -> Check DNS query on port 53
                17 -> {
                    if (buffer.remaining() >= 8) {
                        val srcPort = buffer.short.toInt() and 0xFFFF
                        val dstPort = buffer.short.toInt() and 0xFFFF
                        val udpLen = buffer.short.toInt() and 0xFFFF
                        val udpChecksum = buffer.short

                        if (dstPort == 53 && buffer.hasRemaining()) {
                            // Inspect DNS question without decrypting or logging user data
                            val dnsDomain = DnsQueryParser.extractDnsDomain(buffer)
                            if (dnsDomain != null) {
                                val eval = firewallEngine.evaluateConnection(
                                    domain = dnsDomain,
                                    packageName = null,
                                    networkType = NetworkInterfaceType.ANY
                                )
                                tracker.recordDnsQuery(dnsDomain, ipMeta.sourceAddress)
                                return
                            }
                        }

                        // Record UDP flow
                        tracker.recordObservation(
                            RawFlowObservation(
                                domain = null,
                                destinationIp = ipMeta.destinationAddress,
                                protocol = "UDP",
                                uploadBytes = totalBytes,
                                downloadBytes = 0L,
                                networkType = "ANY"
                            ),
                            forcedConfidence = AttributionConfidence.LOW
                        )
                    }
                }

                // TCP (6) -> Check TLS SNI on port 443
                6 -> {
                    if (buffer.remaining() >= 20) {
                        val srcPort = buffer.short.toInt() and 0xFFFF
                        val dstPort = buffer.short.toInt() and 0xFFFF
                        val seqNum = buffer.int
                        val ackNum = buffer.int
                        val dataOffsetAndFlags = buffer.short.toInt() and 0xFFFF
                        val dataOffset = ((dataOffsetAndFlags shr 12) and 0x0F) * 4

                        // Advance past TCP header into TLS ClientHello payload if present
                        val tcpPayloadStart = ipHeaderLen + dataOffset
                        if (tcpPayloadStart < buffer.limit()) {
                            buffer.position(tcpPayloadStart)

                            var observedDomain: String? = null
                            if (dstPort == 443 || srcPort == 443) {
                                observedDomain = SniParser.extractSni(buffer)
                            }

                            if (observedDomain != null) {
                                val eval = firewallEngine.evaluateConnection(
                                    domain = observedDomain,
                                    packageName = null,
                                    networkType = NetworkInterfaceType.ANY
                                )
                            }

                            tracker.recordObservation(
                                RawFlowObservation(
                                    domain = observedDomain,
                                    destinationIp = ipMeta.destinationAddress,
                                    protocol = if (observedDomain != null) "TLS" else "TCP",
                                    uploadBytes = totalBytes,
                                    downloadBytes = 0L,
                                    networkType = "ANY"
                                ),
                                forcedConfidence = if (observedDomain != null) AttributionConfidence.HIGH else AttributionConfidence.LOW
                            )
                        } else {
                            tracker.recordObservation(
                                RawFlowObservation(
                                    domain = null,
                                    destinationIp = ipMeta.destinationAddress,
                                    protocol = "TCP",
                                    uploadBytes = totalBytes,
                                    downloadBytes = 0L,
                                    networkType = "ANY"
                                ),
                                forcedConfidence = AttributionConfidence.LOW
                            )
                        }
                    }
                }

                else -> {
                    tracker.recordObservation(
                        RawFlowObservation(
                            domain = null,
                            destinationIp = ipMeta.destinationAddress,
                            protocol = "IP-${ipMeta.protocol}",
                            uploadBytes = totalBytes,
                            downloadBytes = 0L,
                            networkType = "ANY"
                        ),
                        forcedConfidence = AttributionConfidence.UNKNOWN
                    )
                }
            }
        } catch (_: Exception) {
            // Malformed frame handled gracefully without halting loop
        }
    }

    /**
     * Safely inspects packet buffer without throwing exceptions on malformed frames.
     */
    fun parseIPv4Header(buffer: ByteBuffer): PacketMetadata? {
        return try {
            if (buffer.remaining() < 20) return null

            val position = buffer.position()
            val versionAndIHL = buffer.get(position).toInt() and 0xFF
            val version = versionAndIHL shr 4
            if (version != 4) return null

            val ihl = (versionAndIHL and 0x0F) * 4
            if (ihl < 20 || ihl > buffer.remaining()) return null

            val totalLength = buffer.getShort(position + 2).toInt() and 0xFFFF
            val protocol = buffer.get(position + 9).toInt() and 0xFF

            val srcBytes = ByteArray(4)
            val dstBytes = ByteArray(4)
            for (i in 0 until 4) {
                srcBytes[i] = buffer.get(position + 12 + i)
                dstBytes[i] = buffer.get(position + 16 + i)
            }

            val srcIp = InetAddress.getByAddress(srcBytes).hostAddress ?: "unknown"
            val dstIp = InetAddress.getByAddress(dstBytes).hostAddress ?: "unknown"

            PacketMetadata(
                version = version,
                protocol = protocol,
                sourceAddress = srcIp,
                destinationAddress = dstIp,
                packetLength = totalLength,
                headerLength = ihl
            )
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "PacketProcessor"
    }
}
