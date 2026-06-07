package com.knowlesy.gitsync.ui.main

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.knowlesy.gitsync.git.GitSyncEngine
import com.knowlesy.gitsync.model.SyncConfig
import com.knowlesy.gitsync.model.SyncLog
import com.knowlesy.gitsync.model.SyncLogger
import com.knowlesy.gitsync.service.SyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val config = SyncConfig(context)
    private val logger = SyncLogger(context)
    private val syncEngine = GitSyncEngine(context)

    // Form inputs
    val gitUrl = MutableStateFlow(config.gitUrl)
    val username = MutableStateFlow(config.username)
    val email = MutableStateFlow(config.email)
    val patToken = MutableStateFlow(config.patToken)
    val syncFolder = MutableStateFlow(config.syncFolder)
    val syncIntervalMinutes = MutableStateFlow(config.syncIntervalMinutes)
    val conflictStrategy = MutableStateFlow(config.conflictStrategy)

    // App state
    val isSyncEnabled = MutableStateFlow(config.isSyncEnabled)
    
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _logs = MutableStateFlow<List<SyncLog>>(emptyList())
    val logs: StateFlow<List<SyncLog>> = _logs.asStateFlow()

    private val _hasStoragePermission = MutableStateFlow(false)
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    private val _isIgnoringBatteryOptimizations = MutableStateFlow(false)
    val isIgnoringBatteryOptimizations: StateFlow<Boolean> = _isIgnoringBatteryOptimizations.asStateFlow()

    init {
        checkPermissionsState()
        checkBatteryOptimizationState()
        refreshLogs()
    }

    fun checkBatteryOptimizationState() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        _isIgnoringBatteryOptimizations.value = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun checkPermissionsState() {
        checkBatteryOptimizationState()
        _hasStoragePermission.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // Under API 30, standard storage permission is checked at runtime. We'll default to true or let activity handle it.
            true
        }
    }

    fun saveSettings() {
        config.gitUrl = gitUrl.value
        config.username = username.value
        config.email = email.value
        config.patToken = patToken.value
        config.syncFolder = syncFolder.value
        config.syncIntervalMinutes = syncIntervalMinutes.value
        config.conflictStrategy = conflictStrategy.value
        
        // If service is currently active, restart it to apply new settings
        if (config.isSyncEnabled) {
            triggerServiceStart()
        }
    }

    fun toggleSyncService(enabled: Boolean) {
        config.isSyncEnabled = enabled
        isSyncEnabled.value = enabled
        if (enabled) {
            saveSettings()
            triggerServiceStart()
        } else {
            triggerServiceStop()
        }
    }

    private fun triggerServiceStart() {
        val intent = Intent(context, SyncService::class.java).apply {
            action = SyncService.ACTION_START
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun triggerServiceStop() {
        val intent = Intent(context, SyncService::class.java).apply {
            action = SyncService.ACTION_STOP
        }
        context.stopService(intent)
    }

    fun syncNow(onComplete: (Boolean, String) -> Unit) {
        if (_isSyncing.value) return
        _isSyncing.value = true
        
        // Ensure latest form values are saved first
        saveSettings()

        viewModelScope.launch(Dispatchers.IO) {
            val result = syncEngine.executeSync()
            withContext(Dispatchers.Main) {
                _isSyncing.value = false
                refreshLogs()
                onComplete(result.success, result.message)
            }
        }
    }

    fun refreshLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = logger.getLogs()
            withContext(Dispatchers.Main) {
                _logs.value = list
            }
        }
    }

    fun clearLogs() {
        logger.clearLogs()
        refreshLogs()
    }
}
