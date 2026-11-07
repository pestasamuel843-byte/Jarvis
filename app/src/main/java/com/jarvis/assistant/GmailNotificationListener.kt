package com.jarvis.assistant

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saves notification previews from Gmail, Messenger, Instagram, Facebook, WhatsApp and EduPage.
 * The user must explicitly grant Android notification access. This does not bypass
 * app privacy controls or fetch full conversations from those services.
 */
class GmailNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        connectedInstance = this
    }

    override fun onListenerDisconnected() {
        if (connectedInstance === this) connectedInstance = null
        super.onListenerDisconnected()
    }

    private fun latestSender(appName: String?): String? {
        val packages = mapOf(
            "Gmail" to "com.google.android.gm",
            "Messenger" to "com.facebook.orca",
            "Instagram" to "com.instagram.android",
            "Facebook" to "com.facebook.katana",
            "WhatsApp" to "com.whatsapp",
            "EduPage" to "air.org.edupage",
            "EduPage Alt" to "air.org.edupagealt"
        )
        val targetPackage = packages[appName]
        return runCatching { activeNotifications?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .filter { targetPackage == null || it.packageName == targetPackage }
            .sortedByDescending { it.postTime }
            .firstNotNullOfOrNull { it.notification.extras?.getCharSequence("android.title")?.toString()?.trim()?.takeIf(String::isNotBlank) }
    }

    private fun sendInlineReply(appName: String?, sender: String, reply: String): Boolean {
        val packages = mapOf(
            "Gmail" to "com.google.android.gm",
            "Messenger" to "com.facebook.orca",
            "Instagram" to "com.instagram.android",
            "Facebook" to "com.facebook.katana",
            "WhatsApp" to "com.whatsapp",
            "EduPage" to "air.org.edupage",
            "EduPage Alt" to "air.org.edupagealt"
        )
        val targetPackage = packages[appName]
        val notifications = runCatching { activeNotifications?.toList().orEmpty() }.getOrDefault(emptyList())
            .filter { (targetPackage == null || it.packageName == targetPackage) &&
                it.notification.extras?.getCharSequence("android.title")?.toString()?.trim() == sender }
            .sortedByDescending { it.postTime }

        for (sbn in notifications) {
            val actions = sbn.notification.actions ?: continue
            for (action in actions) {
                val remoteInputs = action.remoteInputs ?: continue
                val usableInputs = remoteInputs.filter { it.allowFreeFormInput }
                if (usableInputs.isEmpty()) continue
                val fillIn = Intent()
                val results = Bundle()
                usableInputs.forEach { results.putCharSequence(it.resultKey, reply) }
                RemoteInput.addResultsToIntent(usableInputs.toTypedArray(), fillIn, results)
                return runCatching {
                    action.actionIntent.send(this, 0, fillIn)
                    true
                }.getOrDefault(false)
            }
        }
        return false
    }

    companion object {
        @Volatile private var connectedInstance: GmailNotificationListener? = null

        fun latestSender(appName: String?): String? = connectedInstance?.latestSender(appName)

        fun sendReply(appName: String?, sender: String, reply: String): Boolean {
            if (sender.isBlank() || reply.isBlank()) return false
            return connectedInstance?.sendInlineReply(appName, sender, reply) ?: false
        }
    }

    private val supportedApps = mapOf(
        "com.google.android.gm" to "Gmail",
        "com.facebook.orca" to "Messenger",
        "com.instagram.android" to "Instagram",
        "com.facebook.katana" to "Facebook",
            "com.whatsapp" to "WhatsApp",
            "air.org.edupage" to "EduPage",
            "air.org.edupagealt" to "EduPage"
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
