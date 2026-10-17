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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

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
 * Routing & Forwarding Architecture:
 * - All user app traffic is routed through TUN (0.0.0.0/0).
 * - DataGuard's own package is excluded with addDisallowedApplication so its sockets bypass TUN.
 * - Non-DNS TCP packets are forwarded via in-process per-flow protected TCP proxies (TcpFlowRelay).
 * - Non-DNS UDP packets (such as QUIC on port 443) are forwarded via protected DatagramSockets (UdpFlowRelay).
 * - DNS queries on UDP 53 are evaluated and forwarded to system DNS.
 * - Blocked connections are dropped or returned with immediate TCP RST / DNS NXDOMAIN.
 */
class PacketProcessor(
    private val accountingEngine: TrafficAccountingEngine,
    private val firewallEngine: FirewallEngine,
    val flowTracker: DomainFlowTracker? = null
) {

    private var workerJob: Job? = null
    private var cleanerJob: Job? = null
    @Volatile
    private var isRunning = false
    @Volatile
    private var currentFd: Int = -1

    private val lifecycleLock = Any()

    var socketProtector: ((DatagramSocket) -> Boolean)? = null
    var tcpSocketProtector: ((Socket) -> Boolean)? = null
    var context: Context? = null
    var customUpstreamDns: List<InetAddress>? = null

    private val writeLock = Any()
    private var outputStream: FileOutputStream? = null

    // Active flow relay tables
    private val tcpFlows = ConcurrentHashMap<Flow4Tuple, TcpFlowRelay>()
    private val udpFlows = ConcurrentHashMap<Flow4Tuple, UdpFlowRelay>()

    /**
     * Starts the packet processor loop on the given ParcelFileDescriptor.
     * Idempotent: If already running on the same file descriptor, returns immediately.
     * If a new file descriptor is provided, cleanly stops previous loop before starting.
     */
    fun start(pfd: ParcelFileDescriptor, scope: CoroutineScope) {
        val newFd = pfd.fd
        synchronized(lifecycleLock) {
            if (isRunning && currentFd == newFd) {
                Log.i(TAG, "PacketProcessor already running on fd $newFd (idempotent start)")
                return
            }
            if (isRunning) {
                Log.i(TAG, "Stopping previous PacketProcessor loop on fd $currentFd to switch to fd $newFd")
                stopInternal()
            }
            isRunning = true
            currentFd = newFd
        }

        // Start idle flow cleanup job
        cleanerJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRunning) {
                delay(10000)
                cleanupIdleFlows()
            }
        }

        workerJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteBuffer.allocate(32767)
            val inputStream = FileInputStream(pfd.fileDescriptor)
            synchronized(writeLock) {
                outputStream = FileOutputStream(pfd.fileDescriptor)
            }

            try {
                Log.i(TAG, "Packet processing loop started on fd $newFd with full TCP/UDP forwarding")
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

                        when (metadata.protocol) {
                            17 -> handleUdpPacket(buffer, metadata, bytesRead, scope)
                            6 -> handleTcpPacket(buffer, metadata, bytesRead, scope)
                            else -> {
                                flowTracker?.recordObservation(
                                    RawFlowObservation(
                                        domain = null,
                                        destinationIp = metadata.destinationAddress,
                                        protocol = "IP-${metadata.protocol}",
                                        uploadBytes = bytesRead.toLong(),
                                        downloadBytes = 0L,
                                        networkType = getCurrentNetworkType().name
                                    ),
                                    forcedConfidence = AttributionConfidence.LOW
                                )
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                if (isRunning) {
                    Log.e(TAG, "Unexpected error in PacketProcessor loop: ${t.message}", t)
                }
            } finally {
                synchronized(lifecycleLock) {
                    isRunning = false
                    currentFd = -1
                }
                closeAllFlows()
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
        synchronized(lifecycleLock) {
            stopInternal()
        }
    }

    /**
     * Synchronously signals stopping and waits up to timeoutMs for the loop to terminate.
     * Prevents restart races where the new TUN is created before the old reader exits.
     */
    fun stopAndWait(timeoutMs: Long = 1000L) {
        synchronized(lifecycleLock) {
            if (!isRunning) return
            stopInternal()
        }
        val startTime = System.currentTimeMillis()
        while (isRunning && System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                Thread.sleep(25)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    private fun stopInternal() {
        isRunning = false
        currentFd = -1
        workerJob?.cancel()
        workerJob = null
        cleanerJob?.cancel()
        cleanerJob = null
        closeAllFlows()
        synchronized(writeLock) {
            try {
                outputStream?.close()
            } catch (_: Exception) {}
            outputStream = null
        }
    }

    private fun closeAllFlows() {
        for (flow in tcpFlows.values) {
            try { flow.close() } catch (_: Exception) {}
        }
        tcpFlows.clear()

        for (flow in udpFlows.values) {
            try { flow.close() } catch (_: Exception) {}
        }
        udpFlows.clear()
    }

    private fun cleanupIdleFlows() {
        val now = System.currentTimeMillis()
        val tcpIter = tcpFlows.entries.iterator()
        while (tcpIter.hasNext()) {
            val entry = tcpIter.next()
            if (now - entry.value.lastActiveTime.get() > 60000L) {
                try { entry.value.close() } catch (_: Exception) {}
                tcpIter.remove()
            }
        }

        val udpIter = udpFlows.entries.iterator()
        while (udpIter.hasNext()) {
            val entry = udpIter.next()
            if (now - entry.value.lastActiveTime.get() > 30000L) {
                try { entry.value.close() } catch (_: Exception) {}
                udpIter.remove()
            }
        }
    }

    /**
     * Resolves caller UID and package name for an active socket connection.
     * Uses ConnectivityManager.getConnectionOwnerUid on Android 10+ (API 29+),
     * and robustly falls back to /proc/net/tcp and /proc/net/udp socket tables.
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

        // 1. Try ConnectivityManager.getConnectionOwnerUid on API 29+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                if (cm != null) {
                    val ipProto = if (protocol == 6) OsConstants.IPPROTO_TCP else OsConstants.IPPROTO_UDP
                    val localAddress = InetSocketAddress(InetAddress.getByName(srcIp), srcPort)
                    val remoteAddress = InetSocketAddress(InetAddress.getByName(dstIp), dstPort)
                    resolvedUid = cm.getConnectionOwnerUid(ipProto, localAddress, remoteAddress)
                }
            } catch (_: Exception) {}
        }

        // 2. Fall back to reading /proc/net socket tables when TUN address or API restrictions return -1
        if (resolvedUid <= 0) {
            resolvedUid = ProcNetSocketReader.findUidForPort(protocol, srcPort)
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
     * Intercepts DNS queries on port 53, evaluates firewall rules, and either returns
     * immediate NXDOMAIN/Sinkhole or forwards upstream. Non-DNS UDP packets (including QUIC)
     * are forwarded through protected UdpFlowRelay sockets.
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

        // 1. Intercept DNS query on port 53
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

        // 2. Evaluate Firewall for Caller App on non-DNS UDP
        if (callerUid > 0 && firewallEngine.evaluateUid(callerUid, currentNetworkType).decision == FirewallDecision.BLOCK) {
            Log.d(TAG, "Dropped UDP packet from blocked UID $callerUid")
            return
        }
        if (!callerPkg.isNullOrBlank() && firewallEngine.evaluatePackage(callerPkg, currentNetworkType).decision == FirewallDecision.BLOCK) {
            Log.d(TAG, "Dropped UDP packet from blocked package $callerPkg")
            return
        }

        // 3. Check QUIC on UDP 443
        var quicDomain: String? = null
        if (dstPort == QuicParser.QUIC_PORT && buffer.hasRemaining()) {
            quicDomain = QuicParser.resolveQuicDomain(buffer, ipMeta.destinationAddress, flowTracker)
            if (quicDomain != null) {
                val eval = firewallEngine.evaluateConnection(
                    domain = quicDomain,
                    packageName = callerPkg,
                    uid = callerUid,
                    networkType = currentNetworkType
                )
                if (eval.decision == FirewallDecision.BLOCK) {
                    Log.i(TAG, "Dropped QUIC packet for blocked domain $quicDomain")
                    return
                }
            }
        }

        // 4. Non-DNS UDP Packet Forwarding Relay
        val payloadLen = buffer.remaining()
        if (payloadLen <= 0) return
        val payload = ByteArray(payloadLen)
        buffer.get(payload)

        val tuple = Flow4Tuple(ipMeta.sourceAddress, srcPort, ipMeta.destinationAddress, dstPort)
        val relay = udpFlows.computeIfAbsent(tuple) {
            val r = UdpFlowRelay(
                tuple = tuple,
                callerUid = callerUid,
                callerPkg = callerPkg,
                scope = scope,
                accountingEngine = accountingEngine,
                flowTracker = flowTracker,
                socketProtector = socketProtector,
                packetWriter = { pkt -> writePacketToTun(pkt) },
                onClose = { t -> udpFlows.remove(t) }
            )
            r.associatedDomain = quicDomain
            r
        }

        relay.associatedDomain = quicDomain ?: relay.associatedDomain
        relay.sendPacket(payload)
        accountingEngine.recordPacketIn(payload.size.toLong())

        flowTracker?.recordObservation(
            RawFlowObservation(
                domain = relay.associatedDomain,
                destinationIp = ipMeta.destinationAddress,
                protocol = if (dstPort == 443) "QUIC" else "UDP",
                uploadBytes = payload.size.toLong(),
                downloadBytes = 0L,
                networkType = currentNetworkType.name,
                uid = callerUid
            ),
            forcedConfidence = if (relay.associatedDomain != null) AttributionConfidence.MEDIUM else AttributionConfidence.LOW
        )
    }

    /**
     * Handles incoming TCP packets from TUN interface.
     * Evaluates firewall rules and forwards connections through TcpFlowRelay.
     */
    private fun handleTcpPacket(
        buffer: ByteBuffer,
        ipMeta: PacketMetadata,
        totalBytes: Int,
        scope: CoroutineScope
    ) {
        val ipHeaderLen = ipMeta.headerLength
        if (buffer.limit() < ipHeaderLen + 20) return

        buffer.position(ipHeaderLen)
        val srcPort = buffer.short.toInt() and 0xFFFF
        val dstPort = buffer.short.toInt() and 0xFFFF
        val seqNum = buffer.int.toLong() and 0xFFFFFFFFL
        val ackNum = buffer.int.toLong() and 0xFFFFFFFFL
        val dataOffsetAndFlags = buffer.short.toInt() and 0xFFFF
        val tcpHeaderLen = ((dataOffsetAndFlags shr 12) and 0x0F) * 4
        val flags = dataOffsetAndFlags and 0x3F

        val (callerUid, callerPkg) = resolveCaller(6, ipMeta.sourceAddress, srcPort, ipMeta.destinationAddress, dstPort)
        val currentNetworkType = getCurrentNetworkType()

        // 1. Check if application is blocked on current network interface
        if (callerUid > 0 && firewallEngine.evaluateUid(callerUid, currentNetworkType).decision == FirewallDecision.BLOCK) {
            sendTcpRst(ipMeta.destinationAddress, ipMeta.sourceAddress, dstPort, srcPort, ackNum, (seqNum + 1) and 0xFFFFFFFFL)
            return
        }
        if (!callerPkg.isNullOrBlank() && firewallEngine.evaluatePackage(callerPkg, currentNetworkType).decision == FirewallDecision.BLOCK) {
            sendTcpRst(ipMeta.destinationAddress, ipMeta.sourceAddress, dstPort, srcPort, ackNum, (seqNum + 1) and 0xFFFFFFFFL)
            return
        }

        val tuple = Flow4Tuple(ipMeta.sourceAddress, srcPort, ipMeta.destinationAddress, dstPort)
        val isSyn = (flags and TcpPacketBuilder.FLAG_SYN) != 0 && (flags and TcpPacketBuilder.FLAG_ACK) == 0
        val isRst = (flags and TcpPacketBuilder.FLAG_RST) != 0
        val isFin = (flags and TcpPacketBuilder.FLAG_FIN) != 0

        if (isRst) {
            tcpFlows.remove(tuple)?.handleClientRst()
            return
        }

        // Handle SYN: start new TCP relay flow
        if (isSyn) {
            // Check IP-level domain mapping pre-evaluation
            val preDomain = flowTracker?.getPrimaryDomainForIp(ipMeta.destinationAddress)
            if (preDomain != null) {
                val eval = firewallEngine.evaluateConnection(preDomain, callerPkg, callerUid, currentNetworkType)
                if (eval.decision == FirewallDecision.BLOCK) {
                    sendTcpRst(ipMeta.destinationAddress, ipMeta.sourceAddress, dstPort, srcPort, 0L, (seqNum + 1) and 0xFFFFFFFFL)
                    return
                }
            }

            val relay = TcpFlowRelay(
                tuple = tuple,
                callerUid = callerUid,
                callerPkg = callerPkg,
                scope = scope,
                accountingEngine = accountingEngine,
                firewallEngine = firewallEngine,
                flowTracker = flowTracker,
                socketProtector = tcpSocketProtector,
                packetWriter = { pkt -> writePacketToTun(pkt) },
                onClose = { t -> tcpFlows.remove(t) }
            )
            tcpFlows[tuple] = relay
            relay.handleSyn(seqNum)
            return
        }

        val relay = tcpFlows[tuple]
        if (relay == null) {
            // Unrecognized TCP packet for untracked flow: send RST
            if (!isFin) {
                sendTcpRst(ipMeta.destinationAddress, ipMeta.sourceAddress, dstPort, srcPort, ackNum, seqNum)
            }
            return
        }

        val payloadOffset = ipHeaderLen + tcpHeaderLen
        val payloadLen = buffer.limit() - payloadOffset
        val payload = if (payloadLen > 0) {
            buffer.position(payloadOffset)
            val p = ByteArray(payloadLen)
            buffer.get(p)
            p
        } else {
            ByteArray(0)
        }

        if (isFin) {
            relay.handleClientFin(seqNum)
        } else {
            relay.handleClientData(seqNum, payload)
        }
    }

    private fun sendTcpRst(
        srcIp: String,
        dstIp: String,
        srcPort: Int,
        dstPort: Int,
        seqNum: Long,
        ackNum: Long
    ) {
        val rstPacket = TcpPacketBuilder.buildIpv4TcpPacket(
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            seqNum = seqNum,
            ackNum = ackNum,
            flags = TcpPacketBuilder.FLAG_RST or TcpPacketBuilder.FLAG_ACK
        )
        writePacketToTun(rstPacket)
        accountingEngine.recordPacketOut(rstPacket.size.toLong())
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

            // Map resolved IPs to domain with exact DNS answer TTL
            val resolvedRecords = DnsQueryParser.extractResolvedIpv4Records(response)
            for (record in resolvedRecords) {
                flowTracker?.registerIpDomainMapping(record.ip, dnsDomain, record.ttlSeconds)
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
     * Safe parser for IPv4 headers (RFC 791).
     */
    fun parseIPv4Header(buffer: ByteBuffer): PacketMetadata? {
        val originalPos = buffer.position()
        try {
            if (buffer.remaining() < 20) return null

            val versionAndIHL = buffer.get().toInt() and 0xFF
            val version = versionAndIHL shr 4
            if (version != 4) return null

            val ihl = (versionAndIHL and 0x0F) * 4
            if (ihl < 20 || buffer.remaining() + 1 < ihl) return null

            buffer.position(originalPos + 2)
            val totalLength = buffer.short.toInt() and 0xFFFF

            buffer.position(originalPos + 9)
            val protocol = buffer.get().toInt() and 0xFF

            buffer.position(originalPos + 12)
            val srcBytes = ByteArray(4)
            buffer.get(srcBytes)
            val srcIp = InetAddress.getByAddress(srcBytes).hostAddress ?: return null

            val dstBytes = ByteArray(4)
            buffer.get(dstBytes)
            val dstIp = InetAddress.getByAddress(dstBytes).hostAddress ?: return null

            return PacketMetadata(
                version = 4,
                protocol = protocol,
                sourceAddress = srcIp,
                destinationAddress = dstIp,
                packetLength = totalLength,
                headerLength = ihl
            )
        } catch (_: Exception) {
            return null
        } finally {
            buffer.position(originalPos)
        }
    }

    companion object {
        private const val TAG = "PacketProcessor"
    }
}
