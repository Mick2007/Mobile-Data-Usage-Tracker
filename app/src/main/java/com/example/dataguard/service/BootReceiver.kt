package com.example.dataguard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.dataguard.DataGuardApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            DataMonitorService.start(context)

            try {
                val vpnSessionManager = DataGuardApp.instance.vpnSessionManager
                if (vpnSessionManager.isAutoStartOnBoot() && vpnSessionManager.isVpnPrepared(context)) {
                    vpnSessionManager.startVpn(context)
                }
            } catch (_: Exception) {
                // Best-effort reboot restoration
            }
        }
    }
}
