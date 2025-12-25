package com.example.focusapp

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class FocusAccessibilityService : AccessibilityService() {
    
    private lateinit var policyRepository: PolicyRepository
    private lateinit var bypassManager: BypassManager
    
    private var lastBlockedPackage: String? = null
    private var lastBlockedTime: Long = 0
    
    private var currentForegroundPackage: String? = null
    private val handler = Handler(Looper.getMainLooper())
    
    // To keep track of scheduled expiry checks
    private val scheduledExpiries = mutableMapOf<String, Runnable>()
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        policyRepository = PolicyRepository(this)
        bypassManager = BypassManager(this)
        Log.d(TAG, "Focus Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            // Log for debugging
            Log.d(TAG, "Window state changed: $packageName")
            currentForegroundPackage = packageName
            
            // Ignore our own app and system UI
            if (isExcludedPackage(packageName)) {
                return
            }
            
            checkAndEnforceBlocking(packageName)
        }
    }

    private fun checkAndEnforceBlocking(packageName: String) {
        if (policyRepository.isAppCurrentlyBlocked(packageName)) {
            
            if (bypassManager.isAppBypassed(packageName)) {
                Log.d(TAG, "App $packageName is currently bypassed")
                scheduleExpiryCheck(packageName)
                return
            }

            val now = System.currentTimeMillis()
            if (packageName == lastBlockedPackage && now - lastBlockedTime < 2000) {
                return
            }
            
            Log.d(TAG, "Blocking app: $packageName")
            lastBlockedPackage = packageName
            lastBlockedTime = now
            
            val appName = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)
                ).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName
            }
            
            // Go home first, then launch prompt
            performGlobalAction(GLOBAL_ACTION_HOME)
            handler.postDelayed({
                startActivity(BypassActivity.createIntent(this, packageName, appName))
            }, 100)
        }
    }

    private fun scheduleExpiryCheck(packageName: String) {
        // Only schedule if not already scheduled for this package
        if (scheduledExpiries.containsKey(packageName)) return
        
        val remainingMillis = bypassManager.getRemainingMillis(packageName)
        if (remainingMillis <= 0) return

        Log.d(TAG, "Scheduling expiry check for $packageName in ${remainingMillis / 1000}s")
        
        val checkRunnable = Runnable {
            scheduledExpiries.remove(packageName)
            // Only enforce if the app is still in the foreground
            if (currentForegroundPackage == packageName) {
                Log.d(TAG, "Bypass expired for $packageName while in foreground. kicking home.")
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
        
        scheduledExpiries[packageName] = checkRunnable
        handler.postDelayed(checkRunnable, remainingMillis + 500) // 500ms buffer
    }

    private fun isExcludedPackage(packageName: String): Boolean {
        return packageName == this.packageName || 
               packageName == "com.android.systemui" ||
               packageName == "com.google.android.apps.nexuslauncher" ||
               packageName == "com.android.launcher3" ||
               packageName == "com.google.android.inputmethod.latin"
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
        handler.removeCallbacksAndMessages(null)
    }
    
    companion object {
        private const val TAG = "FocusService"
    }
}
