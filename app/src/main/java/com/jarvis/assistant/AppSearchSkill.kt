package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import java.util.Locale

/** Built-in, deterministic skill for launching apps and opening search results. */
internal class AppSearchSkill : JarvisSkill {
    override val descriptor = JarvisSkillDescriptor(
        id = "android.app_search",
        description = "Open an installed Android app or start a web/YouTube search"
    )

    private sealed class Step {
        data class OpenApp(val name: String) : Step()
        data class Search(val query: String, val youtube: Boolean) : Step()
    }

    override fun canHandle(command: String): Boolean = parse(command) != null

    override fun execute(context: Context, command: String): JarvisToolResult {
        val steps = parse(command) ?: return JarvisToolResult.Failure("Tomuto príkazu nerozumiem.")
        val messages = mutableListOf<String>()
        for (step in steps) {
            val result = when (step) {
                is Step.OpenApp -> openApp(context, step.name)
                is Step.Search -> search(context, step.query, step.youtube)
            }
            if (result is JarvisToolResult.Failure) return result
            if (result is JarvisToolResult.NeedsConfirmation) return result
            if (result is JarvisToolResult.Success) messages += result.message
        }
        return JarvisToolResult.Success(messages.joinToString(" "))
    }

    private fun parse(raw: String): List<Step>? {
        val normalized = normalize(raw)
        if (normalized.isBlank()) return null
        val searchMatch = Regex(
            """(?i)(?:vyhľadaj|vyhladaj|hľadaj|hladaj|nájdi|najdi)\s+(.+)$"""
        ).find(raw)
        val compoundYoutube = normalized.contains("youtube") &&
            normalized.substringBefore("vyhladaj").contains("otvor") &&
            searchMatch != null

        return when {
            compoundYoutube -> listOf(
                Step.OpenApp("YouTube"),
                Step.Search(searchMatch!!.groupValues[1].trim(), true)
            )
            searchMatch != null && Regex(
                """(?i)^(?:vyhľadaj|vyhladaj|hľadaj|hladaj|nájdi|najdi)\s+"""
            ).containsMatchIn(raw.trim()) ->
                listOf(Step.Search(searchMatch.groupValues[1].trim(), false))
            else -> {
                val app = Regex("""(?i)^(?:otvor|spusti|zapni)\s+(.+?)\s*$""")
                    .find(raw.trim())?.groupValues?.get(1)?.trim()
                    ?.removePrefix("aplikáciu ")?.removePrefix("aplikaciu ")
                    ?: return null
                if (app.isBlank()) null else listOf(Step.OpenApp(app))
            }
        }
    }

    private fun normalize(value: String): String = value.lowercase(Locale("sk", "SK"))
        .replace('á', 'a').replace('ä', 'a').replace('č', 'c').replace('ď', 'd')
        .replace('é', 'e').replace('í', 'i').replace('ĺ', 'l').replace('ľ', 'l')
        .replace('ň', 'n').replace('ó', 'o').replace('ô', 'o').replace('ŕ', 'r')
        .replace('š', 's').replace('ť', 't').replace('ú', 'u').replace('ý', 'y')
        .replace('ž', 'z')

    private fun openApp(context: Context, requested: String): JarvisToolResult {
        val aliases = mapOf(
            "youtube" to "com.google.android.youtube",
            "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana",
            "messenger" to "com.facebook.orca",
            "gmail" to "com.google.android.gm",
            "chrome" to "com.android.chrome",
            "spotify" to "com.spotify.music",
            "netflix" to "com.netflix.mediaclient",
            "mapy" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps"
        )
        val packageName = aliases[requested.lowercase(Locale.ROOT)]
        val directIntent = packageName?.let {
            context.packageManager.getLaunchIntentForPackage(it)
        }
        if (directIntent != null) {
            return launch(context, directIntent, requested)
        }

        val wanted = normalize(requested)
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = context.packageManager.queryIntentActivities(launcher, 0).firstOrNull {
            normalize(it.loadLabel(context.packageManager).toString()).contains(wanted)
        }
        val intent = match?.let {
            context.packageManager.getLaunchIntentForPackage(it.activityInfo.packageName)
        } ?: return JarvisToolResult.Failure(
            "Aplikáciu $requested som v nainštalovaných aplikáciách nenašiel."
        )
        return launch(context, intent, requested)
    }

    private fun launch(context: Context, intent: Intent, requested: String): JarvisToolResult =
        runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            JarvisToolResult.Success(
                "Požiadavku na otvorenie aplikácie $requested som odovzdal Androidu.",
                evidence = "startActivity returned without exception"
            )
        }.getOrElse {
            JarvisToolResult.Failure(
                "Aplikáciu $requested sa nepodarilo spustiť: ${it.message ?: "chyba Androidu"}."
            )
        }

    private fun search(context: Context, query: String, youtube: Boolean): JarvisToolResult {
        if (query.isBlank()) return JarvisToolResult.Failure("Chýba výraz, ktorý mám vyhľadať.")
        val base = if (youtube) {
            "https://www.youtube.com/results?search_query="
        } else {
            "https://www.google.com/search?q="
        }
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(base + URLEncoder.encode(query, "UTF-8"))
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (intent.resolveActivity(context.packageManager) == null) {
            return JarvisToolResult.Failure(
                "Vyhľadávanie sa nepodarilo spustiť: Android nenašiel aplikáciu ani prehliadač."
            )
        }
        return runCatching {
            context.startActivity(intent)
            val target = if (youtube) "YouTube" else "webovom vyhľadávaní"
            JarvisToolResult.Success(
                "V $target som odovzdal vyhľadávanie výrazu: $query.",
                evidence = "ACTION_VIEW intent accepted by Android"
            )
        }.getOrElse {
            JarvisToolResult.Failure(
                "Vyhľadávanie sa nepodarilo otvoriť: ${it.message ?: "chyba Androidu"}."
            )
        }
    }
}
