package com.dimadesu.djiremote.dji

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.annotation.SuppressLint
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dimadesu.djiremote.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class DjiConnectionService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var devicesJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val wakeLockHandler = Handler(Looper.getMainLooper())
    private val wakeLockRunnable = object : Runnable {
        override fun run() {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
            }
            wakeLockHandler.postDelayed(this, WAKE_LOCK_RENEWAL_DELAY_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        acquireWakeLock()
        observeDevices()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        devicesJob?.cancel()
        releaseWakeLock()
    }

    private fun observeDevices() {
        devicesJob?.cancel()
        devicesJob = scope.launch {
            DjiRepository.devices.collect { devices ->
                val hasActive = devices.any { it.isStarted }
                if (!hasActive) {
                    stopSelf()
                }
            }
        }
    }

    @SuppressLint("InvalidWakeLockTag")
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
        }
        if (!wakeLock!!.isHeld) {
            wakeLock!!.acquire(WAKE_LOCK_TIMEOUT_MS)
        }
        wakeLockHandler.removeCallbacks(wakeLockRunnable)
        wakeLockHandler.postDelayed(wakeLockRunnable, WAKE_LOCK_RENEWAL_DELAY_MS)
    }

    private fun releaseWakeLock() {
        wakeLockHandler.removeCallbacks(wakeLockRunnable)
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            // Best-effort release; ignore.
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DJI Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active DJI device connection"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DJI Remote")
            .setContentText("Device connection active")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "dji_connection"
        const val NOTIFICATION_ID = 1
        const val WAKE_LOCK_TAG = "DjiRemote:Connect"
        const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
        const val WAKE_LOCK_RENEWAL_DELAY_MS = 9 * 60 * 1000L

        fun startIfNeeded(context: Context) {
            val intent = Intent(context, DjiConnectionService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
