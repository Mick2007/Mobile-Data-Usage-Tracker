package com.example.dataguard.vpn.engine

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Per-flow user-space UDP relay for non-DNS traffic (e.g. QUIC UDP 443, media streams).
 * Forwards outgoing UDP datagrams through a protected socket and relays response datagrams
 * back to the TUN virtual interface with accurate download byte accounting.
 */
class UdpFlowRelay(
    val tuple: Flow4Tuple,
    val callerUid: Int,
    val callerPkg: String?,
    private val scope: CoroutineScope,
    private val accountingEngine: TrafficAccountingEngine,
    private val flowTracker: DomainFlowTracker?,
    private val socketProtector: ((DatagramSocket) -> Boolean)?,
    private val packetWriter: (ByteArray) -> Unit,
    private val onClose: (Flow4Tuple) -> Unit
) {
    private val tag = "UdpFlowRelay[${tuple.srcPort}->${tuple.dstPort}]"

    private var socket: DatagramSocket? = null
    private var receiverJob: Job? = null
    private val isClosed = AtomicBoolean(false)
    val lastActiveTime = AtomicLong(System.currentTimeMillis())

    var associatedDomain: String? = null

    init {
        try {
            val ds = DatagramSocket()
            val protectedSuccessfully = socketProtector?.invoke(ds) ?: true
            if (!protectedSuccessfully) {
                Log.w(tag, "Failed to protect UDP DatagramSocket via VpnService")
            }
            ds.soTimeout = 30000
            socket = ds
            startReceiver()
        } catch (e: Exception) {
            Log.d(tag, "Error initializing UDP relay socket: ${e.message}")
            close()
        }
    }

    fun sendPacket(payload: ByteArray) {
        if (isClosed.get()) return
        lastActiveTime.set(System.currentTimeMillis())

        scope.launch(Dispatchers.IO) {
            try {
                val ds = socket ?: return@launch
                val destAddr = InetAddress.getByName(tuple.dstIp)
                val packet = DatagramPacket(payload, payload.size, destAddr, tuple.dstPort)
                ds.send(packet)
            } catch (e: Exception) {
                Log.d(tag, "Error sending UDP packet to ${tuple.dstIp}:${tuple.dstPort}: ${e.message}")
            }
        }
    }

    private fun startReceiver() {
        receiverJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(2048)
            try {
                val ds = socket ?: return@launch
                while (isActive && !isClosed.get()) {
                    val inPacket = DatagramPacket(buffer, buffer.size)
                    try {
                        ds.receive(inPacket)
                    } catch (_: Exception) {
                        break
                    }

                    if (inPacket.length <= 0) continue
                    lastActiveTime.set(System.currentTimeMillis())

                    val responsePayload = buffer.copyOf(inPacket.length)

                    // Synthesize IPv4 UDP packet back to TUN
                    val tunPacket = SystemDnsResolver.buildIpv4UdpPacket(
                        srcIp = tuple.dstIp,
                        dstIp = tuple.srcIp,
                        srcPort = tuple.dstPort,
                        dstPort = tuple.srcPort,
                        payload = responsePayload
                    )
                    packetWriter(tunPacket)
                    accountingEngine.recordPacketOut(tunPacket.size.toLong())

                    // Record download telemetry
                    flowTracker?.recordObservation(
                        RawFlowObservation(
                            domain = associatedDomain,
                            destinationIp = tuple.dstIp,
                            protocol = if (tuple.dstPort == 443) "QUIC" else "UDP",
                            uploadBytes = 0L,
                            downloadBytes = inPacket.length.toLong(),
                            networkType = "ANY",
                            uid = callerUid
                        )
                    )
                }
            } catch (t: Throwable) {
                Log.d(tag, "UDP receiver error: ${t.message}")
            } finally {
                close()
            }
        }
    }

    fun close() {
        if (isClosed.compareAndSet(false, true)) {
            receiverJob?.cancel()
            try {
                socket?.close()
            } catch (_: Exception) {}
            socket = null
            onClose(tuple)
        }
    }
}
