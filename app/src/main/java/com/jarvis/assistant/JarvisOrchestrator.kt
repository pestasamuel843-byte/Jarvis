package com.jarvis.assistant

import android.content.Context
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small Android-side orchestration layer inspired by OpenJarvis' separation
 * of agents and tools. It only invokes explicitly registered in-app skills.
 */
object JarvisOrchestrator {
    fun execute(context: Context, command: String): String? {
        val skill = JarvisSkillRegistry.find(command) ?: return null
        val started = SystemClock.elapsedRealtime()
        val result = runCatching {
            skill.execute(context.applicationContext, command)
        }.getOrElse { error ->
            JarvisToolResult.Failure(
                "Nástroj ${skill.descriptor.id} zlyhal: ${error.message ?: "neočakávaná chyba"}."
            )
        }
        val duration = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L)
        JarvisTraceStore.record(
            context = context.applicationContext,
            skillId = skill.descriptor.id,
            status = result.traceStatus(),
            durationMs = duration
        )
        return result.toUserMessage()
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
                old.optJSONObject(index)?.let(next::put)
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
