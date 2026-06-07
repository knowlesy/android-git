package com.knowlesy.gitsync.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class SyncLog(
    val timestamp: Long,
    val status: String, // "SUCCESS", "ERROR", "CONFLICT"
    val message: String,
    val changedFiles: List<String>
)

class SyncLogger(context: Context) {
    private val logFile = File(context.filesDir, "sync_logs.json")
    private val maxLogs = 100

    @Synchronized
    fun log(status: String, message: String, changedFiles: List<String> = emptyList()) {
        try {
            val logs = readLogsFromFile()
            val newLog = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("status", status)
                put("message", message)
                val filesArray = JSONArray()
                changedFiles.forEach { filesArray.put(it) }
                put("changedFiles", filesArray)
            }

            // Insert new log at the beginning
            val updatedLogs = mutableListOf<JSONObject>()
            updatedLogs.add(newLog)
            for (i in 0 until logs.length()) {
                if (updatedLogs.size >= maxLogs) break
                updatedLogs.add(logs.getJSONObject(i))
            }

            val finalArray = JSONArray()
            updatedLogs.forEach { finalArray.put(it) }

            logFile.writeText(finalArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun logSuccess(message: String, changedFiles: List<String> = emptyList()) {
        log("SUCCESS", message, changedFiles)
    }

    fun logError(message: String) {
        log("ERROR", message)
    }

    fun logConflict(message: String, changedFiles: List<String> = emptyList()) {
        log("CONFLICT", message, changedFiles)
    }

    @Synchronized
    fun getLogs(): List<SyncLog> {
        val result = mutableListOf<SyncLog>()
        try {
            val array = readLogsFromFile()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val timestamp = obj.optLong("timestamp", 0)
                val status = obj.optString("status", "UNKNOWN")
                val message = obj.optString("message", "")
                val filesArray = obj.optJSONArray("changedFiles")
                val changedFiles = mutableListOf<String>()
                if (filesArray != null) {
                    for (j in 0 until filesArray.length()) {
                        changedFiles.add(filesArray.getString(j))
                    }
                }
                result.add(SyncLog(timestamp, status, message, changedFiles))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    @Synchronized
    fun clearLogs() {
        try {
            if (logFile.exists()) {
                logFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun readLogsFromFile(): JSONArray {
        if (!logFile.exists()) {
            return JSONArray()
        }
        return try {
            val text = logFile.readText()
            if (text.trim().isEmpty()) JSONArray() else JSONArray(text)
        } catch (e: Exception) {
            JSONArray()
        }
    }
}
