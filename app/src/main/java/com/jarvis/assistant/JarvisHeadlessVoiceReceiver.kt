package com.jarvis.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.concurrent.Executors

/**
 * Receives speech commands even when the Compose screen has been dismissed.
 * Supported quick actions are executed headlessly. Other commands are safely
 * queued and replayed by the normal command engine when JARVIS is reopened.
 */
class JarvisHeadlessVoiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != JarvisVoiceService.ACTION_COMMAND) return
        val command = intent.getStringExtra(JarvisVoiceService.EXTRA_COMMAND)?.trim().orEmpty()
        if (command.isBlank()) return

        val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        // The Compose receiver owns commands while the app UI is attached.
        if (prefs.getBoolean("voice_ui_attached", false)) return

        val pending = goAsync()
        executor.execute {
            try {
                val quickResult = runCatching { JarvisAgent.execute(context, command) }.getOrNull()
                if (!quickResult.isNullOrBlank()) {
                    showNotification(context, "JARVIS vykonal príkaz", quickResult)
                } else {
                    prefs.edit().putString("pending_headless_voice_command", command).apply()
                    showNotification(
                        context,
                        "JARVIS zachytil váš príkaz",
                        "Príkaz je uložený. Otvorte JARVIS a automaticky ho spracujem."
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "JARVIS hlasové príkazy", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val openApp = PendingIntent.getActivity(
            context,
            812,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        })
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "jarvis_headless_commands"
        private const val NOTIFICATION_ID = 812
        private val executor = Executors.newSingleThreadExecutor()
    }
}
