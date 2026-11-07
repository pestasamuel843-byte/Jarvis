package com.jarvis.assistant

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.text.InputType
import java.security.MessageDigest

/**
 * Local PIN gate for apps explicitly selected by the device owner.
 * The PIN is stored as a SHA-256 digest, never as plain text.
 */
class JarvisLockActivity : Activity() {
    private var targetPackage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.setBackgroundDrawableResource(android.R.color.black)
        setFinishOnTouchOutside(false)
        render()
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        targetPackage = intent?.getStringExtra(EXTRA_PACKAGE).orEmpty()
        render()
    }

    private fun render() {
        val prefs = getSharedPreferences("jarvis_app_lock", MODE_PRIVATE)
        val storedHash = prefs.getString("pin_hash", "").orEmpty()
        val setup = storedHash.isBlank()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36, 36, 36, 36)
            setBackgroundColor(android.graphics.Color.rgb(5, 12, 22))
        }
        val title = TextView(this).apply {
            text = if (setup) "NASTAVENIE JARVIS PIN" else "APLIKÁCIA UZAMKNUTÁ"
            textSize = 22f
            setTextColor(android.graphics.Color.rgb(77, 235, 255))
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = if (setup) "Vytvorte PIN s aspoň 4 číslicami. Potom ho potvrďte." else "Zadajte PIN na pokračovanie."
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 18)
        }
        val first = EditText(this).apply {
            hint = if (setup) "Nový PIN" else "PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        val second = if (setup) EditText(this).apply {
            hint = "Potvrďte PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.LTGRAY)
            gravity = Gravity.CENTER
        } else null
        val status = TextView(this).apply {
            textSize = 13f
            setTextColor(android.graphics.Color.rgb(255, 150, 130))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 12)
        }
        val button = Button(this).apply { text = if (setup) "ULOŽIŤ PIN" else "ODOMKNÚŤ" }
        button.setOnClickListener {
            val pin = first.text.toString()
            if (setup) {
                if (pin.length < 4 || pin.length > 12 || pin.any { !it.isDigit() }) {
                    status.text = "PIN musí mať 4 až 12 číslic."
                    return@setOnClickListener
                }
                if (pin != second?.text?.toString()) {
                    status.text = "PINy sa nezhodujú."
                    return@setOnClickListener
                }
                prefs.edit().putString("pin_hash", digest(pin)).apply()
                unlock()
            } else if (digest(pin) == storedHash) {
                unlock()
            } else {
                first.text.clear()
                status.text = "Nesprávny PIN."
            }
        }
        root.addView(title)
        root.addView(subtitle)
        root.addView(first, LinearLayout.LayoutParams(-1, 58))
        if (second != null) root.addView(second, LinearLayout.LayoutParams(-1, 58))
        root.addView(status)
        root.addView(button, LinearLayout.LayoutParams(-1, 54))
        setContentView(root)
    }

    private fun unlock() {
        getSharedPreferences("jarvis_app_lock", MODE_PRIVATE).edit()
            .putString("unlocked_package", targetPackage)
            .apply()
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Keep the selected app behind its PIN gate. Home/system navigation remains available.
    }

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        const val EXTRA_PACKAGE = "target_package"
    }
}
