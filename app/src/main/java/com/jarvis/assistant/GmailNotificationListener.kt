package com.jarvis.assistant

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores only Gmail notification previews. It never opens the inbox or reads credentials.
 * The user must explicitly enable Notification access in Android settings.
 */
class GmailNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != "com.google.android.gm") return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence("android.bigText")?.toString()?.trim().orEmpty()
        val preview = (bigText.ifBlank { text }).take(700)
        if (title.isBlank() && preview.isBlank()) return
        val prefs = getSharedPreferences("jarvis_gmail", MODE_PRIVATE)
        val old = runCatching { JSONArray(prefs.getString("previews", "[]")) }.getOrDefault(JSONArray())
        val next = JSONArray()
        next.put(JSONObject().put("title", title).put("text", preview).put("time", sbn.postTime))
        for (i in 0 until old.length()) {
            if (next.length() >= 20) break
            val item = old.optJSONObject(i) ?: continue
            if (item.optString("title") != title || item.optString("text") != preview) next.put(item)
        }
        prefs.edit().putString("previews", next.toString()).apply()
    }
}
