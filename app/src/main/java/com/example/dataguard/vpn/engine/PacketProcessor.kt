package com.example.dataguard.vpn.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import com.example.dataguard.data.entity.AttributionConfidence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
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
 * Implements non-blocking, safe packet processing with robust error handling,
 * real-time DNS capture, and protected upstream forwarding to system default DNS.
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

    var socketProtector: ((DatagramSocket) -> Boolean)? = null
    var context: Context? = null
    var customUpstreamDns: List<InetAddress>? = null

    private val writeLock = Any()
    private var outputStream: FileOutputStream? = null

    fun start(pfd: ParcelFileDescriptor, scope: CoroutineScope) {
        if (isRunning) {
            Log.w(TAG, "PacketProcessor already running")
            return
        }

        isRunning = true
        workerJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteBuffer.allocate(32767)
            val inputStream = FileInputStream(pfd.fileDescriptor)
            outputStream = FileOutputStream(pfd.fileDescriptor)

            try {
                Log.i(TAG, "Packet processing loop started with real-time DNS capture and upstream forwarding")
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

                        // Check if this is a DNS UDP query
                        if (metadata.protocol == 17) {
                            handleUdpPacket(buffer, metadata, bytesRead, scope)
                        } else {
                            // Extract other transport metadata (e.g. TCP SNI) safely
                            inspectTransportPayload(buffer, metadata, bytesRead.toLong())
                        }
                    }
                }
            } catch (t: Throwable) {
                if (isRunning) {
                    Log.e(TAG, "Unexpected error in PacketProcessor loop: ${t.message}", t)
                }
            } finally {
                isRunning = false
                synchronized(writeLock) {
                    try {
                        outputStream?.close()
                    } catch (_: Exception) {}
                    outputStream = null
                }
                Log.i(TAG, "Packet processing loop terminated cleanly")
            }
        }
    }

    fun stop() {
        isRunning = false
        workerJob?.cancel()
        workerJob = null
        synchronized(writeLock) {
            try {
                outputStream?.close()
            } catch (_: Exception) {}
            outputStream = null
        }
    }

    /**
     * Resolves caller UID and package name for an active socket connection.
     */
    fun resolveCaller(
        protocol: Int,
        srcIp: String,
        srcPort: Int,
        dstIp: String,
        dstPort: Int
    ): Pair<Int, String?> {
        val ctx = context ?: return Pair(-1, null)
        var resolvedUid = -1

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                if (cm != null) {
                    val ipProto = if (protocol == 6) OsConstants.IPPROTO_TCP else OsConstants.IPPROTO_UDP
                    val localAddress = InetSocketAddress(InetAddress.getByName(srcIp), srcPort)
                    val remoteAddress = InetSocketAddress(InetAddress.getByName(dstIp), dstPort)
                    resolvedUid = cm.getConnectionOwnerUid(ipProto, localAddress, remoteAddress)
                }
            } catch (_: Exception) {
                // System limitation or connection fallback
            }
        }

        var resolvedPkg: String? = null
        if (resolvedUid > 0) {
            try {
                val packages = ctx.packageManager.getPackagesForUid(resolvedUid)
                if (!packages.isNullOrEmpty()) {
                    resolvedPkg = packages[0]
                }
            } catch (_: Exception) {}
        }

        return Pair(resolvedUid, resolvedPkg)
    }

    private fun getCurrentNetworkType(): NetworkInterfaceType {
        val ctx = context ?: return NetworkInterfaceType.ANY
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkInterfaceType.ANY
        val activeNetwork = cm.activeNetwork ?: return NetworkInterfaceType.ANY
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return NetworkInterfaceType.ANY
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkInterfaceType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkInterfaceType.MOBILE
            else -> NetworkInterfaceType.ANY
        }
    }

    /**
     * Handles incoming UDP packets on the TUN interface.
     * Intercepts DNS queries on port 53, evaluates firewall rules with package attribution,
     * and either returns an immediate NXDOMAIN/Sinkhole or forwards upstream to System Default DNS.
     */
    private fun handleUdpPacket(
        buffer: ByteBuffer,
        ipMeta: PacketMetadata,
        totalBytes: Int,
        scope: CoroutineScope
    ) {
        val ipHeaderLen = ipMeta.headerLength
        if (buffer.limit() < ipHeaderLen + 8) return

        buffer.position(ipHeaderLen)
        val srcPort = buffer.short.toInt() and 0xFFFF
        val dstPort = buffer.short.toInt() and 0xFFFF
        val udpLen = buffer.short.toInt() and 0xFFFF
        val udpChecksum = buffer.short

        val (callerUid, callerPkg) = resolveCaller(17, ipMeta.sourceAddress, srcPort, ipMeta.destinationAddress, dstPort)
        val currentNetworkType = getCurrentNetworkType()

        // Check if this is a DNS query (destined to port 53 or to virtual DNS IP)
        if (dstPort == SystemDnsResolver.DNS_PORT && buffer.hasRemaining()) {
            val payloadLen = buffer.remaining()
            val queryBytes = ByteArray(payloadLen)
            buffer.get(queryBytes)

            val dnsDomain = DnsQueryParser.extractDnsDomain(queryBytes)
            if (dnsDomain != null) {
                val eval = firewallEngine.evaluateConnection(
                    domain = dnsDomain,
                    packageName = callerPkg,
                    uid = callerUid,
                    networkType = currentNetworkType
                )

                if (eval.decision == FirewallDecision.BLOCK) {
                    // Synthesize immediate NXDOMAIN response for blocked domain or blocked app
                    val nxDomainResponse = SystemDnsResolver.buildDnsNxDomainResponse(queryBytes)
                        ?: SystemDnsResolver.buildDnsSinkholeResponse(queryBytes)
                    if (nxDomainResponse != null) {
                        val packet = SystemDnsResolver.buildIpv4UdpPacket(
                            srcIp = ipMeta.destinationAddress,
                            dstIp = ipMeta.sourceAddress,
                            srcPort = dstPort,
                            dstPort = srcPort,
                            payload = nxDomainResponse
                        )
                        writePacketToTun(packet)
                        accountingEngine.recordPacketOut(packet.size.toLong())

                        flowTracker?.recordDnsQuery(
                            queriedDomain = dnsDomain,
                            clientIp = ipMeta.sourceAddress,
                            uploadBytes = queryBytes.size.toLong(),
                            downloadBytes = nxDomainResponse.size.toLong()
                        )
                    }
                    return
                }

                // If allowed: forward upstream to System Network Default DNS
                scope.launch(Dispatchers.IO) {
                    forwardAndRespondDns(
                        dnsDomain = dnsDomain,
                        queryBytes = queryBytes,
                        clientIp = ipMeta.sourceAddress,
                        clientPort = srcPort,
                        dnsServerIp = ipMeta.destinationAddress,
                        dnsPort = dstPort
                    )
                }
                return
            }
        }

        // Check if caller application is blocked on current network interface
        if (callerUid > 0 && firewallEngine.evaluateUid(callerUid, currentNetworkType).decision == FirewallDecision.BLOCK) {
            Log.d(TAG, "Dropped UDP packet from blocked UID $callerUid")
            return
        }
        if (!callerPkg.isNullOrBlank() && firewallEngine.evaluatePackage(callerPkg, currentNetworkType).decision == FirewallDecision.BLOCK) {
            Log.d(TAG, "Dropped UDP packet from blocked package $callerPkg")
            return
        }

        // Other generic UDP traffic
        flowTracker?.recordObservation(
            RawFlowObservation(
                domain = null,
                destinationIp = ipMeta.destinationAddress,
                protocol = "UDP",
                uploadBytes = totalBytes.toLong(),
                downloadBytes = 0L,
                networkType = currentNetworkType.name,
                uid = callerUid
            ),
            forcedConfidence = AttributionConfidence.LOW
        )
    }

    /**
     * Forwards DNS query to System Default DNS over protected socket and writes response to TUN.
     */
    private fun forwardAndRespondDns(
        dnsDomain: String,
        queryBytes: ByteArray,
        clientIp: String,
        clientPort: Int,
        dnsServerIp: String,
        dnsPort: Int
    ) {
        val upstreamServers = customUpstreamDns ?: (
            context?.let { SystemDnsResolver.getSystemDnsServers(it) } ?: listOf(
                InetAddress.getByName("8.8.8.8"),
                InetAddress.getByName("1.1.1.1")
            )
        )

        val response = SystemDnsResolver.forwardDnsQuery(
            queryData = queryBytes,
            upstreamServers = upstreamServers,
            socketProtector = socketProtector
        )

        if (response != null) {
            val responsePacket = SystemDnsResolver.buildIpv4UdpPacket(
                srcIp = dnsServerIp,
                dstIp = clientIp,
                srcPort = dnsPort,
                dstPort = clientPort,
                payload = response
            )
            writePacketToTun(responsePacket)
            accountingEngine.recordPacketOut(responsePacket.size.toLong())

            // Map resolved IPs to domain
            val resolvedIps = DnsQueryParser.extractResolvedIpv4Addresses(response)
            for (ip in resolvedIps) {
                flowTracker?.registerIpDomainMapping(ip, dnsDomain)
            }

            // Record live observation in tracker
            flowTracker?.recordDnsQuery(
                queriedDomain = dnsDomain,
                clientIp = clientIp,
                uploadBytes = queryBytes.size.toLong(),
                downloadBytes = response.size.toLong()
            )
        }
    }

    /**
     * Thread-safely writes raw IPv4 packet back to the TUN virtual interface.
     */
    fun writePacketToTun(packet: ByteArray) {
        synchronized(writeLock) {
            try {
                outputStream?.write(packet)
                outputStream?.flush()
            } catch (e: Exception) {
                if (isRunning) {
                    Log.d(TAG, "Error writing packet to TUN: ${e.message}")
                }
            }
        }
    }

    /**
     * Inspects transport layer payload (DNS UDP 53 or TLS ClientHello TCP 443).
     */
    fun inspectTransportPayload(buffer: ByteBuffer, ipMeta: PacketMetadata, totalBytes: Long) {
        val tracker = flowTracker ?: return

        try {
            val ipHeaderLen = ipMeta.headerLength
            if (buffer.limit() <= ipHeaderLen) return

            buffer.position(ipHeaderLen)

            when (ipMeta.protocol) {
                // TCP (6) -> Check TLS SNI on port 443
                6 -> {
                    if (buffer.remaining() >= 20) {
                        val srcPort = buffer.short.toInt() and 0xFFFF
                        val dstPort = buffer.short.toInt() and 0xFFFF
                        val seqNum = buffer.int
                        val ackNum = buffer.int
                        val dataOffsetAndFlags = buffer.short.toInt() and 0xFFFF
                        val tcpHeaderLen = ((dataOffsetAndFlags shr 12) and 0x0F) * 4

                        val (callerUid, callerPkg) = resolveCaller(6, ipMeta.sourceAddress, srcPort, ipMeta.destinationAddress, dstPort)
                        val currentNetworkType = getCurrentNetworkType()

                        // Check if application is blocked on current network interface
                        if (callerUid > 0 && firewallEngine.evaluateUid(callerUid, currentNetworkType).decision == FirewallDecision.BLOCK) {
                            Log.d(TAG, "Dropped TCP packet from blocked UID $callerUid")
                            return
                        }
                        if (!callerPkg.isNullOrBlank() && firewallEngine.evaluatePackage(callerPkg, currentNetworkType).decision == FirewallDecision.BLOCK) {
                            Log.d(TAG, "Dropped TCP packet from blocked package $callerPkg")
                            return
                        }

                        if (tcpHeaderLen in 20..buffer.remaining()) {
                            buffer.position(ipHeaderLen + tcpHeaderLen)
                            if (dstPort == 443 && buffer.hasRemaining()) {
                                val sni = TlsSniParser.extractSniHostname(buffer)
                                if (sni != null) {
                                    val eval = firewallEngine.evaluateConnection(
                                        domain = sni,
                                        packageName = callerPkg,
                                        uid = callerUid,
                                        networkType = currentNetworkType
                                    )
                                    if (eval.decision == FirewallDecision.BLOCK) {
                                        // STRICT ENFORCEMENT: Drop TLS/HTTPS connection immediately!
                                        Log.i(TAG, "Dropped TLS packet matching blocked rule for $sni (caller: $callerPkg, reason: ${eval.reason})")
                                        return
                                    }

                                    tracker.registerIpDomainMapping(ipMeta.destinationAddress, sni)
                                    tracker.recordObservation(
                                        RawFlowObservation(
                                            domain = sni,
                                            destinationIp = ipMeta.destinationAddress,
                                            protocol = "TLS/HTTPS",
                                            uploadBytes = totalBytes,
                                            downloadBytes = 0L,
                                            networkType = currentNetworkType.name,
                                            uid = callerUid
                                        ),
                                        forcedConfidence = AttributionConfidence.HIGH
                                    )
                                    return
                                }
                            }

                            tracker.recordObservation(
                                RawFlowObservation(
                                    domain = null,
                                    destinationIp = ipMeta.destinationAddress,
                                    protocol = "TCP",
                                    uploadBytes = totalBytes,
                                    downloadBytes = 0L,
                                    networkType = currentNetworkType.name,
                                    uid = callerUid
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
