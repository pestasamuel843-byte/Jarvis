package com.jarvis.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local marketplace-listing drafts. This skill never publishes listings or
 * contacts buyers; it stores only facts dictated by the user and marks missing
 * details instead of inventing prices, condition, warranty, or specifications.
 */
internal class ListingDraftSkill : JarvisSkill {
    override val descriptor = JarvisSkillDescriptor(
        id = "marketplace.listing_draft",
        description = "Prepare and locally save a marketplace listing draft"
    )

    override fun canHandle(command: String): Boolean {
        val value = command.trim().lowercase(Locale.ROOT)
        return value.startsWith("priprav inzerat") ||
            value.startsWith("priprav inzerát") ||
            value == "ukaz moje inzeraty" ||
            value == "ukáž moje inzeráty" ||
            value == "moje inzeráty" ||
            value == "moje inzeraty"
    }

    override fun execute(context: Context, command: String): JarvisToolResult {
        val normalized = command.trim()
        if (normalized.startsWith("ukáž moje inzeráty", true) ||
            normalized.startsWith("ukaz moje inzeraty", true) ||
            normalized.equals("moje inzeráty", true) ||
            normalized.equals("moje inzeraty", true)
        ) return listDrafts(context)

        val details = normalized
            .replace(Regex("(?i)^priprav inzer[aá]t\\s*"), "")
            .removePrefix(":")
            .trim()
            .replace(Regex("(?i)^na\\s+"), "")
            .trim()

        if (details.isBlank()) {
            return JarvisToolResult.Failure(
                "Povedzte, čo chcete ponúknuť. Napríklad: Priprav inzerát na horský bicykel, značka Kellys, používaný, dobrý stav."
            )
        }

        val title = details.split(Regex("[,.;\\n]"), limit = 2)
            .firstOrNull()?.trim()?.take(90).orEmpty()
        val draft = JSONObject()
            .put("id", System.currentTimeMillis().toString())
            .put("created_at", System.currentTimeMillis())
            .put("title", title.ifBlank { "Návrh inzerátu" })
            .put("user_facts", details)
            .put("status", "draft")
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val drafts = runCatching { JSONArray(prefs.getString(KEY, "[]").orEmpty()) }
            .getOrDefault(JSONArray())
        drafts.put(draft)
        val bounded = JSONArray()
        val start = (drafts.length() - MAX_DRAFTS).coerceAtLeast(0)
        for (index in start until drafts.length()) bounded.put(drafts.getJSONObject(index))
        prefs.edit().putString(KEY, bounded.toString()).apply()

        return JarvisToolResult.Success(
            "Návrh inzerátu som uložil iba do tohto zariadenia. Nadpis: „${draft.optString("title")}“. " +
                "Použil som len údaje, ktoré ste nadiktovali; cenu, stav ani ďalšie vlastnosti som si nevymyslel. " +
                "Zatiaľ nie je zverejnený a nikomu som neposlal správu. Povedzte „ukáž moje inzeráty“, ak ho chcete skontrolovať."
        )
    }

    private fun listDrafts(context: Context): JarvisToolResult {
        val drafts = runCatching {
            JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]").orEmpty())
        }.getOrDefault(JSONArray())
        if (drafts.length() == 0) {
            return JarvisToolResult.Success("Zatiaľ nemáte uložený žiadny návrh inzerátu.")
        }
        val output = (0 until drafts.length()).mapNotNull { index ->
            drafts.optJSONObject(index)?.let { item ->
                val date = SimpleDateFormat("d.M.yyyy", Locale("sk", "SK"))
                    .format(Date(item.optLong("created_at")))
                "${index + 1}. ${item.optString("title")} ($date) — ${item.optString("user_facts")}"
            }
        }
        return JarvisToolResult.Success(
            "Vaše lokálne návrhy inzerátov:\n" + output.joinToString("\n") +
                "\nVšetky sú iba koncepty. Nie sú zverejnené."
        )
    }

    private companion object {
        const val PREFS = "jarvis_marketplace_drafts"
        const val KEY = "listing_drafts"
        const val MAX_DRAFTS = 30
    }
}
