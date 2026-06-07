package com.example.gitsync.model

import android.content.Context
import android.content.SharedPreferences

class SyncConfig(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("git_sync_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_GIT_URL = "git_url"
        const val KEY_USERNAME = "username"
        const val KEY_EMAIL = "email"
        const val KEY_PAT_TOKEN = "pat_token"
        const val KEY_SYNC_FOLDER = "sync_folder"
        const val KEY_SYNC_INTERVAL = "sync_interval_minutes"
        const val KEY_CONFLICT_STRATEGY = "conflict_strategy"
        const val KEY_IS_SYNC_ENABLED = "is_sync_enabled"
    }

    var gitUrl: String
        get() = prefs.getString(KEY_GIT_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GIT_URL, value.trim()).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value.trim()).apply()

    var email: String
        get() = prefs.getString(KEY_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_EMAIL, value.trim()).apply()

    var patToken: String
        get() = prefs.getString(KEY_PAT_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PAT_TOKEN, value.trim()).apply()

    var syncFolder: String
        get() = prefs.getString(KEY_SYNC_FOLDER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SYNC_FOLDER, value.trim()).apply()

    var syncIntervalMinutes: Int
        get() = prefs.getInt(KEY_SYNC_INTERVAL, 30)
        set(value) = prefs.edit().putInt(KEY_SYNC_INTERVAL, value).apply()

    var conflictStrategy: String
        get() = prefs.getString(KEY_CONFLICT_STRATEGY, "CONFLICT_COPY") ?: "CONFLICT_COPY"
        set(value) = prefs.edit().putString(KEY_CONFLICT_STRATEGY, value).apply()

    var isSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_IS_SYNC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_SYNC_ENABLED, value).apply()

    fun isValid(): Boolean {
        return gitUrl.isNotEmpty() && syncFolder.isNotEmpty() && username.isNotEmpty() && email.isNotEmpty()
    }
}
