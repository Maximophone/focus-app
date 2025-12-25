package com.example.focusapp

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class FocusAccessibilityService : AccessibilityService() {
    
    private lateinit var policyRepository: PolicyRepository
    private lateinit var bypassManager: BypassManager
    
    private var lastEnforcedPackage: String? = null
    private var lastEnforcedTime: Long = 0
    private var currentForegroundPackage: String? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private val scheduledExpiries = mutableSetOf<String>()
    
    // Cooldown period to prevent rapid re-triggering (5 seconds)
    private val enforcementCooldownMs = 5000L
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        policyRepository = PolicyRepository(this)
        bypassManager = BypassManager(this)
        Log.d(TAG, "Focus Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventPackage = event.packageName?.toString() ?: return
        
        // Ignore our own activities entirely
        if (eventPackage == this.packageName) return
        
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentForegroundPackage = eventPackage
            Log.d(TAG, "Foreground: $currentForegroundPackage")
            
            if (!isExcludedPackage(eventPackage)) {
                // Check if this app needs blocking
                handleAppLaunch(eventPackage)
            }
        }
    }

    private fun handleAppLaunch(packageName: String) {
        if (!policyRepository.isAppCurrentlyBlocked(packageName)) return
        
        if (bypassManager.isAppBypassed(packageName)) {
            Log.d(TAG, "$packageName is bypassed. Scheduling expiry.")
            scheduleExpiryCheck(packageName)
            return
        }
        
        // Check cooldown to prevent rapid re-triggering
        if (isInCooldown(packageName)) {
            Log.d(TAG, "Skipping enforcement for $packageName (in cooldown)")
            return
        }
        
        Log.d(TAG, "Blocking $packageName")
        enforceBlock(packageName)
    }

    private fun isInCooldown(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        return packageName == lastEnforcedPackage && 
               (now - lastEnforcedTime) < enforcementCooldownMs
    }

    private fun enforceBlock(packageName: String) {
        lastEnforcedPackage = packageName
        lastEnforcedTime = System.currentTimeMillis()
        
        // Go home
        performGlobalAction(GLOBAL_ACTION_HOME)
        
        // Get app name
        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) { packageName }
        
        // Launch bypass prompt after a short delay
        handler.postDelayed({
            startActivity(BypassActivity.createIntent(this, packageName, appName))
        }, 300)
    }

    private fun scheduleExpiryCheck(packageName: String) {
        if (scheduledExpiries.contains(packageName)) return
        
        val remainingMillis = bypassManager.getRemainingMillis(packageName)
        if (remainingMillis <= 0) return

        Log.d(TAG, "Scheduling expiry for $packageName in ${remainingMillis / 1000}s")
        scheduledExpiries.add(packageName)
        
        handler.postDelayed({
            scheduledExpiries.remove(packageName)
            onBypassExpired(packageName)
        }, remainingMillis + 500)
    }

    private fun onBypassExpired(packageName: String) {
        Log.d(TAG, "Bypass expired for $packageName")
        
        // Check if the app is still blocked by policy
        if (!policyRepository.isAppCurrentlyBlocked(packageName)) return
        
        // Check if user created a new bypass in the meantime
        if (bypassManager.isAppBypassed(packageName)) {
            Log.d(TAG, "$packageName has a new bypass. Rescheduling.")
            scheduleExpiryCheck(packageName)
            return
        }
        
        // Check if app is still in foreground
        if (currentForegroundPackage == packageName) {
            Log.d(TAG, "$packageName still in foreground after expiry. Enforcing.")
            enforceBlock(packageName)
        } else {
            Log.d(TAG, "$packageName no longer in foreground. No action needed.")
        }
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
