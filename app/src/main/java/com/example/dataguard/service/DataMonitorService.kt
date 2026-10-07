package com.example.dataguard.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.dataguard.DataGuardApp
import com.example.dataguard.utils.DataFormatter
import com.example.dataguard.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DataMonitorService : Service() {

    private val serviceJob = Job()
    private val scope = CoroutineScope(Dispatchers.Default + serviceJob)

    private var lastCheckTime = System.currentTimeMillis()
    private var lastTxBytes = 0L
    private var lastRxBytes = 0L

    override fun onCreate() {
        super.onCreate()
        lastTxBytes = TrafficStats.getMobileTxBytes().coerceAtLeast(0L)
        lastRxBytes = TrafficStats.getMobileRxBytes().coerceAtLeast(0L)
        lastCheckTime = System.currentTimeMillis()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? DataGuardApp
        val notificationHelper = app?.notificationHelper ?: NotificationHelper(this)

        val notification = notificationHelper.createForegroundNotification("DataGuard is actively monitoring bandwidth")
        startForeground(NotificationHelper.FOREGROUND_NOTIFICATION_ID, notification)

        startPeriodicMonitoring()
        return START_STICKY
    }

    private fun startPeriodicMonitoring() {
        val app = application as? DataGuardApp ?: return
        scope.launch {
            while (isActive) {
                delay(3000) // update speed and check usage every 3 seconds

                val now = System.currentTimeMillis()
                val elapsedSec = ((now - lastCheckTime) / 1000.0).coerceAtLeast(0.5)

                val currentTx = TrafficStats.getMobileTxBytes().coerceAtLeast(0L)
                val currentRx = TrafficStats.getMobileRxBytes().coerceAtLeast(0L)

                val speedTx = if (lastTxBytes > 0 && currentTx >= lastTxBytes) {
                    ((currentTx - lastTxBytes) / elapsedSec).toLong()
                } else 0L

                val speedRx = if (lastRxBytes > 0 && currentRx >= lastRxBytes) {
                    ((currentRx - lastRxBytes) / elapsedSec).toLong()
                } else 0L

                _liveTxSpeed.value = speedTx
                _liveRxSpeed.value = speedRx

                lastTxBytes = currentTx
                lastRxBytes = currentRx
                lastCheckTime = now

                // Check limits & idle every 15 seconds
                if (now % 15000 < 3500) {
                    app.getUsageStatsUseCase("daily")

                    val isIdle = app.detectIdleUseCase(5)
                    if (isIdle && app.isAutoIdleSaverEnabled) {
                        app.notificationHelper.showIdleNotification()
                    }

                    // Check daily threshold
                    val dailyLimitBytes = app.dailyLimitBytes
                    val totalSessionBytes = currentTx + currentRx
                    if (dailyLimitBytes > 0 && totalSessionBytes >= dailyLimitBytes * 0.85) {
                        app.notificationHelper.showHighUsageNotification(
                            totalSessionBytes,
                            dailyLimitBytes,
                            "Daily"
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private val _liveTxSpeed = MutableStateFlow(0L)
        val liveTxSpeed = _liveTxSpeed.asStateFlow()

        private val _liveRxSpeed = MutableStateFlow(0L)
        val liveRxSpeed = _liveRxSpeed.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, DataMonitorService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DataMonitorService::class.java)
            context.stopService(intent)
        }
    }
}
