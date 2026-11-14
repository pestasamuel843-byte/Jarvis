package com.jarvis.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** User-authored personal context, stored locally. Never invent traits or diagnoses. */
internal object JarvisPersonalProfiles {
    private const val PREFS = "jarvis_personal_profiles"

    fun addPersonNote(context: Context, nameRaw: String, noteRaw: String): String {
        val name = nameRaw.trim().take(70)
        val note = noteRaw.trim().take(900)
        require(name.isNotBlank() && note.isNotBlank()) { "Zadajte meno aj poznámku." }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val profiles = readProfiles(prefs)
        val existing = (0 until profiles.length()).mapNotNull { profiles.optJSONObject(it) }
            .firstOrNull { it.optString("name").equals(name, true) }
        val profile = existing ?: JSONObject().put("name", name).put("notes", "")
        val old = profile.optString("notes").trim()
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        profile.put("notes", (old.lines().filter { it.isNotBlank() } + "$date: $note").takeLast(12).joinToString("\n"))
        profile.put("updated", System.currentTimeMillis())
        if (existing == null) profiles.put(profile)
        while (profiles.length() > 30) profiles.remove(0)
        prefs.edit().putString("people", profiles.toString()).apply()
        return "Poznámku o $name som uložil lokálne. Ide o informáciu, ktorú ste mi sami poskytli."
    }

    fun personSummary(context: Context, nameRaw: String): String {
        val name = nameRaw.trim()
        val profiles = readProfiles(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
        val profile = (0 until profiles.length()).mapNotNull { profiles.optJSONObject(it) }
            .firstOrNull { it.optString("name").equals(name, true) }
            ?: return "O osobe $name zatiaľ nemám uložené žiadne poznámky."
        return "Čo mám uložené o ${profile.optString("name")}: " +
            profile.optString("notes").ifBlank { "Zatiaľ bez poznámok." } +
            ". Tieto poznámky sú vaše pozorovania, nie overený psychologický profil."
    }

    fun removePerson(context: Context, nameRaw: String): String {
        val name = nameRaw.trim()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val profiles = readProfiles(prefs)
        val next = JSONArray()
        var removed = false
        for (i in 0 until profiles.length()) {
            val item = profiles.optJSONObject(i) ?: continue
            if (!removed && item.optString("name").equals(name, true)) {
                removed = true
            } else next.put(item)
        }
        prefs.edit().putString("people", next.toString()).apply()
        return if (removed) "Profil osoby $name som odstránil." else "Profil osoby $name som nenašiel."
    }

    fun clearHealthNotes(context: Context): String {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("health_notes").apply()
        return "Lokálne zdravotné poznámky som vymazal."
    }

    fun addHealthNote(context: Context, noteRaw: String): String {
        val note = noteRaw.trim().take(1200)
        require(note.isNotBlank()) { "Chýba zdravotná poznámka." }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getString("health_notes", "").orEmpty()
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        val next = (old.lines().filter { it.isNotBlank() } + "$date: $note").takeLast(20).joinToString("\n")
        prefs.edit().putString("health_notes", next).apply()
        return "Zdravotnú poznámku som uložil lokálne. Nie je to zdravotná diagnóza."
    }

    fun promptContext(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val profiles = readProfiles(prefs)
        val people = (0 until profiles.length()).mapNotNull { profiles.optJSONObject(it) }
            .joinToString("\n") { item -> "${item.optString("name")}: ${item.optString("notes")}" }
        val health = prefs.getString("health_notes", "").orEmpty()
        return buildString {
            if (people.isNotBlank()) append("OSOBNÉ POZNÁMKY O ĽUĎOCH (používateľom zadané; nie objektívne diagnózy):\n").append(people).append('\n')
            if (health.isNotBlank()) append("ZDRAVOTNÉ POZNÁMKY (používateľom výslovne uložené; nevyvodzuj diagnózy):\n").append(health).append('\n')
            val autonomousContext = JarvisAgent.promptContext(context)
            if (autonomousContext.isNotBlank()) append(autonomousContext).append('\n')
        }.take(11000)
    }

    private fun readProfiles(prefs: android.content.SharedPreferences): JSONArray =
        runCatching { JSONArray(prefs.getString("people", "[]").orEmpty()) }.getOrElse { JSONArray() }
}
