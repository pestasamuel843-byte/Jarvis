package com.jarvis.assistant

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saves notification previews from Gmail, Messenger, Instagram and Facebook.
 * The user must explicitly grant Android notification access. This does not bypass
 * app privacy controls or fetch full conversations from those services.
 */
class GmailNotificationListener : NotificationListenerService() {
    private val supportedApps = mapOf(
        "com.google.android.gm" to "Gmail",
        "com.facebook.orca" to "Messenger",
        "com.instagram.android" to "Instagram",
        "com.facebook.katana" to "Facebook"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val appName = supportedApps[sbn.packageName] ?: return
        if (sbn.isGroup) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence("android.bigText")?.toString()?.trim().orEmpty()
        val lines = extras.getCharSequenceArray("android.textLines")
            ?.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
            ?.joinToString(". ").orEmpty()
        val preview = (bigText.ifBlank { lines.ifBlank { text } }).take(900)
        if (title.isBlank() && preview.isBlank()) return

        val prefs = getSharedPreferences("jarvis_gmail", MODE_PRIVATE)
        val old = runCatching {
            JSONArray(prefs.getString("previews", "[]"))
        }.getOrDefault(JSONArray())
        val next = JSONArray()
        next.put(
            JSONObject()
                .put("app", appName)
                .put("package", sbn.packageName)
                .put("title", title)
                .put("text", preview)
                .put("time", sbn.postTime)
        )
        for (i in 0 until old.length()) {
            if (next.length() >= 30) break
            val item = old.optJSONObject(i) ?: continue
            val same = item.optString("package") == sbn.packageName &&
                item.optString("title") == title &&
                item.optString("text") == preview
            if (!same) next.put(item)
        }
        prefs.edit().putString("previews", next.toString()).apply()
    }
}
