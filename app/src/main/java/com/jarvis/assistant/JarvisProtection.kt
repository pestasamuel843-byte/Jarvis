package com.jarvis.assistant

import android.content.Context
import java.util.Locale

/** Four-level support mode. It never takes control away from the user. */
internal object JarvisProtection {
    const val NORMAL = 0
    const val GUARDIAN = 1
    const val BLACKOUT = 2
    const val OMEGA = 3
    const val SOVEREIGN = 4
    private const val PREFS = "jarvis_protection"

    fun currentLevel(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("level", NORMAL).coerceIn(NORMAL, SOVEREIGN)

    fun setLevel(context: Context, level: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("level", level.coerceIn(NORMAL, SOVEREIGN))
            .putLong("changed_at", System.currentTimeMillis()).apply()
    }

    fun clear(context: Context) = setLevel(context, NORMAL)

    fun detectRequest(raw: String): Int? {
        val text = normalize(raw)
        return when {
            listOf("aktivuj sovereign", "aktivuj sovereign protocol", "prevezmi to cele", "ja uz to nedam", "uz to nedavam", "ja to uz nezvladam", "nechcem uz nic riesit").any { text.contains(it) } -> SOVEREIGN
            listOf("aktivuj omega", "omega protocol").any { text.contains(it) } -> OMEGA
            listOf("aktivuj blackout", "blackout protocol").any { text.contains(it) } -> BLACKOUT
            listOf("aktivuj guardian", "guardian rezim", "guardian mode").any { text.contains(it) } -> GUARDIAN
            listOf("som v nebezpeci", "niekto mi hrozi", "bojim sa ze mi niekto ublizi", "niekto ma napadol", "hrozi mi bezprostredne nebezpecenstvo").any { text.contains(it) } -> OMEGA
            listOf("som pretazeny", "som uplne vycerpany", "uz nevladzem", "mam toho prilis vela", "nezvladam to").any { text.contains(it) } -> GUARDIAN
            else -> null
        }
    }

    fun isExitRequest(raw: String): Boolean {
        val text = normalize(raw)
        return listOf("ukonci ochranny rezim", "ukonci guardian", "ukonci blackout", "ukonci omega", "ukonci sovereign", "situacia vyriesena", "obnov normalny rezim").any { text.contains(it) }
    }

    fun label(level: Int): String = when (level) {
        GUARDIAN -> "GUARDIAN"
        BLACKOUT -> "BLACKOUT"
        OMEGA -> "OMEGA"
        SOVEREIGN -> "SOVEREIGN"
        else -> "NORMAL"
    }

    fun activationMessage(level: Int): String = when (level) {
        GUARDIAN -> "GUARDIAN aktivovaný. Stíšim nepodstatné rozptýlenia a pomôžem vám rozdeliť situáciu na zvládnuteľné kroky. Čo sa stalo?"
        BLACKOUT -> "BLACKOUT aktivovaný. Zameriam sa na aktuálny problém a bezpečný postup. Nebudem eskalovať konflikt ani konať bez vášho súhlasu."
        OMEGA -> "OMEGA aktivovaný. Najprv bezpečnosť: ste teraz na bezpečnom mieste? Ak hrozí bezprostredné nebezpečenstvo, presuňte sa k dôveryhodnému dospelému a volajte 112."
        SOVEREIGN -> "SOVEREIGN aktivovaný. Preberám organizáciu ďalších krokov, nie rozhodovanie za vás. Nebudem vás zahlcovať. Najprv mi, prosím, povedzte: ste teraz v bezpečí?"
        else -> "Ochranný režim je vypnutý."
    }

    fun promptRules(context: Context): String {
        val level = currentLevel(context)
        return """
AKTUÁLNY OCHRANNÝ REŽIM: ${label(level)}.
- GUARDIAN: podpora pri preťažení; pokojne a stručne rozdeľ úlohy na malé kroky.
- BLACKOUT: kritická situácia; stíš nepodstatné témy, sústreď sa na bezpečný a vecný postup.
- OMEGA: používaj iba pri dôvodných známkach bezprostredného vážneho ohrozenia; uprednostni bezpečné miesto a kontaktovanie dôveryhodného dospelého alebo tiesňovej linky 112.
- SOVEREIGN: používateľ žiada minimálnu záťaž a pomoc s organizáciou; dávaj vždy jeden jednoduchý krok a najviac jednu nevyhnutnú otázku naraz.
- Veta „už to nedám“ sama osebe neznamená samovražedný úmysel ani bezprostredné nebezpečenstvo. Citlivo over bezpečnosť bez dramatizovania.
- Nikdy netvrď, že vidíš, počuješ alebo sleduješ niečo, čo aplikácia technicky nesprístupnila. Nevykonávaj tajné nahrávanie, odpočúvanie, vyhrážky ani odvetu.
- Neodosielaj správy, nevolaj, nemaž dáta ani nevykonávaj citlivé úkony bez výslovného potvrdenia používateľa. Režim nepreberá jeho slobodnú vôľu.
- Ochranný režim možno ukončiť jasným príkazom používateľa; pri pretrvávajúcom nebezpečenstve však najprv pripomeň bezpečnostný plán.
""".trimIndent()
    }

    private fun normalize(value: String): String = value.lowercase(Locale("sk", "SK"))
        .replace('á','a').replace('ä','a').replace('č','c').replace('ď','d').replace('é','e')
        .replace('í','i').replace('ĺ','l').replace('ľ','l').replace('ň','n').replace('ó','o')
        .replace('ô','o').replace('ŕ','r').replace('š','s').replace('ť','t').replace('ú','u')
        .replace('ý','y').replace('ž','z')
}
