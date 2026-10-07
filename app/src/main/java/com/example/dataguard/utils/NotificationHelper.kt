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
                "Data Limit & High Usage Alerts",
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
        }
    }

    fun createForegroundNotification(statsText: String = "Monitoring active data usage"): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, FOREGROUND_CHANNEL_ID)
            .setContentTitle("DataGuard Active")
            .setContentText(statsText)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showHighUsageNotification(currentBytes: Long, limitBytes: Long, period: String = "Daily") {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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

    fun showIdleNotification() {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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

    fun showAppWarningNotification(appName: String, bytesUsed: Long) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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

    companion object {
        const val FOREGROUND_CHANNEL_ID = "dataguard_foreground"
        const val ALERT_CHANNEL_ID = "dataguard_alerts"
        const val IDLE_CHANNEL_ID = "dataguard_idle"

        const val FOREGROUND_NOTIFICATION_ID = 1001
        const val HIGH_USAGE_NOTIFICATION_ID = 1002
        const val IDLE_NOTIFICATION_ID = 1003
        const val APP_USAGE_NOTIFICATION_ID = 1004
    }
}
