package com.example.focusapp

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class FocusAccessibilityService : AccessibilityService() {
    
    private lateinit var policyRepository: PolicyRepository
    private lateinit var bypassManager: BypassManager
    
    private var lastBlockedPackage: String? = null
    private var lastBlockedTime: Long = 0
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        policyRepository = PolicyRepository(this)
        bypassManager = BypassManager(this)
        Log.d(TAG, "Focus Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            // Ignore our own app and system UI
            if (packageName == this.packageName || 
                packageName == "com.android.systemui" ||
                packageName == "com.google.android.apps.nexuslauncher" ||
                packageName == "com.android.launcher3" ||
                packageName == "com.google.android.inputmethod.latin") { // Keyboards
                return
            }
            
            // 1. Check if app is blocked by policy
            if (policyRepository.isAppCurrentlyBlocked(packageName)) {
                
                // 2. Check if user has an active bypass
                if (bypassManager.isAppBypassed(packageName)) {
                    Log.d(TAG, "App $packageName is currently bypassed")
                    return
                }

                val now = System.currentTimeMillis()
                // Debounce to prevent rapid activity launching
                if (packageName == lastBlockedPackage && now - lastBlockedTime < 2000) {
                    return
                }
                
                Log.d(TAG, "Blocking app: $packageName")
                lastBlockedPackage = packageName
                lastBlockedTime = now
                
                // 3. Go home FIRST to clear the blocked app
                performGlobalAction(GLOBAL_ACTION_HOME)
                
                // 4. Get readable app name for the prompt
                val appName = try {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(packageName, 0)
                    ).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    packageName
                }
                
                // 5. Launch Bypass Activity AFTER a tiny delay to ensure Home has settled
                // Using a handler or a simple thread to ensure the activity lands on top of Home
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    startActivity(BypassActivity.createIntent(this, packageName, appName))
                }, 100)
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
