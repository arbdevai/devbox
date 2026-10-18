package com.arbdev.devbox.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.arbdev.devbox.MainActivity

class KeepAliveService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "devbox_keepalive_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.arbdev.devbox.ACTION_START_KEEPALIVE"
        const val ACTION_STOP = "com.arbdev.devbox.ACTION_STOP_KEEPALIVE"

        var isRunning = false
            private set

        fun startService(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            isRunning = false
            return START_NOT_STICKY
        }

        acquireWakeLock()
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        isRunning = true

        return START_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "DevBox::ChrootKeepAliveWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DevBox Background Keeper",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menjaga CPU tetap aktif agar Linux chroot dan server tidak terhenti saat layar mati."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DevBox Chroot Aktif")
            .setContentText("CPU WakeLock aktif. Proses Linux dan server berjalan di latar belakang.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        releaseWakeLock()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
