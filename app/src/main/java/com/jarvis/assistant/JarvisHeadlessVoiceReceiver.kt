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
import kotlinx.coroutines.runBlocking

/**
 * Receives speech commands even when the Compose screen has been dismissed.
 * Quick actions and conversational AI are processed without the Compose screen.
 */
class JarvisHeadlessVoiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == JarvisVoiceService.ACTION_WAKE) {
            if (!MainActivity.isUiAttached) {
                runCatching { showNotification(context, "JARVIS počúva", "Počúvam, pane. Povedzte svoj príkaz.") }
            }
            return
        }
        if (action != JarvisVoiceService.ACTION_COMMAND) return
        val command = intent.getStringExtra(JarvisVoiceService.EXTRA_COMMAND)?.trim().orEmpty()
        if (command.isBlank()) return
        // The Compose receiver owns commands while the app UI is attached.
        if (MainActivity.isUiAttached) return

        val pending = goAsync()
        executor.execute {
            try {
                val quickResult = runCatching { JarvisAgent.execute(context, command) }.getOrNull()
                if (!quickResult.isNullOrBlank()) {
                    deliverSpeech(context, quickResult)
                    .also { runCatching { showNotification(context, "JARVIS vykonal príkaz", quickResult) } }
                } else {
                    val answer = runCatching {
                        runBlocking { answerHeadlessVoiceCommand(context, command) }
                    }.getOrElse { "Pane, príkaz som zachytil, ale spracovanie zlyhalo: ${it.message ?: "neznáma chyba"}." }
                    deliverSpeech(context, answer)
                    runCatching { showNotification(context, "JARVIS odpovedal", answer) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun deliverSpeech(context: Context, text: String) {
        runCatching {
            context.startService(
                Intent(context, JarvisVoiceService::class.java)
                    .setAction(JarvisVoiceService.ACTION_SPEAK)
                    .putExtra(JarvisVoiceService.EXTRA_SPEECH, text)
            )
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
