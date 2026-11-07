package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import java.util.Locale

/** Deterministic Android tool router. Success is reported only after intent launch. */
object JarvisAgent {
    private sealed class Step {
        data class OpenApp(val name: String) : Step()
        data class Search(val query: String, val youtube: Boolean) : Step()
    }

    fun execute(context: Context, raw: String): String? {
        val normalized = normalize(raw)
        if (normalized.isBlank()) return null
        val steps = mutableListOf<Step>()
        val searchMatch = Regex("""(?i)(?:vyhľadaj|vyhladaj|hľadaj|hladaj|nájdi|najdi)\s+(.+)$""").find(raw)
        val compoundYoutube = normalized.contains("youtube") &&
            normalized.substringBefore("vyhladaj").contains("otvor") && searchMatch != null

        when {
            compoundYoutube -> {
                steps.add(Step.OpenApp("YouTube"))
                steps.add(Step.Search(searchMatch!!.groupValues[1].trim(), true))
            }
            searchMatch != null && Regex("""(?i)^(?:vyhľadaj|vyhladaj|hľadaj|hladaj|nájdi|najdi)\s+""").containsMatchIn(raw.trim()) ->
                steps.add(Step.Search(searchMatch.groupValues[1].trim(), false))
            else -> {
                val app = Regex("""(?i)^(?:otvor|spusti|zapni)\s+(.+?)\s*$""").find(raw.trim())
                    ?.groupValues?.get(1)?.trim()?.removePrefix("aplikáciu ")?.removePrefix("aplikaciu ")
                    ?: return null
                if (app.isBlank()) return null
                steps.add(Step.OpenApp(app))
            }
        }
        return steps.joinToString(" ") { step ->
            when (step) {
                is Step.OpenApp -> openApp(context, step.name)
                is Step.Search -> search(context, step.query, step.youtube)
            }
        }
    }

    private fun normalize(value: String): String = value.lowercase(Locale("sk", "SK"))
        .replace('á', 'a').replace('ä', 'a').replace('č', 'c').replace('ď', 'd')
        .replace('é', 'e').replace('í', 'i').replace('ĺ', 'l').replace('ľ', 'l')
        .replace('ň', 'n').replace('ó', 'o').replace('ô', 'o').replace('ŕ', 'r')
        .replace('š', 's').replace('ť', 't').replace('ú', 'u').replace('ý', 'y')
        .replace('ž', 'z')

    private fun openApp(context: Context, requested: String): String {
        val aliases = mapOf(
            "youtube" to "com.google.android.youtube",
            "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana",
            "messenger" to "com.facebook.orca",
            "gmail" to "com.google.android.gm",
            "chrome" to "com.android.chrome",
            "spotify" to "com.spotify.music",
            "netflix" to "com.netflix.mediaclient"
        )
        val packageName = aliases[requested.lowercase(Locale.ROOT)]
        val directIntent = packageName?.let { context.packageManager.getLaunchIntentForPackage(it) }
        if (directIntent != null) {
            return runCatching {
                directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(directIntent)
                "Aplikácia $requested bola otvorená."
            }.getOrElse { "Aplikáciu $requested sa nepodarilo otvoriť: ${it.message ?: "chyba Androidu"}." }
        }
        val wanted = normalize(requested)
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = context.packageManager.queryIntentActivities(launcher, 0).firstOrNull {
            normalize(it.loadLabel(context.packageManager).toString()).contains(wanted)
        }
        val intent = match?.let { context.packageManager.getLaunchIntentForPackage(it.activityInfo.packageName) }
            ?: return "Aplikáciu $requested som v nainštalovaných aplikáciách nenašiel."
        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Aplikácia $requested bola otvorená."
        }.getOrElse { "Aplikáciu $requested sa nepodarilo otvoriť." }
    }

    private fun search(context: Context, query: String, youtube: Boolean): String {
        if (query.isBlank()) return "Chýba výraz, ktorý mám vyhľadať."
        val base = if (youtube) "https://www.youtube.com/results?search_query=" else "https://www.google.com/search?q="
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(base + URLEncoder.encode(query, "UTF-8")))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null)
            return "Vyhľadávanie sa nepodarilo spustiť: Android nenašiel aplikáciu ani prehliadač."
        return runCatching {
            context.startActivity(intent)
            if (youtube) "Na YouTube som otvoril výsledky pre: $query."
            else "Vo webovom vyhľadávaní som otvoril výsledky pre: $query."
        }.getOrElse { "Vyhľadávanie sa nepodarilo otvoriť: ${it.message ?: "chyba Androidu"}." }
    }
}
