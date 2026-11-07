package com.jarvis.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.content.Intent

/**
 * Optional app-lock service. It only watches the foreground package name;
 * it does not read screen text or window contents.
 */
class JarvisAppLockService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val foreground = event.packageName?.toString() ?: return
        val prefs = getSharedPreferences("jarvis_app_lock", MODE_PRIVATE)
        val protected = prefs.getStringSet("protected_packages", emptySet()).orEmpty()
        val unlocked = prefs.getString("unlocked_package", "").orEmpty()

        if (foreground == packageName) return
        if (foreground != unlocked && unlocked.isNotBlank()) {
            prefs.edit().remove("unlocked_package").apply()
        }
        if (foreground in protected && foreground != unlocked) {
            val launch = Intent(this, JarvisLockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(JarvisLockActivity.EXTRA_PACKAGE, foreground)
            runCatching { startActivity(launch) }
        }
    }

    override fun onInterrupt() = Unit
}
