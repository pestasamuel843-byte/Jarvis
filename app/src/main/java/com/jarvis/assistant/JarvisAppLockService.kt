package com.jarvis.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.content.Intent

/**
 * User-enabled accessibility bridge for explicit screen actions, plus optional app locking.
 * Screen text is inspected only when a voice command requests it.
 */
class JarvisAppLockService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        JarvisAccessibilityController.attach(this)
    }

    override fun onDestroy() {
        JarvisAccessibilityController.detach(this)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val foreground = event.packageName?.toString() ?: return
        val verification = getSharedPreferences("jarvis_action_verification", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        verification.edit()
            .putString("last_foreground_package", foreground)
            .putLong("last_foreground_at", now)
            .apply()
        val expectedPackage = verification.getString("pending_package", "").orEmpty()
        val pendingSince = verification.getLong("pending_since", 0L)
        if (expectedPackage.isNotBlank() && foreground == expectedPackage && now >= pendingSince) {
            verification.edit().remove("pending_package").remove("pending_since").apply()
            sendBroadcast(
                Intent(ACTION_VERIFIED)
                    .setPackage(packageName)
                    .putExtra(EXTRA_PACKAGE, foreground)
            )
        }
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

    companion object {
        const val ACTION_VERIFIED = "com.jarvis.assistant.ACTION_VERIFIED"
        const val EXTRA_PACKAGE = "verified_package"
    }
}
