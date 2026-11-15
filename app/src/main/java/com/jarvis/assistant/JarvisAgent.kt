package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import java.net.URLEncoder
import java.util.Locale

/** Explicit Android app bridge: launches apps and hands searches/drafts to public Android intents. */
object JarvisAgent {
    private const val AUTONOMOUS_PREFS = "jarvis_autonomous"
    private const val KEY_ACTIVE = "active"
    private const val KEY_GOAL = "goal"

    private sealed class Step {
        data class OpenApp(val name: String) : Step()
        data class Search(val query: String, val target: SearchTarget) : Step()
        data class EmailDraft(val subject: String, val body: String) : Step()
    }
    private enum class SearchTarget { WEB, YOUTUBE, MAPS, SPOTIFY }
    private data class AppTarget(val packageName: String, val webUrl: String? = null)

    private val appTargets = mapOf(
        "youtube" to AppTarget("com.google.android.youtube", "https://www.youtube.com/"),
        "instagram" to AppTarget("com.instagram.android", "https://www.instagram.com/"),
        "facebook" to AppTarget("com.facebook.katana", "https://www.facebook.com/"),
        "messenger" to AppTarget("com.facebook.orca", "https://www.messenger.com/"),
        "gmail" to AppTarget("com.google.android.gm", "https://mail.google.com/"),
        "email" to AppTarget("com.google.android.gm", "https://mail.google.com/"),
        "chrome" to AppTarget("com.android.chrome", "https://www.google.com/"),
        "spotify" to AppTarget("com.spotify.music", "https://open.spotify.com/"),
        "netflix" to AppTarget("com.netflix.mediaclient", "https://www.netflix.com/"),
        "mapy" to AppTarget("com.google.android.apps.maps", "https://maps.google.com/"),
        "google maps" to AppTarget("com.google.android.apps.maps", "https://maps.google.com/"),
        "maps" to AppTarget("com.google.android.apps.maps", "https://maps.google.com/"),
        "whatsapp" to AppTarget("com.whatsapp", "https://web.whatsapp.com/"),
        "tiktok" to AppTarget("com.zhiliaoapp.musically", "https://www.tiktok.com/"),
        "telegram" to AppTarget("org.telegram.messenger", "https://web.telegram.org/"),
        "discord" to AppTarget("com.discord", "https://discord.com/app"),
        "teams" to AppTarget("com.microsoft.teams", "https://teams.microsoft.com/"),
        "zoom" to AppTarget("us.zoom.videomeetings", "https://zoom.us/"),
        "drive" to AppTarget("com.google.android.apps.drive", "https://drive.google.com/"),
        "google drive" to AppTarget("com.google.android.apps.drive", "https://drive.google.com/"),
        "calendar" to AppTarget("com.google.android.calendar", "https://calendar.google.com/"),
        "google calendar" to AppTarget("com.google.android.calendar", "https://calendar.google.com/"),
        "edupage" to AppTarget("com.edupage.student", "https://www.edupage.org/"),
        "chatgpt" to AppTarget("com.openai.chatgpt", "https://chatgpt.com/"),
        "fotoaparat" to AppTarget("com.android.camera2"),
        "kamera" to AppTarget("com.android.camera2"),
        "kalkulacka" to AppTarget("com.google.android.calculator")
    )

    fun isAutonomous(context: Context): Boolean =
        context.getSharedPreferences(AUTONOMOUS_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)

