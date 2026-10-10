package com.example.dataguard.vpn

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.example.dataguard.vpn.engine.FirewallEngine
import com.example.dataguard.vpn.engine.PacketProcessor
import com.example.dataguard.vpn.engine.TrafficAccountingEngine
import com.example.dataguard.vpn.model.VpnStatus
import com.example.dataguard.vpn.model.VpnState
import com.example.dataguard.vpn.service.DataGuardVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Central controller for managing the lifecycle, configuration,
 * and state transitions of the DataGuard local VpnService.
 */
class VpnSessionManager(
    private val context: Context,
    val firewallEngine: FirewallEngine,
    val trafficAccountingEngine: TrafficAccountingEngine,
    val packetProcessor: PacketProcessor,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _vpnState = MutableStateFlow(
        VpnState(
            status = VpnStatus.OFF,
            isAutoStartOnBoot = prefs.getBoolean(KEY_AUTO_START_BOOT, false)
        )
    )
    val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()

    init {
        // Stream accounting metrics into VpnState when active
        scope.launch {
            trafficAccountingEngine.metricsFlow.collectLatest { metrics ->
                if (_vpnState.value.status == VpnStatus.ACTIVE) {
                    _vpnState.value = _vpnState.value.copy(
                        bytesIn = metrics.bytesIn,
                        bytesOut = metrics.bytesOut,
                        packetsProcessed = metrics.packetsProcessed
                    )
                }
            }
        }
    }

    /**
     * Checks if VpnService permission has already been granted by the user.
     * Returns true if prepared (permission granted), false if system prompt is needed.
     */
    fun isVpnPrepared(ctx: Context = context): Boolean {
        return VpnService.prepare(ctx) == null
    }

    /**
     * Returns Intent for launching the system VPN permission dialog,
     * or null if permission is already granted.
     */
    fun getPrepareIntent(ctx: Context = context): Intent? {
        return VpnService.prepare(ctx)
    }

    /**
     * Requests starting the DataGuard local VPN service.
     */
    fun startVpn(ctx: Context = context) {
        if (!isVpnPrepared(ctx)) {
            _vpnState.value = _vpnState.value.copy(
                status = VpnStatus.ERROR,
                errorMessage = "VPN permission has not been granted. Please grant permission."
            )
            return
        }

        _vpnState.value = _vpnState.value.copy(
            status = VpnStatus.STARTING,
            errorMessage = null
        )

        val intent = Intent(ctx, DataGuardVpnService::class.java).apply {
            action = DataGuardVpnService.ACTION_START
        }
        try {
            ContextCompat.startForegroundService(ctx, intent)
        } catch (e: Exception) {
            _vpnState.value = _vpnState.value.copy(
                status = VpnStatus.ERROR,
                errorMessage = "Failed to start VPN service: ${e.message}"
            )
        }
    }

    /**
     * Requests stopping the DataGuard local VPN service.
     */
    fun stopVpn(ctx: Context = context) {
        val intent = Intent(ctx, DataGuardVpnService::class.java).apply {
            action = DataGuardVpnService.ACTION_STOP
        }
        try {
            ctx.startService(intent)
        } catch (_: Exception) {
            // Service may already be stopped
        }
        _vpnState.value = _vpnState.value.copy(
            status = VpnStatus.OFF,
            errorMessage = null
        )
    }

    /**
     * Re-applies active firewall rules to the running VPN session without full teardown.
     */
    fun reloadFirewallRules(ctx: Context = context) {
        if (_vpnState.value.status == VpnStatus.ACTIVE) {
            val intent = Intent(ctx, DataGuardVpnService::class.java).apply {
                action = DataGuardVpnService.ACTION_RELOAD_RULES
            }
            try {
                ctx.startService(intent)
            } catch (e: Exception) {
                // If restarting is required
                startVpn(ctx)
            }
        }
    }

    fun isAutoStartOnBoot(): Boolean {
        return prefs.getBoolean(KEY_AUTO_START_BOOT, false)
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START_BOOT, enabled).apply()
        _vpnState.value = _vpnState.value.copy(isAutoStartOnBoot = enabled)
    }

    fun onPermissionDenied() {
        _vpnState.value = _vpnState.value.copy(
            status = VpnStatus.OFF,
            errorMessage = "VPN permission denied by user. Local firewall could not be started."
        )
    }

    // Callbacks invoked by DataGuardVpnService to maintain synchronized state
    internal fun onServiceStarted(sessionName: String, localAddress: String = "10.1.10.2") {
        trafficAccountingEngine.startSession()
        _vpnState.value = _vpnState.value.copy(
            status = VpnStatus.ACTIVE,
            startedAt = System.currentTimeMillis(),
            activeSessionName = sessionName,
            localAddress = localAddress,
            errorMessage = null,
            bytesIn = 0L,
            bytesOut = 0L,
            packetsProcessed = 0L
        )
    }

    internal fun onServiceStopped() {
        packetProcessor.stop()
        trafficAccountingEngine.reset()
        _vpnState.value = _vpnState.value.copy(
            status = VpnStatus.OFF,
            activeSessionName = null,
            errorMessage = null
        )
    }

    internal fun onServiceError(message: String) {
        packetProcessor.stop()
        _vpnState.value = _vpnState.value.copy(
            status = VpnStatus.ERROR,
            activeSessionName = null,
            errorMessage = message
        )
    }

    internal fun onServiceRevoked(reason: String = "VPN permission revoked by system or another VPN connected") {
        packetProcessor.stop()
        _vpnState.value = _vpnState.value.copy(
            status = VpnStatus.OFF,
            activeSessionName = null,
            errorMessage = reason
        )
    }

    companion object {
        private const val PREFS_NAME = "dataguard_vpn_prefs"
        private const val KEY_AUTO_START_BOOT = "vpn_auto_start_boot"
    }
}
