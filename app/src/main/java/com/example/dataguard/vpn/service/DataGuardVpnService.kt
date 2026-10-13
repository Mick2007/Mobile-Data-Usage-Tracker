package com.example.dataguard.vpn.service

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.MainActivity
import com.example.dataguard.DataGuardApp
import com.example.dataguard.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class DataGuardVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.i(TAG, "onStartCommand with action: $action")

        when (action) {
            ACTION_STOP -> {
                handleStop()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                handleStart()
                return START_STICKY
            }
            ACTION_RELOAD_RULES -> {
                // Seamlessly re-apply updated rules if active
                if (DataGuardApp.instance.vpnSessionManager.vpnState.value.status == com.example.dataguard.vpn.model.VpnStatus.ACTIVE) {
                    handleStart()
                }
                return START_STICKY
            }
            else -> {
                handleStart()
                return START_STICKY
            }
        }
    }

    private fun handleStart() {
        val app = DataGuardApp.instance
        val notificationHelper = app.notificationHelper
        val sessionManager = app.vpnSessionManager

        // 1. Post foreground notification immediately within lifecycle bounds
        startVpnForegroundNotification(notificationHelper)

        // 2. Validate VPN permission
        if (prepare(this) != null) {
            Log.e(TAG, "VPN permission is not prepared or was revoked")
            sessionManager.onServiceError("VPN permission has not been granted by system")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        // 3. Clean up any existing interface before re-establishing
        closeVpnInterface()

        // 4. Configure local VPN tunnel interface
        try {
            val configIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val configurePendingIntent = PendingIntent.getActivity(
                this,
                0,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = Builder()
                .setSession(SESSION_NAME)
                .setMtu(1500)
                .addAddress("10.1.10.1", 32)
                .addDnsServer("10.1.10.2")
                .addRoute("10.1.10.2", 32)
                .addRoute("10.1.10.0", 24)
                .allowBypass()
                .setConfigureIntent(configurePendingIntent)

            // Dynamic application-level firewall enforcement
            val blockedPkgs = sessionManager.firewallEngine.getBlockedPackages()
            var rulesEnforcedCount = 0
            for (pkg in blockedPkgs) {
                // Never block our own application package or system server core
                if (pkg == packageName || pkg == "android" || pkg == "android.system") continue
                try {
                    builder.addDisallowedApplication(pkg)
                    rulesEnforcedCount++
                } catch (e: Exception) {
                    // PackageManager.NameNotFoundException or fail-open safety
                    Log.w(TAG, "Cannot enforce firewall rule for package $pkg (failing open): ${e.message}")
                }
            }
            Log.i(TAG, "Configured firewall with $rulesEnforcedCount blocked applications")

            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface (another VPN may be active)")
                sessionManager.onServiceError("Failed to establish VPN tunnel. Another VPN may be active.")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }

            Log.i(TAG, "DataGuard local VPN established successfully (FD: ${vpnInterface?.fd})")
            sessionManager.onServiceStarted(SESSION_NAME)

            // 5. Start packet processor on established TUN interface with socket protection
            vpnInterface?.let { pfd ->
                sessionManager.packetProcessor.context = applicationContext
                sessionManager.packetProcessor.socketProtector = { socket ->
                    this@DataGuardVpnService.protect(socket)
                }
                sessionManager.packetProcessor.start(pfd, serviceScope)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception during VPN configuration: ${e.message}", e)
            sessionManager.onServiceError("VPN initialization failed: ${e.message}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startVpnForegroundNotification(notificationHelper: NotificationHelper) {
        val stopIntent = Intent(this, DataGuardVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = notificationHelper.createVpnForegroundNotification(
            statusText = "DataGuard Local Firewall Active",
            subText = "Local traffic protection running on-device",
            stopIntent = stopPendingIntent
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(
                    NotificationHelper.VPN_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
                )
            } catch (e: Exception) {
                Log.w(TAG, "Falling back to specialUse FGS type: ${e.message}")
                try {
                    startForeground(
                        NotificationHelper.VPN_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } catch (_: Exception) {
                    startForeground(NotificationHelper.VPN_NOTIFICATION_ID, notification)
                }
            }
        } else {
            startForeground(NotificationHelper.VPN_NOTIFICATION_ID, notification)
        }
    }

    private fun handleStop() {
        Log.i(TAG, "Stopping DataGuard VPN service")
        teardownResources()
        DataGuardApp.instance.vpnSessionManager.onServiceStopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        Log.w(TAG, "VPN service revoked by system or another VPN connected")
        teardownResources()
        val app = DataGuardApp.instance
        app.vpnSessionManager.onServiceRevoked("VPN disconnected: another VPN activated or permission revoked in system settings")
        app.notificationHelper.showVpnRevokedNotification("Local firewall stopped: another VPN activated or permission revoked")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onRevoke()
    }

    private fun teardownResources() {
        try {
            DataGuardApp.instance.vpnSessionManager.packetProcessor.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping packet processor: ${e.message}")
        }
        closeVpnInterface()
    }

    private fun closeVpnInterface() {
        vpnInterface?.let { pfd ->
            try {
                pfd.close()
                Log.i(TAG, "Closed VPN ParcelFileDescriptor")
            } catch (e: Exception) {
                Log.e(TAG, "Error closing VPN interface: ${e.message}")
            }
        }
        vpnInterface = null
    }

    override fun onDestroy() {
        Log.i(TAG, "DataGuardVpnService onDestroy")
        teardownResources()
        DataGuardApp.instance.vpnSessionManager.onServiceStopped()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.dataguard.vpn.ACTION_START"
        const val ACTION_STOP = "com.example.dataguard.vpn.ACTION_STOP"
        const val ACTION_RELOAD_RULES = "com.example.dataguard.vpn.ACTION_RELOAD_RULES"
        const val SESSION_NAME = "DataGuard Local Firewall"
        private const val TAG = "DataGuardVpnService"
    }
}
