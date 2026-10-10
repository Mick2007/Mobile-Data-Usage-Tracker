package com.example.dataguard.vpn.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

data class TrafficMetrics(
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val packetsIn: Long = 0L,
    val packetsOut: Long = 0L,
    val packetsProcessed: Long = 0L,
    val sessionStartTime: Long? = null
)

/**
 * High-performance, atomic counter engine for tracking traffic flowing through
 * the local VPN interface without blocking packet processing threads.
 */
class TrafficAccountingEngine {

    private val _bytesIn = AtomicLong(0L)
    private val _bytesOut = AtomicLong(0L)
    private val _packetsIn = AtomicLong(0L)
    private val _packetsOut = AtomicLong(0L)
    private val _sessionStartTime = AtomicLong(0L)

    private val _metricsFlow = MutableStateFlow(TrafficMetrics())
    val metricsFlow: StateFlow<TrafficMetrics> = _metricsFlow.asStateFlow()

    fun startSession(startTimeMillis: Long = System.currentTimeMillis()) {
        _sessionStartTime.set(startTimeMillis)
        reset()
        updateFlow()
    }

    fun recordPacketIn(bytes: Long) {
        if (bytes <= 0) return
        _bytesIn.addAndGet(bytes)
        _packetsIn.incrementAndGet()
        updateFlow()
    }

    fun recordPacketOut(bytes: Long) {
        if (bytes <= 0) return
        _bytesOut.addAndGet(bytes)
        _packetsOut.incrementAndGet()
        updateFlow()
    }

    fun getMetrics(): TrafficMetrics {
        val bIn = _bytesIn.get()
        val bOut = _bytesOut.get()
        val pIn = _packetsIn.get()
        val pOut = _packetsOut.get()
        val start = _sessionStartTime.get()
        return TrafficMetrics(
            bytesIn = bIn,
            bytesOut = bOut,
            packetsIn = pIn,
            packetsOut = pOut,
            packetsProcessed = pIn + pOut,
            sessionStartTime = if (start > 0) start else null
        )
    }

    fun reset() {
        _bytesIn.set(0L)
        _bytesOut.set(0L)
        _packetsIn.set(0L)
        _packetsOut.set(0L)
        updateFlow()
    }

    private fun updateFlow() {
        _metricsFlow.value = getMetrics()
    }
}
