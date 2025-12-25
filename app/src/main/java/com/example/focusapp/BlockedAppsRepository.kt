package com.example.focusapp

import android.content.Context
import android.content.SharedPreferences

/**
 * Repository to manage the list of blocked app package names.
 * Uses SharedPreferences for simple persistent storage.
 */
class BlockedAppsRepository(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    
    /**
     * Get the set of blocked app package names.
     */
    fun getBlockedApps(): Set<String> {
        return prefs.getStringSet(KEY_BLOCKED_APPS, emptySet()) ?: emptySet()
    }
    
    /**
     * Check if a specific app is blocked.
     */
    fun isAppBlocked(packageName: String): Boolean {
        return getBlockedApps().contains(packageName)
    }
    
    /**
     * Add an app to the blocked list.
     */
    fun blockApp(packageName: String) {
        val current = getBlockedApps().toMutableSet()
        current.add(packageName)
        prefs.edit().putStringSet(KEY_BLOCKED_APPS, current).apply()
    }
    
    /**
     * Remove an app from the blocked list.
     */
    fun unblockApp(packageName: String) {
        val current = getBlockedApps().toMutableSet()
        current.remove(packageName)
        prefs.edit().putStringSet(KEY_BLOCKED_APPS, current).apply()
    }
    
    /**
     * Toggle an app's blocked status.
     */
    fun toggleApp(packageName: String) {
        if (isAppBlocked(packageName)) {
            unblockApp(packageName)
        } else {
            blockApp(packageName)
        }
    }
    
    companion object {
        private const val PREFS_NAME = "focus_app_prefs"
        private const val KEY_BLOCKED_APPS = "blocked_apps"
    }
}
