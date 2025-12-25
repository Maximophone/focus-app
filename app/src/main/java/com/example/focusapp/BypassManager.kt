package com.example.focusapp

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Manages general app settings.
 */
class SettingsRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("focus_settings", Context.MODE_PRIVATE)

    fun getLogFilePath(): String {
        val defaultPath = File(context.getExternalFilesDir(null), "focus_log.md").absolutePath
        return prefs.getString("log_file_path", defaultPath) ?: defaultPath
    }

    fun setLogFilePath(path: String) {
        prefs.edit().putString("log_file_path", path).apply()
    }
}

/**
 * Manages active bypasses and logging to markdown.
 */
class BypassManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("focus_bypasses", Context.MODE_PRIVATE)
    private val settingsRepository = SettingsRepository(context)

    /**
     * Check if an app currently has an active bypass.
     */
    fun isAppBypassed(packageName: String): Boolean {
        val expiryTimeStr = prefs.getString(packageName, null) ?: return false
        return try {
            val expiryTime = LocalDateTime.parse(expiryTimeStr)
            LocalDateTime.now().isBefore(expiryTime)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get remaining time for a bypass in milliseconds.
     */
    fun getRemainingMillis(packageName: String): Long {
        val expiryTimeStr = prefs.getString(packageName, null) ?: return 0
        return try {
            val expiryTime = LocalDateTime.parse(expiryTimeStr)
            val now = LocalDateTime.now()
            val duration = java.time.Duration.between(now, expiryTime)
            duration.toMillis().coerceAtLeast(0)
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Create a bypass for an app and log it.
     */
    fun createBypass(packageName: String, appName: String, durationMinutes: Int, reason: String) {
        val expiryTime = LocalDateTime.now().plusMinutes(durationMinutes.toLong())
        prefs.edit().putString(packageName, expiryTime.toString()).apply()
        
        logToMarkdown(appName, packageName, durationMinutes, reason)
    }

    private fun logToMarkdown(appName: String, packageName: String, duration: Int, reason: String) {
        val logFile = File(settingsRepository.getLogFilePath())
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        
        val header = if (!logFile.exists()) {
            "| Date | App | Package | Duration | Reason |\n|------|-----|---------|----------|--------|\n"
        } else ""

        val entry = "| $timestamp | $appName | $packageName | $duration min | $reason |\n"
        
        try {
            FileOutputStream(logFile, true).use { 
                if (header.isNotEmpty()) it.write(header.toByteArray())
                it.write(entry.toByteArray()) 
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
