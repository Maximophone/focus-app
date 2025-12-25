package com.example.focusapp

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

class FocusAccessibilityService : AccessibilityService() {
    
    private lateinit var policyRepository: PolicyRepository
    private lateinit var bypassManager: BypassManager
    
    private var lastBlockedPackage: String? = null
    private var lastBlockedTime: Long = 0
    
    private var currentForegroundPackage: String? = null
    private val handler = Handler(Looper.getMainLooper())
    
    private val scheduledExpiries = mutableSetOf<String>()
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        policyRepository = PolicyRepository(this)
        bypassManager = BypassManager(this)
        Log.d(TAG, "Focus Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventPackage = event.packageName?.toString() ?: ""
        
        // Update current foreground package if it's a window change
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (!isExcludedPackage(eventPackage)) {
                currentForegroundPackage = eventPackage
                Log.d(TAG, "New foreground app: $currentForegroundPackage")
            }
        }

        // Always check if current app needs blocking (including PiP)
        checkAndEnforceBlockingForPackage(eventPackage)
        checkAndBlockPip()
    }

    private fun checkAndBlockPip() {
        val windows = windows ?: return
        for (window in windows) {
            if (window.isInPictureInPictureMode) {
                val pkg = window.root?.packageName?.toString() ?: continue
                if (shouldBlockApp(pkg)) {
                    Log.d(TAG, "PiP detected for blocked app $pkg. Kicking home.")
                    enforceBlock(pkg)
                    break
                }
            }
        }
    }

    private fun checkAndEnforceBlockingForPackage(packageName: String) {
        if (packageName.isEmpty() || isExcludedPackage(packageName)) return

        if (policyRepository.isAppCurrentlyBlocked(packageName)) {
            if (bypassManager.isAppBypassed(packageName)) {
                scheduleExpiryCheck(packageName)
                return
            }
            
            val now = System.currentTimeMillis()
            if (packageName == lastBlockedPackage && now - lastBlockedTime < 2000) {
                return
            }
            
            Log.d(TAG, "App $packageName is restricted and not bypassed. Enforcing.")
            enforceBlock(packageName)
        }
    }

    private fun shouldBlockApp(packageName: String): Boolean {
        return policyRepository.isAppCurrentlyBlocked(packageName) && 
               !bypassManager.isAppBypassed(packageName)
    }

    private fun enforceBlock(packageName: String) {
        lastBlockedPackage = packageName
        lastBlockedTime = System.currentTimeMillis()
        
        performGlobalAction(GLOBAL_ACTION_HOME)
        
        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) { packageName }
        
        handler.postDelayed({
            startActivity(BypassActivity.createIntent(this, packageName, appName))
        }, 300) // Increased delay slightly for stability
    }

    private fun scheduleExpiryCheck(packageName: String) {
        if (scheduledExpiries.contains(packageName)) return
        
        val remainingMillis = bypassManager.getRemainingMillis(packageName)
        if (remainingMillis <= 0) return

        Log.d(TAG, "Bypass active for $packageName. Scheduling expiry in ${remainingMillis / 1000}s")
        scheduledExpiries.add(packageName)
        
        handler.postDelayed({
            scheduledExpiries.remove(packageName)
            checkExpiryNow(packageName)
        }, remainingMillis + 500)
    }

    private fun checkExpiryNow(packageName: String) {
        Log.d(TAG, "Timer expired for $packageName. Checking if enforcement is needed.")
        
        // Use a window-based check instead of just the variable for better accuracy
        val isAppVisible = isAppInAnyWindow(packageName)
        
        if (isAppVisible && shouldBlockApp(packageName)) {
            Log.d(TAG, "App $packageName is still active after expiry. Kicking home.")
            enforceBlock(packageName)
        } else {
            Log.d(TAG, "App $packageName is no longer in foreground or still bypassed. Doing nothing.")
        }
    }

    private fun isAppInAnyWindow(packageName: String): Boolean {
        val windows = windows ?: return false
        for (window in windows) {
            // Check both full-screen and PiP windows
            val root = window.root
            if (root?.packageName?.toString() == packageName) {
                return true
            }
        }
        // Fallback to our tracked variable
        return currentForegroundPackage == packageName
    }

    private fun isExcludedPackage(packageName: String): Boolean {
        return packageName == this.packageName || 
               packageName == "com.android.systemui" ||
               packageName == "com.google.android.apps.nexuslauncher" ||
               packageName == "com.android.launcher3" ||
               packageName == "com.google.android.inputmethod.latin" ||
               packageName.isEmpty()
    }

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
        scheduledExpiries.clear()
    }
    
    companion object {
        private const val TAG = "FocusService"
    }
}
