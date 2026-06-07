package com.knowlesy.gitsync.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.knowlesy.gitsync.model.SyncConfig

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val config = SyncConfig(context)
            if (config.isSyncEnabled && config.syncIntervalMinutes > 0 && config.isValid()) {
                val serviceIntent = Intent(context, SyncService::class.java).apply {
                    action = SyncService.ACTION_START
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
