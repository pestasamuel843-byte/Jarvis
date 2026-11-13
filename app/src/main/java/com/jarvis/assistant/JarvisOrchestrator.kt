package com.jarvis.assistant

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small Android-side orchestration layer inspired by OpenJarvis' separation
 * of agents and tools. It only invokes explicitly registered in-app skills.
 */
object JarvisOrchestrator {
    fun execute(
        context: Context,
        command: String,
        confirmationGranted: Boolean = false
    ): String? {
        val appContext = context.applicationContext
        val skill = JarvisSkillRegistry.find(command) ?: return null

        val denied = skill.descriptor.requiredPermissions.filter { permission ->
            appContext.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        }
        if (denied.isNotEmpty()) {
            val result = JarvisToolResult.Failure(
                "Na túto funkciu potrebujem povolenie Androidu: ${denied.joinToString()}."
            )
            record(appContext, skill.descriptor.id, result, 0L)
            return result.toUserMessage()
        }

        if (skill.descriptor.requiresNetwork && !hasInternet(appContext)) {
            val result = JarvisToolResult.Failure(
                "Táto funkcia potrebuje internetové pripojenie."
            )
            record(appContext, skill.descriptor.id, result, 0L)
            return result.toUserMessage()
        }

        if (skill.descriptor.confirmationPolicy != JarvisConfirmationPolicy.NEVER &&
            !confirmationGranted
        ) {
            val result = JarvisToolResult.NeedsConfirmation(
                "Táto akcia vyžaduje tvoje potvrdenie. Zatiaľ som ju nevykonal."
            )
            record(appContext, skill.descriptor.id, result, 0L)
            return result.toUserMessage()
        }

        val started = SystemClock.elapsedRealtime()
        val result = runCatching {
            skill.execute(appContext, command)
        }.getOrElse { error ->
            JarvisToolResult.Failure(
                "Nástroj ${skill.descriptor.id} zlyhal: ${error.message ?: "neočakávaná chyba"}."
            )
        }
        val duration = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L)
        record(appContext, skill.descriptor.id, result, duration)
        return result.toUserMessage()
    }

    private fun hasInternet(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)

    private fun record(
        context: Context,
        skillId: String,
        result: JarvisToolResult,
        durationMs: Long
    ) {
        JarvisTraceStore.record(
            context = context,
            skillId = skillId,
            status = result.traceStatus(),
            durationMs = durationMs
        )
    }
}

/**
 * Local-only, content-free trace log.
 * It deliberately stores no recognized phrase, message text, notification,
 * microphone audio, or model prompt. The newest 100 execution summaries remain
 * in app-private SharedPreferences.
 */
internal object JarvisTraceStore {
    private const val PREFS = "jarvis_agent_traces"
    private const val KEY = "events"
    private const val MAX_EVENTS = 100

    fun record(context: Context, skillId: String, status: String, durationMs: Long) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val old = runCatching { JSONArray(prefs.getString(KEY, "[]").orEmpty()) }
                .getOrDefault(JSONArray())
            val next = JSONArray()
            val start = (old.length() - (MAX_EVENTS - 1)).coerceAtLeast(0)
            for (index in start until old.length()) {
                old.optJSONObject(index)?.let { next.put(it) }
            }
            next.put(
                JSONObject()
                    .put("at", System.currentTimeMillis())
                    .put("skill", skillId)
                    .put("status", status)
                    .put("duration_ms", durationMs)
            )
            prefs.edit().putString(KEY, next.toString()).apply()
        }
    }
}
