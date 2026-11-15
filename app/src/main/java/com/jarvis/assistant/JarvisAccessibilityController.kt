package com.jarvis.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
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
            lower in setOf("posuň dole", "posun dole", "scroll dole", "posuň stránku dole", "posun stranku dole", "zroluj dole") ->
                ({ scroll(true) })
            lower in setOf("posuň hore", "posun hore", "scroll hore", "posuň stránku hore", "posun stranku hore", "zroluj hore") ->
                ({ scroll(false) })
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
            if (value.isNotBlank() && value.length <= 500) lines.add(value)
        }
        val result = lines.take(55).joinToString("\n").take(6500)
        return if (result.isBlank()) "Na tejto obrazovke nie je dostupný čitateľný text."
            else "Viditeľný obsah obrazovky:\n$result"
    }

    private fun clickVisibleText(target: String, allowSensitive: Boolean = false): String {
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
            if (label.equals(target, ignoreCase = true)) matches.add(node)
        }
        if (matches.isEmpty()) {
            walk(activeRoot) { node ->
                val label = nodeLabel(node)
                if (label.contains(target, ignoreCase = true)) matches.add(node)
            }
        }
        val unique = matches.distinctBy { nodeLabel(it) + "|" + it.className + "|" + it.isClickable }
        if (unique.isEmpty()) return "Na obrazovke som nenašiel položku „$target“."
        if (unique.size > 1) return "Našiel som viac položiek s názvom „$target“. Spresnite, ktorú mám vybrať."
        var clickable: AccessibilityNodeInfo? = unique.first()
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        val node = clickable ?: unique.first()
        val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return if (success) "Stlačil som „$target“. Overujem zmenu obrazovky."
            else "Položku „$target“ Android označil ako neovládateľnú."
    }

    private fun scroll(forward: Boolean): String {
        if (service == null) return "Na posúvanie obrazovky povoľte JARVIS v nastaveniach Prístupnosti."
        val activeRoot = root() ?: return "Aktívna aplikácia neposkytla ovládateľný obsah."
        val scrollables = mutableListOf<AccessibilityNodeInfo>()
        walk(activeRoot) { if (it.isScrollable) scrollables.add(it) }
        val node = scrollables.firstOrNull() ?: return "Na obrazovke som nenašiel posúvateľnú oblasť."
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return if (node.performAction(action)) {
            if (forward) "Posunul som obrazovku nadol." else "Posunul som obrazovku nahor."
        } else "Aplikácia odmietla posunutie obrazovky."
    }
}
