package com.jarvis.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Defensive inventory and heuristic risk review.
 * It does NOT claim to be an antivirus or automatically disable/remove apps.
 */
internal object JarvisCyberDefense {
    private const val CHANNEL_ID = "jarvis_cyber_defense"
    private const val NOTIFICATION_ID = 7401
    private const val PREFS = "jarvis_cyber_defense"

    data class Finding(val app: String, val packageName: String, val score: Int, val signals: List<String>)

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<CyberDefenseWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "jarvis_cyber_defense_periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scan(context: Context): List<Finding> {
        val pm = context.packageManager
        val installed = runCatching {
            if (Build.VERSION.SDK_INT >= 33) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            else @Suppress("DEPRECATION") pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }.getOrDefault(emptyList())

        val enabledAccessibility = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty().split(':').filter { it.isNotBlank() }.toSet()

        val findings = mutableListOf<Finding>()
        for (info in installed) {
            val appInfo = info.applicationInfo ?: continue
            val pkg = info.packageName
            if (pkg == context.packageName || (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) continue
            val label = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(pkg)
            val signals = mutableListOf<String>()
            var score = 0

            val installer = runCatching {
                if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName
                else @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg)
            }.getOrNull()
            if (installer == null || installer !in setOf("com.android.vending", "com.google.android.packageinstaller", "com.google.android.apps.nbu.files")) {
                score += 2
                signals += "neznámy alebo mimo-obchodový zdroj inštalácie"
            }

            val requested = info.requestedPermissions.orEmpty().toSet()
            if ("android.permission.REQUEST_INSTALL_PACKAGES" in requested) {
                score += 2
                signals += "môže žiadať inštaláciu ďalších APK"
            }
            if (requested.any { it in setOf("android.permission.READ_SMS", "android.permission.RECEIVE_SMS", "android.permission.SEND_SMS", "android.permission.READ_CALL_LOG") }) {
                score += 2
                signals += "žiada prístup k SMS alebo záznamom hovorov"
            }

            val enabledService = enabledAccessibility.any { it.substringBefore('/') == pkg }
            if (enabledService) {
                score += 3
                signals += "má zapnutú službu Prístupnosti"
            }

            val declaresAccessibility = runCatching {
                val services = if (Build.VERSION.SDK_INT >= 33)
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_SERVICES.toLong())).services
                else @Suppress("DEPRECATION") pm.getPackageInfo(pkg, PackageManager.GET_SERVICES).services
                services.orEmpty().any { it.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE" }
            }.getOrDefault(false)
            if (declaresAccessibility && !enabledService) {
                score += 1
                signals += "obsahuje službu Prístupnosti"
            }

            val overlayAllowed = runCatching {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow("android:system_alert_window", appInfo.uid, pkg) == android.app.AppOpsManager.MODE_ALLOWED
            }.getOrDefault(false)
            if (overlayAllowed) {
                score += 1
                signals += "má povolené zobrazovanie cez iné aplikácie"
            }

            if (score >= 4) findings += Finding(label, pkg, score, signals)
        }
        return findings.sortedByDescending { it.score }
    }

    fun runAndNotify(context: Context): List<Finding> {
        val findings = scan(context)
        val highRisk = findings.filter { it.score >= 5 }
        val fingerprint = highRisk.joinToString("|") { it.packageName + ":" + it.score }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getString("last_alert_fingerprint", "").orEmpty()
        prefs.edit()
            .putString("last_scan_summary", "Kontrola dokončená. Podozrivé signály: ${findings.size}.")
            .putStringSet("high_risk_packages", highRisk.map { it.packageName }.toSet())
            .apply()
        if (highRisk.isNotEmpty() && fingerprint != previous) {
            notify(context, highRisk)
            prefs.edit().putString("last_alert_fingerprint", fingerprint).apply()
        } else if (highRisk.isEmpty()) {
            prefs.edit().putString("last_alert_fingerprint", "").apply()
        }
        return findings
    }

    fun onForegroundPackage(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val flagged = prefs.getStringSet("high_risk_packages", emptySet()).orEmpty()
        if (packageName !in flagged) return
        val lastPackage = prefs.getString("last_foreground_alert", "").orEmpty()
        if (lastPackage == packageName) return
        val label = runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
        val item = Finding(label, packageName, 5, listOf("aplikácia s rizikovými signálmi bola otvorená"))
        notify(context, listOf(item), foreground = true)
        prefs.edit().putString("last_foreground_alert", packageName).apply()
    }

    private fun notify(context: Context, findings: List<Finding>, foreground: Boolean = false) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "JARVIS Cyber Defense", NotificationManager.IMPORTANCE_HIGH))
        }
        val names = findings.take(3).joinToString { it.app }
        val open = PendingIntent.getActivity(
            context, 7401, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL_ID) else @Suppress("DEPRECATION") Notification.Builder(context)
        val notification = builder.setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(if (foreground) "JARVIS: otvorila sa podozrivá aplikácia" else "JARVIS: podozrivé správanie aplikácie")
            .setContentText(names)
            .setStyle(Notification.BigTextStyle().bigText(
                "Našiel som aplikácie s kombináciou rizikových signálov: $names. Nie je to dôkaz vírusu. Otvor JARVIS a skontroluj podrobnosti."
            ))
            .setContentIntent(open).setAutoCancel(true).build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }
}

internal class CyberDefenseWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = try {
        JarvisCyberDefense.runAndNotify(applicationContext)
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}
