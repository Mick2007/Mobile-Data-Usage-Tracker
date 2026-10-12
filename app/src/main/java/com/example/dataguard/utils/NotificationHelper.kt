package com.example.dataguard.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.dataguard.data.entity.NotificationAlertCategory

class NotificationHelper(
    private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val foregroundChannel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "Data Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time mobile data monitoring status"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(foregroundChannel)

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Data Plan & Usage Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when data consumption approaches or exceeds plan limits"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(alertChannel)

            val idleChannel = NotificationChannel(
                IDLE_CHANNEL_ID,
                "Device Inactivity & Auto-Saver",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when mobile data is paused to conserve battery and bandwidth"
            }
            notificationManager.createNotificationChannel(idleChannel)

            val securityChannel = NotificationChannel(
                SECURITY_CHANNEL_ID,
                "Firewall & Network Security Events",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when malicious, blocked, or newly observed connections occur"
            }
            notificationManager.createNotificationChannel(securityChannel)

            val vpnChannel = NotificationChannel(
                VPN_CHANNEL_ID,
                "DataGuard Local VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent indicator for on-device local firewall and network protection"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(vpnChannel)
        }
    }

    private fun createActionPendingIntent(requestCode: Int, routeExtra: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (routeExtra != null) {
                putExtra("navigate_to", routeExtra)
            }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun createForegroundNotification(statsText: String = "Monitoring active data usage"): Notification {
        val pendingIntent = createActionPendingIntent(10)

        return NotificationCompat.Builder(context, FOREGROUND_CHANNEL_ID)
            .setContentTitle("DataGuard Active")
            .setContentText(statsText)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // 1. Data Plan Threshold Alert (50%, 75%, 90%, 100%)
    fun showPlanThresholdAlert(
        planName: String,
        thresholdPercent: Int,
        usedBytes: Long,
        totalBytes: Long
    ) {
        val pendingIntent = createActionPendingIntent(101, "settings")
        val usedFormatted = DataFormatter.formatBytes(usedBytes)
        val totalFormatted = DataFormatter.formatBytes(totalBytes)

        val title = when (thresholdPercent) {
            100 -> "🚨 Plan Exhausted: $planName at 100%"
            90 -> "⚠️ Critical Data Alert: $planName at 90%"
            75 -> "📊 Plan Usage Warning: $planName at 75%"
            else -> "ℹ️ Plan Usage Notice: $planName at 50%"
        }
        val detail = "You have consumed $usedFormatted of your $totalFormatted allowance ($thresholdPercent%). Tap to adjust settings or restrict heavy apps."

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Used $usedFormatted of $totalFormatted ($thresholdPercent%)")
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setSmallIcon(if (thresholdPercent >= 90) android.R.drawable.stat_notify_error else android.R.drawable.stat_sys_warning)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(if (thresholdPercent >= 90) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setGroup(GROUP_DATA_ALERTS)
            .addAction(android.R.drawable.ic_menu_preferences, "View Plan", pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_PLAN_THRESHOLD + thresholdPercent, notification)
    }

    // 2. App Exceeds X MB Alert
    fun showAppExceedsMbAlert(appName: String, packageName: String, bytesUsed: Long, thresholdMb: Long) {
        val pendingIntent = createActionPendingIntent(102, "apps")
        val formatted = DataFormatter.formatBytes(bytesUsed)

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle("📱 High App Usage: $appName")
            .setContentText("$appName used $formatted, exceeding threshold of ${thresholdMb} MB")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$appName has transferred $formatted in recent activity. Tap to review quota or block in Firewall.")
            )
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_DATA_ALERTS)
            .addAction(android.R.drawable.ic_menu_view, "Manage App", pendingIntent)
            .build()

        notificationManager.notify(packageName.hashCode(), notification)
    }

    // 3. Domain Exceeds X MB Alert
    fun showDomainExceedsMbAlert(domain: String, bytesUsed: Long, thresholdMb: Long) {
        val pendingIntent = createActionPendingIntent(103, "websites")
        val formatted = DataFormatter.formatBytes(bytesUsed)

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle("🌐 High Domain Traffic: $domain")
            .setContentText("$domain transferred $formatted (limit: ${thresholdMb} MB)")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Observed domain $domain consumed $formatted of network bandwidth. Tap to block this host.")
            )
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setGroup(GROUP_SECURITY_ALERTS)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "View Websites", pendingIntent)
            .build()

        notificationManager.notify(domain.hashCode(), notification)
    }

    // 4. Hotspot Exceeds X MB Alert
    fun showHotspotExceedsMbAlert(totalBytes: Long, thresholdMb: Long) {
        val pendingIntent = createActionPendingIntent(104, "hotspot")
        val formatted = DataFormatter.formatBytes(totalBytes)

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle("📡 Tethering Usage Spike")
            .setContentText("Hotspot session consumed $formatted (threshold: ${thresholdMb} MB)")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Tethering/hotspot client traffic has consumed $formatted. Tap to review hotspot sessions or disable tethering.")
            )
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_DATA_ALERTS)
            .addAction(android.R.drawable.ic_menu_info_details, "Inspect Hotspot", pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_HOTSPOT_ALERT, notification)
    }

    // 5. Unusual Data Spike Alert
    fun showDataSpikeAlert(speedKbps: Long, appName: String? = null) {
        val pendingIntent = createActionPendingIntent(105, "dashboard")
        val speedMbText = String.format(java.util.Locale.US, "%.1f MB/s", speedKbps / 1024.0)

        val text = if (appName != null) {
            "Rapid data transmission detected from $appName at $speedMbText."
        } else {
            "Sudden high background data transfer rate: $speedMbText."
        }

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle("⚡ Unusual Network Activity Spike")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text Tap to view live network inspector."))
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_DATA_ALERTS)
            .build()

        notificationManager.notify(NOTIFICATION_ID_SPIKE_ALERT, notification)
    }

    // 6. Newly Observed App Network Activity
    fun showNewAppObservedAlert(appName: String, packageName: String) {
        val pendingIntent = createActionPendingIntent(106, "apps")

        val notification = NotificationCompat.Builder(context, SECURITY_CHANNEL_ID)
            .setContentTitle("✨ New App Access: $appName")
            .setContentText("$appName initiated network communication for the first time")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Application '$appName' ($packageName) was seen transmitting data. Tap to configure firewall or limits.")
            )
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setGroup(GROUP_SECURITY_ALERTS)
            .build()

        notificationManager.notify(packageName.hashCode() xor 0x55, notification)
    }

    // 7. Newly Observed Domain
    fun showNewDomainObservedAlert(domain: String, appName: String) {
        val pendingIntent = createActionPendingIntent(107, "websites")

        val notification = NotificationCompat.Builder(context, SECURITY_CHANNEL_ID)
            .setContentTitle("🌐 New Host Contacted: $domain")
            .setContentText("$appName connected to new domain $domain")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setGroup(GROUP_SECURITY_ALERTS)
            .build()

        notificationManager.notify(domain.hashCode() xor 0x77, notification)
    }

    // 8. Firewall Block Event
    fun showFirewallBlockAlert(appName: String, targetDomainOrReason: String) {
        val pendingIntent = createActionPendingIntent(108, "firewall")

        val notification = NotificationCompat.Builder(context, SECURITY_CHANNEL_ID)
            .setContentTitle("🛡️ Blocked by Firewall: $appName")
            .setContentText("Traffic blocked: $targetDomainOrReason")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setGroup(GROUP_SECURITY_ALERTS)
            .addAction(android.R.drawable.ic_menu_info_details, "Firewall Rules", pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_FIREWALL_BLOCK, notification)
    }

    // Idle notification
    fun showIdleNotification() {
        val pendingIntent = createActionPendingIntent(109)

        val notification = NotificationCompat.Builder(context, IDLE_CHANNEL_ID)
            .setContentTitle("DataGuard Saver Activated")
            .setContentText("Device is idle. Data guard recommendations enabled.")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(IDLE_NOTIFICATION_ID, notification)
    }

    // High usage daily notification (backward compatibility)
    fun showHighUsageNotification(currentBytes: Long, limitBytes: Long, period: String = "Daily") {
        val pendingIntent = createActionPendingIntent(110)
        val usedFormatted = DataFormatter.formatBytes(currentBytes)
        val limitFormatted = DataFormatter.formatBytes(limitBytes)
        val percent = if (limitBytes > 0) ((currentBytes.toDouble() / limitBytes) * 100).toInt() else 100

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle("⚠️ Data Alert: $period Limit at $percent%")
            .setContentText("Used $usedFormatted of $limitFormatted limit.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("You have used $usedFormatted out of your $limitFormatted $period allowance ($percent%). Consider restricting background sync or streaming.")
            )
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(HIGH_USAGE_NOTIFICATION_ID, notification)
    }

    fun showAppWarningNotification(appName: String, bytesUsed: Long) {
        val pendingIntent = createActionPendingIntent(111, "apps")

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle("App Spike: $appName")
            .setContentText("$appName consumed ${DataFormatter.formatBytes(bytesUsed)} recently")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(APP_USAGE_NOTIFICATION_ID, notification)
    }

    fun showAppQuotaWarningNotification(
        appName: String,
        packageName: String,
        usedBytes: Long,
        limitBytes: Long,
        thresholdPercent: Int
    ) {
        val pendingIntent = createActionPendingIntent(112, "apps")
        val usedFormatted = DataFormatter.formatBytes(usedBytes)
        val limitFormatted = DataFormatter.formatBytes(limitBytes)

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle("⚠️ App Quota Warning: $appName ($thresholdPercent%)")
            .setContentText("Used $usedFormatted of $limitFormatted limit")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$appName has reached $thresholdPercent% of its quota ($usedFormatted / $limitFormatted).")
            )
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(packageName.hashCode() xor 0x11, notification)
    }

    fun showAppQuotaExceededNotification(
        appName: String,
        packageName: String,
        usedBytes: Long,
        limitBytes: Long,
        actionTaken: String
    ) {
        val pendingIntent = createActionPendingIntent(113, "apps")
        val usedFormatted = DataFormatter.formatBytes(usedBytes)
        val limitFormatted = DataFormatter.formatBytes(limitBytes)

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle("🛑 App Quota Exceeded: $appName")
            .setContentText("Used $usedFormatted of $limitFormatted limit ($actionTaken)")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$appName has exceeded its assigned limit of $limitFormatted (Used: $usedFormatted). Action enforced: $actionTaken.")
            )
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(packageName.hashCode() xor 0x22, notification)
    }

    fun createVpnForegroundNotification(
        statusText: String = "Local Firewall Active",
        subText: String = "Monitoring traffic on-device",
        stopIntent: PendingIntent? = null
    ): Notification {
        val pendingIntent = createActionPendingIntent(201, "dashboard")

        val builder = NotificationCompat.Builder(context, VPN_CHANNEL_ID)
            .setContentTitle(statusText)
            .setContentText(subText)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (stopIntent != null) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop VPN",
                stopIntent
            )
        }

        return builder.build()
    }

    fun showVpnRevokedNotification(reason: String = "VPN disconnected by system or another VPN connected") {
        val pendingIntent = createActionPendingIntent(202, "dashboard")

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle("🛡️ DataGuard VPN Inactive")
            .setContentText(reason)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(VPN_REVOKED_NOTIFICATION_ID, notification)
    }

    companion object {
        const val FOREGROUND_CHANNEL_ID = "dataguard_foreground"
        const val ALERT_CHANNEL_ID = "dataguard_alerts"
        const val IDLE_CHANNEL_ID = "dataguard_idle"
        const val SECURITY_CHANNEL_ID = "dataguard_security"
        const val VPN_CHANNEL_ID = "dataguard_vpn"

        const val GROUP_DATA_ALERTS = "group_dataguard_data_alerts"
        const val GROUP_SECURITY_ALERTS = "group_dataguard_security_alerts"

        const val FOREGROUND_NOTIFICATION_ID = 1001
        const val HIGH_USAGE_NOTIFICATION_ID = 1002
        const val IDLE_NOTIFICATION_ID = 1003
        const val APP_USAGE_NOTIFICATION_ID = 1004
        const val NOTIFICATION_ID_PLAN_THRESHOLD = 1100
        const val NOTIFICATION_ID_HOTSPOT_ALERT = 1201
        const val NOTIFICATION_ID_SPIKE_ALERT = 1202
        const val NOTIFICATION_ID_FIREWALL_BLOCK = 1203
        const val VPN_NOTIFICATION_ID = 2001
        const val VPN_REVOKED_NOTIFICATION_ID = 2002
    }
}
