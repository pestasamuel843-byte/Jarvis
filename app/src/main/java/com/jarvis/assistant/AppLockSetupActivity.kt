package com.jarvis.assistant

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.security.MessageDigest

class AppLockSetupActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(AppLockAccessibilityService.PREFS, MODE_PRIVATE) }
    private lateinit var pinInput: EditText
    private lateinit var selectedLabel: TextView
    private val selectedPackages = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedPackages.addAll(prefs.getStringSet(AppLockAccessibilityService.KEY_PACKAGES, emptySet()).orEmpty())
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 34, 28, 28)
            setBackgroundColor(Color.rgb(5, 11, 19))
        }
        fun label(text: String, size: Float = 16f) = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = size
            setPadding(0, 12, 0, 12)
        }
        root.addView(label("J.A.R.V.I.S.  /  ZABEZPEČENIE APLIKÁCIÍ", 20f))
        root.addView(label("Nastav PIN a vyber aplikácie, ktoré chceš chrániť. Zámok funguje iba po povolení služby prístupnosti.", 14f))
        pinInput = EditText(this).apply {
            hint = "Nový PIN (4 až 12 číslic)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        root.addView(pinInput, LinearLayout.LayoutParams(-1, -2))
        selectedLabel = label("Vybrané aplikácie: ${selectedPackages.size}", 14f)
        root.addView(selectedLabel)
        val choose = Button(this).apply { text = "VYBRAŤ APLIKÁCIE" }
        choose.setOnClickListener { chooseApps() }
        root.addView(choose, LinearLayout.LayoutParams(-1, -2))
        val save = Button(this).apply { text = "ULOŽIŤ PIN A VÝBER" }
        save.setOnClickListener { saveSettings() }
        root.addView(save, LinearLayout.LayoutParams(-1, -2))
        val enable = Button(this).apply { text = "POVOLIŤ SLUŽBU PRÍSTUPNOSTI" }
        enable.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        root.addView(enable, LinearLayout.LayoutParams(-1, -2))
        val activate = Button(this).apply { text = "ZAPNÚŤ ZAMYKANIE" }
        activate.setOnClickListener {
            if (!AppLockAccessibilityService.isEnabled(this)) {
                Toast.makeText(this, "Najprv povoľ službu JARVIS v nastaveniach prístupnosti.", Toast.LENGTH_LONG).show()
            } else if (prefs.getString(AppLockAccessibilityService.KEY_PIN_HASH, "").isNullOrBlank() || selectedPackages.isEmpty()) {
                Toast.makeText(this, "Najprv nastav PIN a vyber aplikácie.", Toast.LENGTH_LONG).show()
            } else {
                prefs.edit().putBoolean(AppLockAccessibilityService.KEY_ENABLED, true).apply()
                Toast.makeText(this, "Ochrana aplikácií je zapnutá.", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(activate, LinearLayout.LayoutParams(-1, -2))
        val disable = Button(this).apply { text = "VYPNÚŤ ZAMYKANIE" }
        disable.setOnClickListener {
            prefs.edit().putBoolean(AppLockAccessibilityService.KEY_ENABLED, false).apply()
            Toast.makeText(this, "Ochrana aplikácií je vypnutá.", Toast.LENGTH_SHORT).show()
        }
        root.addView(disable, LinearLayout.LayoutParams(-1, -2))
        setContentView(root, ViewGroup.LayoutParams(-1, -1))
    }

    private fun chooseApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        val labels = apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()
        val packages = apps.map { it.activityInfo.packageName }
        val checked = BooleanArray(packages.size) { selectedPackages.contains(packages[it]) }
        android.app.AlertDialog.Builder(this)
            .setTitle("Aplikácie na uzamknutie")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                if (isChecked) selectedPackages.add(packages[which]) else selectedPackages.remove(packages[which])
                selectedLabel.text = "Vybrané aplikácie: ${selectedPackages.size}"
            }
            .setPositiveButton("HOTOVO", null)
            .show()
    }

    private fun saveSettings() {
        val pin = pinInput.text.toString()
        val oldHash = prefs.getString(AppLockAccessibilityService.KEY_PIN_HASH, "").orEmpty()
        if (pin.isNotBlank() && (pin.length !in 4..12 || pin.any { !it.isDigit() })) {
            Toast.makeText(this, "PIN musí mať 4 až 12 číslic.", Toast.LENGTH_LONG).show()
            return
        }
        if (oldHash.isBlank() && pin.isBlank()) {
            Toast.makeText(this, "Najprv zadaj PIN.", Toast.LENGTH_SHORT).show()
            return
        }
        val editor = prefs.edit().putStringSet(AppLockAccessibilityService.KEY_PACKAGES, selectedPackages.toSet())
        if (pin.isNotBlank()) editor.putString(AppLockAccessibilityService.KEY_PIN_HASH, sha256(pin))
        editor.apply()
        pinInput.text?.clear()
        Toast.makeText(this, "Nastavenia ochrany uložené.", Toast.LENGTH_SHORT).show()
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