    fun promptContext(context: Context): String {
        val prefs = context.getSharedPreferences(AUTONOMOUS_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return "AUTONOMOUS MODE: vypnutý."
        val goal = prefs.getString(KEY_GOAL, "").orEmpty()
        return buildString {
            append("AUTONOMOUS MODE: AKTÍVNY, používateľ ho výslovne zapol.\n")
            if (goal.isNotBlank()) append("AKTUÁLNY CIEĽ POUŽÍVATEĽA: ").append(goal).append("\n")
            append("Postupuj po malých krokoch a používaj iba Android akcie, ktoré aplikácia naozaj podporuje. ")
            append("Po každej akcii over dostupný výsledok. Otvorenie aplikácie neznamená prístup k jej súkromnému obsahu. ")
            append("Súkromné správy čítaj iba cez výslovne povolené oznámenia alebo oficiálne prepojenie. ")
            append("Pred odoslaním správy, zdieľaním údajov, mazaním alebo nákupom si vyžiadaj potvrdenie.")
        }
    }

    fun execute(context: Context, raw: String): String? {
        JarvisAccessibilityController.execute(context, raw)?.let { return it }
        val normalized = normalize(raw)
        if (normalized in setOf("otvor nastavenia pristupnosti", "otvor pristupnost", "zapni pristupnost jarvis", "nastavenia pristupnosti")) {
            return runCatching {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Otvoril som nastavenia Prístupnosti. Nájdite JARVIS a povoľte jeho službu, ak chcete ovládanie obrazovky."
            }.getOrElse { "Nastavenia Prístupnosti sa nepodarilo otvoriť: ${it.message ?: "chyba Androidu"}." }
        }
        if (normalized.isBlank()) return null
        val prefs = context.getSharedPreferences(AUTONOMOUS_PREFS, Context.MODE_PRIVATE)

        when {
            normalized in setOf("aktivuj autonomous mode", "autonomous mode", "zapni autonomous mode",
                "aktivuj autonomny rezim", "zapni autonomny rezim", "autonomny rezim", "aktivuj adaptivnu inteligenciu") -> {
                prefs.edit().putBoolean(KEY_ACTIVE, true).remove(KEY_GOAL).apply()
                return "AUTONOMOUS MODE aktivovaný. Povedzte mi cieľ. Citlivé akcie najprv potvrdíte vy."
            }
            normalized in setOf("vypni autonomous mode", "ukonci autonomous mode", "vypni autonomny rezim",
                "ukonci autonomny rezim", "deaktivuj autonomny rezim") -> {
                prefs.edit().putBoolean(KEY_ACTIVE, false).remove(KEY_GOAL).apply()
                return "AUTONOMOUS MODE vypnutý."
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
            normalized.startsWith("novy ciel ") || normalized.startsWith("novy ciel:") || normalized.startsWith("zmen ciel na ") -> {
                if (!prefs.getBoolean(KEY_ACTIVE, false)) return "Najprv aktivujte AUTONOMOUS MODE."
                val match = Regex("""(?i)^(?:nový|novy)\s+c(?:ieľ|iel)\s*[:,-]?\s*(.+)$|^zmeň\s+c(?:ieľ|iel)\s+na\s+(.+)$""").find(raw.trim())
                val goal = (match?.groupValues?.getOrNull(1).orEmpty().ifBlank { match?.groupValues?.getOrNull(2).orEmpty() }).trim()
                if (goal.isBlank()) return "Povedzte, aký nový cieľ mám riešiť."
                prefs.edit().putString(KEY_GOAL, goal.take(1200)).apply()
                return "Nový cieľ som uložil."
            }
        }

        if (prefs.getBoolean(KEY_ACTIVE, false) && prefs.getString(KEY_GOAL, "").isNullOrBlank()) {
            prefs.edit().putString(KEY_GOAL, raw.trim().take(1200)).apply()
        }

        val steps = mutableListOf<Step>()
        val emailMatch = Regex("""(?is)^(?:napíš|napis|vytvor)\s+(?:nový\s+)?email(?:\s+s\s+predmetom\s+(.+?))?(?:\s+a\s+textom\s+(.+))?$""").find(raw.trim())
        val searchMatch = Regex("""(?i)^(?:vyhľadaj|vyhladaj|hľadaj|hladaj|nájdi|najdi)\s+(.+)$""").find(raw.trim())
        val compoundYoutubeMatch = Regex("""(?i)^(?:otvor|spusti)\s+youtube\s+a\s+(?:vyhľadaj|vyhladaj|hľadaj|hladaj|nájdi|najdi)\s+(.+)$""").find(raw.trim())

        when {
            emailMatch != null -> steps.add(Step.EmailDraft(emailMatch.groupValues.getOrNull(1).orEmpty(), emailMatch.groupValues.getOrNull(2).orEmpty()))
            compoundYoutubeMatch != null -> {
                steps.add(Step.OpenApp("YouTube"))
                steps.add(Step.Search(compoundYoutubeMatch.groupValues[1].trim(), SearchTarget.YOUTUBE))
            }
            searchMatch != null -> {
                var query = searchMatch.groupValues[1].trim()
                val target = when {
                    Regex("""(?i)\s+(?:na\s+)?youtube$""").containsMatchIn(query) -> SearchTarget.YOUTUBE
                    Regex("""(?i)\s+(?:v\s+)?(?:google\s+)?maps$""").containsMatchIn(query) -> SearchTarget.MAPS
                    Regex("""(?i)\s+(?:na\s+)?spotify$""").containsMatchIn(query) -> SearchTarget.SPOTIFY
                    else -> SearchTarget.WEB
                }
                query = query.replace(Regex("""(?i)\s+(?:na\s+)?youtube$"""), "")
                    .replace(Regex("""(?i)\s+(?:v\s+)?(?:google\s+)?maps$"""), "")
                    .replace(Regex("""(?i)\s+(?:na\s+)?spotify$"""), "").trim()
                steps.add(Step.Search(query, target))
            }
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
                is Step.Search -> search(context, step.query, step.target)
                is Step.EmailDraft -> openEmailDraft(context, step.subject, step.body)
            }
        }
        return if (outcomes.any { it == null }) null else outcomes.filterNotNull().joinToString(" ")
    }

    private fun normalize(value: String): String = value.lowercase(Locale("sk", "SK"))
        .replace('á', 'a').replace('ä', 'a').replace('č', 'c').replace('ď', 'd')
        .replace('é', 'e').replace('í', 'i').replace('ĺ', 'l').replace('ľ', 'l')
        .replace('ň', 'n').replace('ó', 'o').replace('ô', 'o').replace('ŕ', 'r')
        .replace('š', 's').replace('ť', 't').replace('ú', 'u').replace('ý', 'y')
        .replace('ž', 'z')

    private fun openApp(context: Context, requested: String): String? {
        val wanted = normalize(requested).removePrefix("aplikaciu ").trim()
        val target = appTargets[wanted]
        if (target != null) {
            val launch = context.packageManager.getLaunchIntentForPackage(target.packageName)
            if (launch != null) return runCatching {
                context.getSharedPreferences("jarvis_action_verification", Context.MODE_PRIVATE).edit()
                    .putString("pending_package", target.packageName)
                    .putLong("pending_since", System.currentTimeMillis())
                    .apply()
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                "Požiadavku na otvorenie $requested som odovzdal Androidu. Overujem, či sa aplikácia skutočne zobrazila."
            }.getOrElse {
                context.getSharedPreferences("jarvis_action_verification", Context.MODE_PRIVATE).edit()
                    .remove("pending_package").remove("pending_since").apply()
                "Aplikáciu $requested sa nepodarilo otvoriť: ${it.message ?: "chyba Androidu"}."
            }
            if (target.webUrl != null) return openUrl(context, target.webUrl,
                "Aplikácia $requested nie je nainštalovaná; otvoril som jej webovú verziu.")
        }

        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = context.packageManager.queryIntentActivities(launcher, 0).firstOrNull {
            normalize(it.loadLabel(context.packageManager).toString()).contains(wanted)
        }
        val launch = match?.let { context.packageManager.getLaunchIntentForPackage(it.activityInfo.packageName) }
        if (launch != null) return runCatching {
            context.getSharedPreferences("jarvis_action_verification", Context.MODE_PRIVATE).edit()
                .putString("pending_package", match!!.activityInfo.packageName)
                .putLong("pending_since", System.currentTimeMillis())
                .apply()
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            "Požiadavku na otvorenie $requested som odovzdal Androidu. Overujem, či sa aplikácia skutočne zobrazila."
        }.getOrElse {
            context.getSharedPreferences("jarvis_action_verification", Context.MODE_PRIVATE).edit()
                .remove("pending_package").remove("pending_since").apply()
            "Aplikáciu $requested sa nepodarilo otvoriť."
        }
        return "Aplikáciu $requested som v tablete nenašiel. Skontrolujte jej názov alebo inštaláciu."
    }

    private fun search(context: Context, query: String, target: SearchTarget): String {
        if (query.isBlank()) return "Chýba výraz, ktorý mám vyhľadať."
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = when (target) {
            SearchTarget.YOUTUBE -> "https://www.youtube.com/results?search_query=$encoded"
            SearchTarget.MAPS -> "https://www.google.com/maps/search/?api=1&query=$encoded"
            SearchTarget.SPOTIFY -> "https://open.spotify.com/search/$encoded"
            SearchTarget.WEB -> "https://www.google.com/search?q=$encoded"
        }
        val message = when (target) {
            SearchTarget.YOUTUBE -> "Otváram výsledky YouTube pre: $query."
            SearchTarget.MAPS -> "Otváram mapové výsledky pre: $query."
            SearchTarget.SPOTIFY -> "Otváram vyhľadávanie Spotify pre: $query."
            SearchTarget.WEB -> "Otváram webové výsledky pre: $query."
        }
        return openUrl(context, url, message)
    }

    private fun openEmailDraft(context: Context, subject: String, body: String): String {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            "Otvoril som nový e-mail ako koncept. Pred odoslaním ho skontrolujte."
        }.getOrElse { "E-mailovú aplikáciu sa nepodarilo otvoriť: ${it.message ?: "chyba Androidu"}." }
    }

    private fun openUrl(context: Context, url: String, success: String): String =
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val targetPackage = intent.resolveActivity(context.packageManager)?.packageName
            if (targetPackage != null) {
                context.getSharedPreferences("jarvis_action_verification", Context.MODE_PRIVATE).edit()
                    .putString("pending_package", targetPackage)
                    .putLong("pending_since", System.currentTimeMillis())
                    .apply()
            }
            context.startActivity(intent)
            if (targetPackage != null) "$success Overujem, či sa cieľová aplikácia skutočne zobrazila."
            else "$success Android prijal požiadavku, ale cieľovú aplikáciu neviem overiť."
        }.getOrElse {
            context.getSharedPreferences("jarvis_action_verification", Context.MODE_PRIVATE).edit()
                .remove("pending_package").remove("pending_since").apply()
            "Odkaz sa nepodarilo otvoriť: ${it.message ?: "chyba Androidu"}."
        }
}
