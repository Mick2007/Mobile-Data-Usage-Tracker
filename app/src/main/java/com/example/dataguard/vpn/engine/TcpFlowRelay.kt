package com.example.dataguard.vpn.engine

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 4-tuple key identifying a unique TCP or UDP network flow.
 */
data class Flow4Tuple(
    val srcIp: String,
    val srcPort: Int,
    val dstIp: String,
    val dstPort: Int
)

/**
 * Per-flow user-space TCP relay with synthetic handshake, socket protection,
 * bidirectional payload pumping, SNI reassembly inspection, and download byte accounting.
 */
class TcpFlowRelay(
    val tuple: Flow4Tuple,
    val callerUid: Int,
    val callerPkg: String?,
    private val scope: CoroutineScope,
    private val accountingEngine: TrafficAccountingEngine,
    private val firewallEngine: FirewallEngine,
    private val flowTracker: DomainFlowTracker?,
    private val socketProtector: ((Socket) -> Boolean)?,
    private val packetWriter: (ByteArray) -> Unit,
    private val onClose: (Flow4Tuple) -> Unit
) {
    private val tag = "TcpFlowRelay[${tuple.srcPort}->${tuple.dstPort}]"

    private var upstreamSocket: Socket? = null
    private var upstreamOut: OutputStream? = null
    private var upstreamIn: InputStream? = null

    private var readerJob: Job? = null
    private val isClosed = AtomicBoolean(false)
    val lastActiveTime = AtomicLong(System.currentTimeMillis())

    // Sequence number management
    private var clientExpectedSeq = 0L
    private var relaySeq = (System.currentTimeMillis() and 0x00FFFFFFL) + 100000L

    // TLS SNI Reassembly Buffer for port 443
    private val tlsBuffer: TlsFlowBuffer? = if (tuple.dstPort == 443) TlsFlowBuffer() else null
    private var observedSni: String? = null
    private val pendingClientData = mutableListOf<ByteArray>()
    private val isConnected = AtomicBoolean(false)

    /**
     * Handles client TCP SYN: synthesizes SYN-ACK and initiates upstream connection.
     */
    fun handleSyn(clientSeq: Long) {
        lastActiveTime.set(System.currentTimeMillis())
        clientExpectedSeq = (clientSeq + 1) and 0xFFFFFFFFL

        // Synthesize and send SYN-ACK back to TUN
        val synAckPacket = TcpPacketBuilder.buildIpv4TcpPacket(
            srcIp = tuple.dstIp,
            dstIp = tuple.srcIp,
            srcPort = tuple.dstPort,
            dstPort = tuple.srcPort,
            seqNum = relaySeq,
            ackNum = clientExpectedSeq,
            flags = TcpPacketBuilder.FLAG_SYN or TcpPacketBuilder.FLAG_ACK
        )
        packetWriter(synAckPacket)
        accountingEngine.recordPacketOut(synAckPacket.size.toLong())
        relaySeq = (relaySeq + 1) and 0xFFFFFFFFL

        // Asynchronously establish upstream protected socket
        scope.launch(Dispatchers.IO) {
            connectUpstream()
        }
    }

    private fun connectUpstream() {
        try {
            val socket = Socket()
            upstreamSocket = socket

            // Protect socket so upstream traffic bypasses TUN interface
            val protectedSuccessfully = socketProtector?.invoke(socket) ?: true
            if (!protectedSuccessfully) {
                Log.w(tag, "Failed to protect TCP socket via VpnService")
            }

            socket.connect(InetSocketAddress(tuple.dstIp, tuple.dstPort), 5000)
            socket.soTimeout = 30000
            upstreamOut = socket.getOutputStream()
            upstreamIn = socket.getInputStream()
            isConnected.set(true)

            // Flush any early client data buffered before connection completed
            synchronized(pendingClientData) {
                for (chunk in pendingClientData) {
                    upstreamOut?.write(chunk)
                }
                upstreamOut?.flush()
                pendingClientData.clear()
            }

            // Start bidirectional response reader loop
            startResponseReader()
        } catch (e: Exception) {
            Log.d(tag, "Upstream TCP connection failed to ${tuple.dstIp}:${tuple.dstPort}: ${e.message}")
            sendRstAndClose()
        }
    }

    /**
     * Handles client TCP data packet (PSH, ACK).
     */
    fun handleClientData(seq: Long, payload: ByteArray) {
        if (isClosed.get()) return
        lastActiveTime.set(System.currentTimeMillis())

        if (payload.isNotEmpty()) {
            clientExpectedSeq = (seq + payload.size) and 0xFFFFFFFFL

            // 1. Inspect TLS SNI on port 443 with multi-segment reassembly
            if (tlsBuffer != null && observedSni == null) {
                val feedResult = tlsBuffer.feed(payload)
                if (feedResult is TlsFeedResult.Parsed && feedResult.sni != null) {
                    observedSni = feedResult.sni
                    flowTracker?.registerIpDomainMapping(tuple.dstIp, feedResult.sni)

                    // Evaluate firewall rule for the detected SNI
                    val eval = firewallEngine.evaluateConnection(
                        domain = feedResult.sni,
                        packageName = callerPkg,
                        uid = callerUid
                    )
                    if (eval.decision == FirewallDecision.BLOCK) {
                        Log.i(tag, "Blocking TLS flow for domain: ${feedResult.sni}")
                        sendRstAndClose()
                        return
                    }
                }
            }

            // 2. Relay payload to upstream socket
            if (isConnected.get()) {
                scope.launch(Dispatchers.IO) {
                    try {
                        upstreamOut?.write(payload)
                        upstreamOut?.flush()
                    } catch (e: Exception) {
                        sendRstAndClose()
                    }
                }
            } else {
                synchronized(pendingClientData) {
                    pendingClientData.add(payload)
                }
            }

            // Record upload bytes telemetry
            accountingEngine.recordPacketIn(payload.size.toLong())
            flowTracker?.recordObservation(
                RawFlowObservation(
                    domain = observedSni,
                    destinationIp = tuple.dstIp,
                    protocol = if (tuple.dstPort == 443) "TLS/HTTPS" else "TCP",
                    uploadBytes = payload.size.toLong(),
                    downloadBytes = 0L,
                    networkType = "ANY",
                    uid = callerUid
                )
            )

            // 3. Acknowledge received payload back to TUN
            val ackPacket = TcpPacketBuilder.buildIpv4TcpPacket(
                srcIp = tuple.dstIp,
                dstIp = tuple.srcIp,
                srcPort = tuple.dstPort,
                dstPort = tuple.srcPort,
                seqNum = relaySeq,
                ackNum = clientExpectedSeq,
                flags = TcpPacketBuilder.FLAG_ACK
            )
            packetWriter(ackPacket)
            accountingEngine.recordPacketOut(ackPacket.size.toLong())
        }
    }

    /**
     * Handles client TCP FIN: closes upstream output stream and acknowledges.
     */
    fun handleClientFin(seq: Long) {
        lastActiveTime.set(System.currentTimeMillis())
        clientExpectedSeq = (seq + 1) and 0xFFFFFFFFL

        val ackPacket = TcpPacketBuilder.buildIpv4TcpPacket(
            srcIp = tuple.dstIp,
            dstIp = tuple.srcIp,
            srcPort = tuple.dstPort,
            dstPort = tuple.srcPort,
            seqNum = relaySeq,
            ackNum = clientExpectedSeq,
            flags = TcpPacketBuilder.FLAG_ACK
        )
        packetWriter(ackPacket)
        accountingEngine.recordPacketOut(ackPacket.size.toLong())

        scope.launch(Dispatchers.IO) {
            try {
                upstreamSocket?.shutdownOutput()
            } catch (_: Exception) {}
        }
    }

    /**
     * Handles client TCP RST: immediately terminates upstream socket and flow.
     */
    fun handleClientRst() {
        close()
    }

    /**
     * Reads responses from upstream socket, synthesizes IPv4 TCP packets, and writes them to TUN.
     */
    private fun startResponseReader() {
        readerJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(1400)
            try {
                val input = upstreamIn ?: return@launch
                while (isActive && !isClosed.get()) {
                    val bytesRead = try {
                        input.read(buffer)
                    } catch (_: Exception) {
                        -1
                    }

                    if (bytesRead <= 0) break // EOF or error
                    lastActiveTime.set(System.currentTimeMillis())

                    val chunk = buffer.copyOf(bytesRead)
                    val dataPacket = TcpPacketBuilder.buildIpv4TcpPacket(
                        srcIp = tuple.dstIp,
                        dstIp = tuple.srcIp,
                        srcPort = tuple.dstPort,
                        dstPort = tuple.srcPort,
                        seqNum = relaySeq,
                        ackNum = clientExpectedSeq,
                        flags = TcpPacketBuilder.FLAG_PSH or TcpPacketBuilder.FLAG_ACK,
                        payload = chunk
                    )
                    packetWriter(dataPacket)
                    accountingEngine.recordPacketOut(dataPacket.size.toLong())
                    relaySeq = (relaySeq + bytesRead) and 0xFFFFFFFFL

                    // Accurately record download bytes in flow tracker
                    flowTracker?.recordObservation(
                        RawFlowObservation(
                            domain = observedSni,
                            destinationIp = tuple.dstIp,
                            protocol = if (tuple.dstPort == 443) "TLS/HTTPS" else "TCP",
                            uploadBytes = 0L,
                            downloadBytes = bytesRead.toLong(),
                            networkType = "ANY",
                            uid = callerUid
                        )
                    )
                }

                // Send FIN-ACK to client upon remote EOF
                if (!isClosed.get()) {
                    val finPacket = TcpPacketBuilder.buildIpv4TcpPacket(
                        srcIp = tuple.dstIp,
                        dstIp = tuple.srcIp,
                        srcPort = tuple.dstPort,
                        dstPort = tuple.srcPort,
                        seqNum = relaySeq,
                        ackNum = clientExpectedSeq,
                        flags = TcpPacketBuilder.FLAG_FIN or TcpPacketBuilder.FLAG_ACK
                    )
                    packetWriter(finPacket)
                    accountingEngine.recordPacketOut(finPacket.size.toLong())
                    relaySeq = (relaySeq + 1) and 0xFFFFFFFFL
                }
            } catch (t: Throwable) {
                Log.d(tag, "Response reader exception: ${t.message}")
            } finally {
                close()
            }
        }
    }

    private fun sendRstAndClose() {
        if (!isClosed.get()) {
            val rstPacket = TcpPacketBuilder.buildIpv4TcpPacket(
                srcIp = tuple.dstIp,
                dstIp = tuple.srcIp,
                srcPort = tuple.dstPort,
                dstPort = tuple.srcPort,
                seqNum = relaySeq,
                ackNum = clientExpectedSeq,
                flags = TcpPacketBuilder.FLAG_RST or TcpPacketBuilder.FLAG_ACK
            )
            packetWriter(rstPacket)
            accountingEngine.recordPacketOut(rstPacket.size.toLong())
        }
        close()
    }

    fun close() {
        if (isClosed.compareAndSet(false, true)) {
            readerJob?.cancel()
            try {
                upstreamSocket?.close()
            } catch (_: Exception) {}
            upstreamSocket = null
            upstreamIn = null
            upstreamOut = null
            onClose(tuple)
        }
    }
}
