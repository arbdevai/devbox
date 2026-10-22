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
import com.arbdev.devbox.data.ChrootManager
import com.arbdev.devbox.terminal.TerminalSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KeepAliveService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "devbox_keepalive_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.arbdev.devbox.ACTION_START_KEEPALIVE"
        const val ACTION_STOP = "com.arbdev.devbox.ACTION_STOP_KEEPALIVE"
        const val ACTION_KILL_ALL = "com.arbdev.devbox.ACTION_KILL_ALL"

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

        fun killAllAndExit(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java).apply {
                action = ACTION_KILL_ALL
            }
            context.startService(intent)
        }

        fun updateNotification(context: Context) {
            if (!isRunning) return
            try {
                val active = TerminalSessionManager.getActiveSession()
                val sessionCount = TerminalSessionManager.sessions.size
                val statusText = if (active.isRunning) "Menjalankan perintah..." else "Siap"
                val dir = active.getDisplayDir()

                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingOpenIntent = PendingIntent.getActivity(
                    context,
                    0,
                    openAppIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val killIntent = Intent(context, KeepAliveService::class.java).apply {
                    action = ACTION_KILL_ALL
                }
                val pendingKillIntent = PendingIntent.getService(
                    context,
                    1,
                    killIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle("DevBox Linux Container ($sessionCount Sesi)")
                    .setContentText("${active.name}: $dir [$statusText]")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setOngoing(true)
                    .setContentIntent(pendingOpenIntent)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "Matikan Container & Keluar",
                        pendingKillIntent
                    )
                    .build()

                manager.notify(NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_KILL_ALL -> {
                serviceScope.launch {
                    try {
                        isRunning = false
                        releaseWakeLock()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        val nm = getSystemService(NotificationManager::class.java)
                        nm?.cancel(NOTIFICATION_ID)
                        nm?.cancelAll()
                        TerminalSessionManager.terminateAll()
                        ChrootManager.killDevboxProcessesAndUnmountSync()
                    } catch (_: Exception) {}
                    stopSelf()
                    kotlinx.coroutines.delay(400)
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                isRunning = false
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                val nm = getSystemService(NotificationManager::class.java)
                nm?.cancel(NOTIFICATION_ID)
                nm?.cancelAll()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        acquireWakeLock()
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        isRunning = true

        // OOM Killer Immunity (-1000) & Swap tuning untuk kestabilan background di CPU Helio G35 / Android 14
        serviceScope.launch {
            try {
                val myPid = android.os.Process.myPid()
                com.arbdev.devbox.data.RootExecutor.executeSync(
                    "echo -1000 > /proc/$myPid/oom_score_adj 2>/dev/null; sysctl -w vm.swappiness=60 2>/dev/null",
                    silent = true
                )
            } catch (_: Exception) {}
        }

        return START_NOT_STICKY
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
        val pendingOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Action Intent "Matikan & Keluar"
        val killIntent = Intent(this, KeepAliveService::class.java).apply {
            action = ACTION_KILL_ALL
        }
        val pendingKillIntent = PendingIntent.getService(
            this,
            1,
            killIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val active = TerminalSessionManager.getActiveSession()
        val sessionCount = TerminalSessionManager.sessions.size
        val statusText = if (active.isRunning) "Menjalankan perintah..." else "Siap"
        val dir = active.getDisplayDir()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DevBox Linux Container ($sessionCount Sesi)")
            .setContentText("${active.name}: $dir [$statusText]")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingOpenIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Matikan Container & Keluar",
                pendingKillIntent
            )
            .build()
    }

    override fun onDestroy() {
        releaseWakeLock()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
