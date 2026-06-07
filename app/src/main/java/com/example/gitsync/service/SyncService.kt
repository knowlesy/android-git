package com.example.gitsync.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.gitsync.MainActivity
import com.example.gitsync.git.GitSyncEngine
import com.example.gitsync.model.SyncConfig
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class SyncService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var syncJob: Job? = null

    private lateinit var config: SyncConfig
    private lateinit var engine: GitSyncEngine

    companion object {
        const val CHANNEL_ID = "git_sync_foreground_channel"
        const val NOTIFICATION_ID = 2026
        
        const val ACTION_START = "START_SYNC_SERVICE"
        const val ACTION_STOP = "STOP_SYNC_SERVICE"
        const val ACTION_RESTART = "RESTART_SYNC_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        config = SyncConfig(applicationContext)
        engine = GitSyncEngine(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START, ACTION_RESTART -> {
                startForegroundServiceCompat("Sync Service Starting...")
                startSyncLoop()
            }
            ACTION_STOP -> {
                stopForeground(true)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startSyncLoop() {
        syncJob?.cancel()
        syncJob = serviceScope.launch {
            while (isActive) {
                val interval = config.syncIntervalMinutes
                if (interval > 0 && config.isSyncEnabled) {
                    updateNotification("Syncing files...")
                    val result = engine.executeSync()
                    val lastSyncTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    
                    if (result.success) {
                        updateNotification("Sync active. Last sync: $lastSyncTime (Success)")
                    } else {
                        updateNotification("Sync active. Last sync: $lastSyncTime (Failed)")
                    }

                    // Delay for specified minutes
                    delay(interval * 60 * 1000L)
                } else {
                    // Config disabled or manual, check again in 1 minute
                    updateNotification("Sync idle. Background scheduling paused.")
                    delay(60 * 1000L)
                }
            }
        }
    }

    private fun startForegroundServiceCompat(content: String) {
        val notification = createNotification(content)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(content: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(content))
    }

    private fun createNotification(content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Git Sync")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_popup_sync) // Standard Android system icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Git Sync Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of background git sync cycles."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        syncJob?.cancel()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
