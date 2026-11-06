package com.jarvis.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.content.Context.INPUT_METHOD_SERVICE
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.security.MessageDigest

/**
 * Optional user-enabled accessibility service used only to display a PIN gate
 * over apps explicitly selected by the device owner in AppLockSetupActivity.
 * It does not inspect screen text or collect accessibility node content.
 */
class AppLockAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var lockView: LinearLayout? = null
    private var lockedPackage: String? = null
    private var lastForegroundPackage: String? = null
    private val unlockedPackages = mutableSetOf<String>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (isInputMethodPackage(pkg)) return
        if (pkg == packageName) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                lastForegroundPackage?.let { unlockedPackages.remove(it) }
                lastForegroundPackage = pkg
                removeLock()
            }
            return
        }
        if (pkg != lastForegroundPackage) {
            lastForegroundPackage?.let { unlockedPackages.remove(it) }
            lastForegroundPackage = pkg
            if (lockedPackage != pkg) removeLock()
        }
        if (!prefs.getBoolean(KEY_ENABLED, false)) {
            removeLock()
            return
        }
        val targets = prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty()
        if (pkg == packageName || pkg !in targets || pkg in unlockedPackages) {
            removeLock()
            return
        }
        if (lockedPackage != pkg) showLock(pkg)
    }

    override fun onInterrupt() { removeLock() }

    private fun showLock(pkg: String) {
        if (lockView != null) return
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36, 36, 36, 36)
            setBackgroundColor(Color.rgb(5, 11, 19))
        }
        val title = TextView(this).apply {
            text = "J.A.R.V.I.S.  /  APP SECURITY"
            setTextColor(Color.rgb(77, 235, 255))
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Táto aplikácia je uzamknutá. Zadajte svoj PIN."
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }
        val pin = EditText(this).apply {
            hint = "PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setSingleLine(true)
        }
        val unlock = Button(this).apply { text = "ODOMKNÚŤ" }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2))
        root.addView(pin, LinearLayout.LayoutParams(-1, -2))
        pin.requestFocus()
        pin.post { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(pin, InputMethodManager.SHOW_IMPLICIT) }
        root.addView(unlock, LinearLayout.LayoutParams(-1, -2))
        unlock.setOnClickListener {
            val stored = getSharedPreferences(PREFS, MODE_PRIVATE)
            val expected = stored.getString(KEY_PIN_HASH, "").orEmpty()
            val salt = stored.getString(KEY_PIN_SALT, "").orEmpty()
            if (AppLockPin.verify(pin.text.toString(), salt, expected)) {
                unlockedPackages.add(pkg)
                removeLock()
            } else {
                pin.text?.clear()
                Toast.makeText(this, "Nesprávny PIN", Toast.LENGTH_SHORT).show()
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN,
            android.graphics.PixelFormat.OPAQUE
        ).apply { gravity = Gravity.CENTER }
        runCatching {
            windowManager?.addView(root, params)
            lockView = root
            lockedPackage = pkg
        }.onFailure {
            Toast.makeText(this, "Zámok aplikácie sa nepodarilo zobraziť.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeLock() {
        val view = lockView ?: run { lockedPackage = null; return }
        runCatching { windowManager?.removeView(view) }
        lockView = null
        lockedPackage = null
    }

    override fun onDestroy() {
        removeLock()
        super.onDestroy()
    }

    private fun isInputMethodPackage(pkg: String): Boolean {
        val current = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
        ).orEmpty()
        return current.substringBefore('/') == pkg
    }

    companion object {
        const val PREFS = "jarvis_app_lock"
        const val KEY_ENABLED = "enabled"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_PACKAGES = "packages"

        fun isEnabled(context: Context): Boolean {
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            val component = android.content.ComponentName(context, AppLockAccessibilityService::class.java)
            return enabled.split(':').any { it.equals(component.flattenToString(), ignoreCase = true) }
        }
    }
}
