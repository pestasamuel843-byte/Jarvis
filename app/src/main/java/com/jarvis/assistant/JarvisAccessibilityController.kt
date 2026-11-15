package com.jarvis.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.graphics.Path
import android.graphics.Rect
import android.accessibilityservice.GestureDescription
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * User-invoked screen control. It reads or activates visible UI nodes only after
 * a spoken command; it does not continuously collect screen text.
 */
object JarvisAccessibilityController {
    @Volatile private var service: AccessibilityService? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var lastScrollForward = true

    fun attach(value: AccessibilityService) { service = value }
    fun detach(value: AccessibilityService) { if (service === value) service = null }

    fun execute(context: Context, raw: String): String? {
        val command = raw.trim()
        val lower = command.lowercase()
        val confirmation = Regex("(?i)^potvrdzujem (?:kliknutie na|stlačenie tlačidla|stlacenie tlacidla)\\s+(.+)$").find(command)
        if (confirmation != null) {
            val target = confirmation.groupValues[1].trim()
            val prefs = service?.getSharedPreferences("jarvis_accessibility_pending", Context.MODE_PRIVATE)
            val pending = prefs?.getString("target", "").orEmpty()
            val created = prefs?.getLong("created_at", 0L) ?: 0L
            if (!pending.equals(target, ignoreCase = true) || System.currentTimeMillis() - created > 30_000L) {
                prefs?.edit()?.clear()?.apply()
                return "Nemám čakajúcu citlivú akciu s týmto názvom. Najprv ju vyberte."
            }
            prefs?.edit()?.clear()?.apply()
            return runOnMain { clickVisibleText(target, allowSensitive = true) }
        }
        val typePrefix = Regex("(?i)^(?:napíš|napis|zadaj) do aktívneho poľa\\s+(.+)$").find(command)
            ?: Regex("(?i)^(?:napíš|napis|zadaj) do aktivneho pola\\s+(.+)$").find(command)
        if (typePrefix != null) return runOnMain { typeIntoFocusedField(typePrefix.groupValues[1]) }
        val longPress = Regex("(?i)^(?:podrž na|podrz na|dlho stlač|dlho stlac)\\s+(.+)$").find(command)
        if (longPress != null) return runOnMain { clickVisibleText(longPress.groupValues[1].trim(), longClick = true) }
        val action: (() -> String)? = when {
            lower in setOf("prečítaj obrazovku", "precitaj obrazovku", "čo je na obrazovke", "co je na obrazovke",
                "prečítaj stránku", "precitaj stranku", "prečítaj obsah", "precitaj obsah") ->
                ({ readVisibleScreen() })
            lower == "späť" || lower == "spat" || lower == "choď späť" || lower == "chod spat" ->
                ({ globalAction(AccessibilityService.GLOBAL_ACTION_BACK, "Vrátil som sa o krok späť.") })
            lower in setOf("domov", "choď domov", "chod domov", "otvor domovskú obrazovku", "otvor domovsku obrazovku") ->
                ({ globalAction(AccessibilityService.GLOBAL_ACTION_HOME, "Otvoril som domovskú obrazovku.") })
            lower in setOf("otvor oznámenia", "otvor oznamenia", "zobraz oznámenia", "zobraz oznamenia") ->
                ({ globalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "Otvoril som panel oznámení.") })
            lower in setOf("otvor rýchle nastavenia", "otvor rychle nastavenia", "zobraz rýchle nastavenia", "zobraz rychle nastavenia") ->
                ({ globalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, "Otvoril som rýchle nastavenia.") })
            lower in setOf("posuň dole", "posun dole", "scroll dole", "skrol dole", "skrolni dole",
                "scrolluj dole", "posuň stránku dole", "posun stranku dole", "zroluj dole", "skroluj dole",
                "posuň obrazovku dole", "posun obrazovku dole") ->
                ({ scroll(true) })
            lower in setOf("posuň hore", "posun hore", "scroll hore", "skrol hore", "skrolni hore",
                "scrolluj hore", "posuň stránku hore", "posun stranku hore", "zroluj hore", "skroluj hore",
                "posuň obrazovku hore", "posun obrazovku hore") ->
                ({ scroll(false) })
            lower in setOf("skrolni", "skroluj", "scroll", "scrollni", "posuň", "posun") ->
                ({ scroll(lastScrollForward) })
            lower in setOf("ešte", "este", "ešte trochu", "este trochu", "pokračuj", "pokracuj",
                "ďalej", "dalej", "ešte ďalej", "este dalej") ->
                ({ scroll(lastScrollForward) })
            lower.startsWith("klikni na ") || lower.startsWith("stlač ") || lower.startsWith("stlac ") ||
                lower.startsWith("ťukni na ") || lower.startsWith("tukni na ") || lower.startsWith("vyber ") ->
                ({
                    val target = command.replace(Regex("(?i)^(klikni na|stlač|stlac|ťukni na|tukni na|vyber)\\s+"), "").trim()
                    clickVisibleText(target)
                })
            else -> null
        }
        return action?.let { runOnMain(it) }
    }

    private fun runOnMain(action: () -> String): String {
        if (Looper.myLooper() == Looper.getMainLooper()) return runCatching(action).getOrElse {
            "Ovládanie obrazovky zlyhalo: ${it.message ?: "neznáma chyba"}."
        }
        val latch = CountDownLatch(1)
        var result = "Ovládanie obrazovky neodpovedalo."
        mainHandler.post {
            result = runCatching(action).getOrElse {
                "Ovládanie obrazovky zlyhalo: ${it.message ?: "neznáma chyba"}."
            }
            latch.countDown()
        }
        latch.await(2500, TimeUnit.MILLISECONDS)
        return result
    }

    private fun root(): AccessibilityNodeInfo? = service?.rootInActiveWindow

    private fun globalAction(action: Int, success: String): String {
        val current = service ?: return "Na ovládanie obrazovky najprv povoľte JARVIS v nastaveniach Prístupnosti."
        return if (current.performGlobalAction(action)) success else "Android túto akciu odmietol."
    }

    private fun walk(node: AccessibilityNodeInfo?, visit: (AccessibilityNodeInfo) -> Unit) {
        if (node == null) return
        visit(node)
        for (index in 0 until node.childCount) walk(node.getChild(index), visit)
    }

    private fun nodeLabel(node: AccessibilityNodeInfo): String =
        (node.text?.toString().orEmpty().ifBlank { node.contentDescription?.toString().orEmpty() }).trim()

    private fun readVisibleScreen(): String {
        if (service == null) return "Na čítanie obrazovky povoľte JARVIS v nastaveniach Prístupnosti."
        val activeRoot = root() ?: return "Aktívna aplikácia neposkytla čitateľný obsah obrazovky."
        val lines = linkedSetOf<String>()
        walk(activeRoot) { node ->
            val value = nodeLabel(node)
            if (node.isVisibleToUser && value.isNotBlank() && value.length <= 500) lines.add(value)
        }
        val result = lines.take(55).joinToString("\n").take(6500)
        return if (result.isBlank()) "Na tejto obrazovke nie je dostupný čitateľný text."
            else "Viditeľný obsah obrazovky:\n$result"
    }

    private fun clickVisibleText(target: String, allowSensitive: Boolean = false, longClick: Boolean = false): String {
        if (target.isBlank()) return "Povedzte názov tlačidla alebo položky."
        val sensitive = Regex("(?i)(odoslať|odoslat|send|delete|vymazať|vymazat|zmazať|zmazat|kúpiť|kupit|buy|zaplatiť|zaplatit|pay|publikovať|publikovat|publish|objednať|objednat|confirm|potvrdiť|potvrdit)")
            .containsMatchIn(target)
        if (sensitive && !allowSensitive) {
            service?.getSharedPreferences("jarvis_accessibility_pending", Context.MODE_PRIVATE)?.edit()
                ?.putString("target", target)?.putLong("created_at", System.currentTimeMillis())?.apply()
            return "Toto môže odoslať, vymazať, zverejniť alebo zaplatiť. Ak to naozaj chcete vykonať, do 30 sekúnd povedzte: Potvrdzujem stlačenie tlačidla $target."
        }
        if (service == null) return "Na ovládanie aplikácií povoľte JARVIS v nastaveniach Prístupnosti."
        val activeRoot = root() ?: return "Aktívna aplikácia neposkytla ovládateľné prvky."
        val matches = mutableListOf<AccessibilityNodeInfo>()
        walk(activeRoot) { node ->
            val label = nodeLabel(node)
            if (node.isVisibleToUser && label.equals(target, ignoreCase = true)) matches.add(node)
        }
        if (matches.isEmpty()) {
            walk(activeRoot) { node ->
                val label = nodeLabel(node)
                if (node.isVisibleToUser && label.contains(target, ignoreCase = true)) matches.add(node)
            }
        }
        val unique = matches
        if (unique.isEmpty()) return "Na obrazovke som nenašiel položku „$target“."
        if (unique.size > 1) return "Našiel som viac položiek s názvom „$target“. Spresnite, ktorú mám vybrať."
        var clickable: AccessibilityNodeInfo? = unique.first()
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        val node = clickable ?: unique.first()
        val action = if (longClick) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
        val success = node.performAction(action)
        return if (success) {
            if (longClick) "Podržal som „$target“." else "Stlačil som „$target“. Overujem zmenu obrazovky."
        } else "Položku „$target“ Android označil ako neovládateľnú."
    }


    private fun typeIntoFocusedField(value: String): String {
        if (value.isBlank()) return "Text na vloženie je prázdny."
        if (service == null) return "Na zadávanie textu povoľte JARVIS v nastaveniach Prístupnosti."
        val activeRoot = root() ?: return "Aktívna aplikácia neposkytla textové pole."
        val editable = mutableListOf<AccessibilityNodeInfo>()
        walk(activeRoot) { node -> if (node.isVisibleToUser && node.isEditable) editable.add(node) }
        if (editable.isEmpty()) return "Na obrazovke som nenašiel viditeľné textové pole."
        val focused = editable.filter { it.isFocused }
        val target = when {
            focused.size == 1 -> focused.first()
            focused.size > 1 -> return "Je aktívnych viac textových polí. Ťuknite prstom do toho správneho a zopakujte príkaz."
            else -> return "Najprv ťuknite prstom do textového poľa, do ktorého mám písať."
        }
        val fieldHint = listOfNotNull(target.hintText?.toString(), target.contentDescription?.toString(), target.text?.toString())
            .joinToString(" ").lowercase()
        if (target.isPassword || Regex("(?i)(heslo|password|pin|kód|kod|cvv|cvc|card number|číslo karty|cislo karty|overovací kód|overovaci kod|otp)")
                .containsMatchIn(fieldHint)) {
            return "Do hesiel, PIN-ov a platobných alebo overovacích polí text nevkladám."
        }
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            "Text som vložil do aktívneho poľa. Zatiaľ som nič neodoslal."
        } else "Aplikácia nedovolila vložiť text do tohto poľa."
    }

    private fun scroll(forward: Boolean): String {
        lastScrollForward = forward
        val current = service ?: return "Na posúvanie obrazovky povoľte JARVIS v nastaveniach Prístupnosti."
        val activeRoot = root() ?: return "Aktívna aplikácia neposkytla ovládateľný obsah."
        val scrollables = mutableListOf<Pair<AccessibilityNodeInfo, Rect>>()
        walk(activeRoot) { node ->
            if (node.isVisibleToUser && node.isScrollable) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (bounds.width() > 0 && bounds.height() > 0) scrollables.add(node to bounds)
            }
        }
        val selected = scrollables.maxByOrNull { it.second.width().toLong() * it.second.height().toLong() }
        if (selected != null) {
            val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            if (selected.first.performAction(action)) {
                return if (forward) "Posúvam nadol." else "Posúvam nahor."
            }
        }
        // Some apps expose no scrollable accessibility node. Use a swipe in the visible content area.
        val bounds = selected?.second ?: Rect().also { activeRoot.getBoundsInScreen(it) }
        if (bounds.width() < 80 || bounds.height() < 160) return "Na obrazovke som nenašiel oblasť, ktorú možno posúvať."
        val x = bounds.centerX().toFloat()
        val fromY = if (forward) bounds.bottom - bounds.height() / 4f else bounds.top + bounds.height() / 4f
        val toY = if (forward) bounds.top + bounds.height() / 4f else bounds.bottom - bounds.height() / 4f
        val path = Path().apply { moveTo(x, fromY); lineTo(x, toY) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 420))
            .build()
        return if (current.dispatchGesture(gesture, null, null)) {
            if (forward) "Posúvam obrazovku nadol." else "Posúvam obrazovku nahor."
        } else "Android nedovolil gesto posúvania."
    }
}
