package com.jarvis.assistant

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Local, on-device reminders. Uses an inexact alarm so no special exact-alarm
 * permission is required. Android may delay delivery under battery restrictions.
 */
internal object JarvisReminders {
    private const val CHANNEL_ID = "jarvis_reminders"

    fun schedule(context: Context, title: String, hour: Int, minute: Int): Long {
        require(title.isNotBlank() && hour in 0..23 && minute in 0..59)
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }
        val requestCode = (calendar.timeInMillis xor title.hashCode().toLong()).toInt()
        val intent = Intent(context, JarvisReminderReceiver::class.java)
            .putExtra(JarvisReminderReceiver.EXTRA_TITLE, title.take(240))
            .putExtra(JarvisReminderReceiver.EXTRA_ID, requestCode)
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
        return calendar.timeInMillis
    }
}

class JarvisReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val title = intent?.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    "jarvis_reminders",
                    "Pripomienky JARVIS",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Lokálne pripomienky vytvorené hlasovým príkazom" }
            )
        }
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, "jarvis_reminders")
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }
        val openApp = PendingIntent.getActivity(
            context, 7201,
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            intent.getIntExtra(EXTRA_ID, 7201),
            notification
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("JARVIS • Pripomienka")
                .setContentText(title)
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .build()
        )
    }

    companion object {
        const val EXTRA_TITLE = "reminder_title"
        const val EXTRA_ID = "reminder_id"
    }
}
