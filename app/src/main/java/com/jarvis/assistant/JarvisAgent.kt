package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import java.util.Locale

/** Deterministic Android tool router with an explicit, user-controlled autonomous task mode. */
object JarvisAgent {
    private const val AUTONOMOUS_PREFS = "jarvis_autonomous"
    private const val KEY_ACTIVE = "active"
    private const val KEY_GOAL = "goal"

    private sealed class Step {
        data class OpenApp(val name: String) : Step()
        data class Search(val query: String, val youtube: Boolean) : Step()
    }

    fun isAutonomous(context: Context): Boolean =
        context.getSharedPreferences(AUTONOMOUS_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)

    /** Context injected into the local model; the goal is kept separately from user-authored memories. */
    fun promptContext(context: Context): String {
        val prefs = context.getSharedPreferences(AUTONOMOUS_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return "AUTONOMOUS MODE: vypnutý."
        val goal = prefs.getString(KEY_GOAL, "").orEmpty()
        return buildString {
            append("AUTONOMOUS MODE: AKTÍVNY, používateľ ho výslovne zapol.\n")
            if (goal.isNotBlank()) append("AKTUÁLNY CIEĽ POUŽÍVATEĽA: ").append(goal).append("\n")
            append("Postupuj ako plánovací agent: rozdeľ cieľ na malé konkrétne kroky, urč najbližší bezpečný krok a používaj iba funkcie, ktoré aplikácia naozaj podporuje. ")
            append("Po každej vykonanej akcii over dostupný výsledok. Ak akciu nevieš vykonať alebo overiť, povedz to priamo a ponúkni najbližší uskutočniteľný krok. ")
            append("Nevydávaj plán za vykonanú prácu. Pred odoslaním správ, zdieľaním súkromných údajov, mazaním, nákupom alebo iným citlivým či nevratným úkonom si vyžiadaj výslovné potvrdenie.")
        }
    }

    fun execute(context: Context, raw: String): String? {
        val normalized = normalize(raw)
        if (normalized.isBlank()) return null
        val prefs = context.getSharedPreferences(AUTONOMOUS_PREFS, Context.MODE_PRIVATE)

        when {
            normalized in setOf(
                "aktivuj autonomous mode", "autonomous mode", "zapni autonomous mode",
                "aktivuj autonomny rezim", "zapni autonomny rezim", "autonomny rezim",
                "aktivuj adaptivnu inteligenciu"
            ) -> {
                prefs.edit().putBoolean(KEY_ACTIVE, true).remove(KEY_GOAL).apply()
                return "AUTONOMOUS MODE aktivovaný. Povedzte mi cieľ. Rozdelím ho na kroky, využijem dostupné funkcie a výsledky budem kontrolovať. Citlivé akcie najprv potvrdíte vy."
            }
            normalized in setOf(
                "vypni autonomous mode", "ukonci autonomous mode",
                "vypni autonomny rezim", "ukonci autonomny rezim", "deaktivuj autonomny rezim"
            ) -> {
                prefs.edit().putBoolean(KEY_ACTIVE, false).remove(KEY_GOAL).apply()
                return "AUTONOMOUS MODE vypnutý. JARVIS pokračuje v adaptívnom bežnom režime."
            }
            normalized in setOf("stav autonomous mode", "aky rezim je aktivny", "si v autonomnom rezime") -> {
                val active = prefs.getBoolean(KEY_ACTIVE, false)
                val goal = prefs.getString(KEY_GOAL, "").orEmpty()
                return if (active) "AUTONOMOUS MODE je aktívny." + if (goal.isBlank()) " Zatiaľ nemám zadaný cieľ." else " Aktuálny cieľ: $goal"
                else "AUTONOMOUS MODE je vypnutý."
            }
            normalized in setOf("zrus ciel", "vymaz ciel", "zabudni aktualny ciel") -> {
                prefs.edit().remove(KEY_GOAL).apply()
                return "Aktuálny cieľ som vymazal. Režim zostáva " + if (prefs.getBoolean(KEY_ACTIVE, false)) "aktívny." else "vypnutý."
            }
            normalized.startsWith("novy ciel ") || normalized.startsWith("zmen ciel na ") -> {
                if (!prefs.getBoolean(KEY_ACTIVE, false)) return "Najprv aktivujte AUTONOMOUS MODE."
                val goal = raw.substringAfter("cieľ", raw.substringAfter("ciel", "")).trim().trimStart(':', ',', ' ')
                if (goal.isBlank()) return "Povedzte, aký nový cieľ mám riešiť."
                prefs.edit().putString(KEY_GOAL, goal.take(1200)).apply()
                return null
            }
        }

        // Preserve the first goal across follow-up turns. Do not overwrite it with every short reply.
        if (prefs.getBoolean(KEY_ACTIVE, false) && prefs.getString(KEY_GOAL, "").isNullOrBlank()) {
            prefs.edit().putString(KEY_GOAL, raw.trim().take(1200)).apply()
        }

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
        val outcomes = steps.map { step ->
            when (step) {
                is Step.OpenApp -> openApp(context, step.name)
                is Step.Search -> search(context, step.query, step.youtube)
            }
        }
        if (outcomes.any { it == null }) return null
        return outcomes.filterNotNull().joinToString(" ")
    }

    private fun normalize(value: String): String = value.lowercase(Locale("sk", "SK"))
        .replace('á', 'a').replace('ä', 'a').replace('č', 'c').replace('ď', 'd')
        .replace('é', 'e').replace('í', 'i').replace('ĺ', 'l').replace('ľ', 'l')
        .replace('ň', 'n').replace('ó', 'o').replace('ô', 'o').replace('ŕ', 'r')
        .replace('š', 's').replace('ť', 't').replace('ú', 'u').replace('ý', 'y')
        .replace('ž', 'z')

    private fun openApp(context: Context, requested: String): String? {
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
            ?: return null
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
