package com.example.focusapp

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class FocusAccessibilityService : AccessibilityService() {
    
    private lateinit var policyRepository: PolicyRepository
    
    // Keep track of the last blocked app to avoid repeated blocking
    private var lastBlockedPackage: String? = null
    private var lastBlockedTime: Long = 0
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        policyRepository = PolicyRepository(this)
        Log.d(TAG, "Focus Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            // Ignore our own app and system UI
            if (packageName == this.packageName || 
                packageName == "com.android.systemui" ||
                packageName == "com.google.android.apps.nexuslauncher" ||
                packageName == "com.android.launcher3") {
                return
            }
            
            // Check if app is blocked based on active policies
            if (policyRepository.isAppCurrentlyBlocked(packageName)) {
                val now = System.currentTimeMillis()
                
                // Debounce: Don't block the same app more than once per second
                if (packageName == lastBlockedPackage && now - lastBlockedTime < 1000) {
                    return
                }
                
                Log.d(TAG, "Blocking app: $packageName")
                lastBlockedPackage = packageName
                lastBlockedTime = now
                
                // Go back to home screen
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
    }
    
    companion object {
        private const val TAG = "FocusService"
    }
}
