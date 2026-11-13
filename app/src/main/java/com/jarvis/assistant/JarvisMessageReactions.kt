package com.jarvis.assistant

import java.util.Locale

/**
 * Adds a short, context-aware spoken observation when the user asks JARVIS
 * to read notification previews. This is intentionally conservative: it does
 * not send replies and does not speak about notifications on its own.
 *
 * Notification previews are incomplete, so comments describe only the visible
 * text and never claim to know the sender's intent.
 */
internal object JarvisMessageReactions {
    data class MessageInput(val appName: String, val sender: String, val message: String)

    fun safetyComment(appName: String, message: String): String? {
        if (appName.equals("EduPage", ignoreCase = true) || message.isBlank()) return null
        val text = message.lowercase(Locale.forLanguageTag("sk-SK"))
            .replace(Regex("\\s+"), " ")
            .trim()
        val seriousSignals = listOf(
            "je mi zle", "som chor", "som v nemocnici", "úraz", "nehoda",
            "zomrel", "zomrela", "zomrelo", "pohreb", "bojím sa", "bojim sa",
            "nevládzem", "nevladzem", "potrebujem pomoc", "ublížil", "ublizil",
            "ublížila", "ublizila", "šikana", "sikana", "problém doma",
            "problem doma", "urgent", "núdz", "nudz", "lekár", "lekar",
            "doktor", "plačem", "placem", "som smut"
        )
        if (seriousSignals.any(text::contains)) {
            return "Táto správa pôsobí vážne, pane. Odporúčam najprv si pokojne overiť, čo presne od vás odosielateľ potrebuje."
        }
        val hostileSignals = listOf(
            "idiot", "hlupák", "hlupak", "trápny", "trapny", "trapná", "trapna",
            "nenávidím ťa", "nenavidim ta", "daj mi pokoj", "neznášam ťa", "neznasam ta"
        )
        if (hostileSignals.any(text::contains)) {
            return "Tón tejto správy je nepríjemný. Nemusíme odpovedať rovnakým spôsobom, pane."
        }
        return null
    }

    fun parseAiOutput(raw: String, itemCount: Int): Map<Int, String> {
        val result = linkedMapOf<Int, String>()
        raw.lineSequence().forEach { line ->
            val match = Regex("^\\s*(\\d+)\\s*[|:]\\s*(.*?)\\s*$").find(line) ?: return@forEach
            val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: return@forEach
            val value = match.groupValues[2].trim()
            if (index !in 0 until itemCount || value.equals("NONE", true) || value.isBlank()) return@forEach
            val cleaned = value.replace(Regex("\\s+"), " ").take(150)
            if (cleaned.isNotBlank()) result[index] = cleaned
        }
        return result
    }
    fun comment(appName: String, sender: String, message: String): String? {
        if (appName.equals("EduPage", ignoreCase = true) || message.isBlank()) return null

        val text = message.lowercase(Locale.forLanguageTag("sk-SK"))
            .replace(Regex("\\s+"), " ")
            .trim()

        safetyComment(appName, message)?.let { return it }

        val laughing = listOf("😂", "🤣", "😅", "😆", "haha", "hehe", "lol").any(text::contains)
        val school = listOf("písomk", "pisomk", "test", "skúšan", "skusan", "domácu úlohu", "domacu ulohu", "matemat").any(text::contains)
        val invitation = listOf("ideš von", "ides von", "poď von", "pod von", "stretn", "zajtra von", "cez víkend", "cez vikend").any(text::contains)
        val compliment = listOf("dobrá práca", "dobra praca", "máš talent", "mas talent", "gratulujem", "super video", "si šikovný", "si sikovny").any(text::contains)
        val greeting = listOf("dobré ráno", "dobre rano", "dobrý deň", "dobry den", "dobrú noc", "dobru noc").any(text::contains)

        return when {
            laughing && school ->
                "Zdá sa, že odosielateľ berie školské povinnosti s humorom. Odvážny prístup; učebnice sa zatiaľ nesmejú."
            laughing ->
                "V správe je jasný veselý tón, pane. Aspoň táto konverzácia zatiaľ nevyzerá na krízové zasadnutie."
            school ->
                "Vyzerá to na školskú záležitosť. Učivo si zjavne našlo cestu aj do vašich správ."
            invitation ->
                "To vyzerá ako pozvanie na stretnutie, pane. Teraz už len zistiť, či vám to časovo vyhovuje."
            compliment ->
                "To je príjemná pochvala, pane. Tú si pokojne môžete pripísať k dnešným úspechom."
            greeting ->
                "Odosielateľ vás zdraví. Konečne správa, ktorá od vás nechce domácu úlohu."
            text.endsWith("?") ->
                "Odosielateľ sa vás na niečo pýta. Ak chcete, môžeme spolu pripraviť odpoveď."
            else -> null
        }
    }
}
