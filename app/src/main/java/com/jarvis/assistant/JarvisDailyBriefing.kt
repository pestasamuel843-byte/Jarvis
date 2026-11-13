package com.jarvis.assistant

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Builds a short daily briefing from data already on this device.
 * It does not call a cloud API and does not fetch private account contents.
 */
internal object JarvisDailyBriefing {
    fun build(context: Context, includeSchedule: Boolean = true): String {
        val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        val now = Calendar.getInstance()
        val dayName = when (now.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Pondelok"
            Calendar.TUESDAY -> "Utorok"
            Calendar.WEDNESDAY -> "Streda"
            Calendar.THURSDAY -> "Štvrtok"
            Calendar.FRIDAY -> "Piatok"
            Calendar.SATURDAY -> "Sobota"
            else -> "Nedeľa"
        }
        val time = SimpleDateFormat("H:mm", Locale("sk", "SK")).format(Date())
        val greeting = when (now.get(Calendar.HOUR_OF_DAY)) {
            in 5..10 -> "Dobré ráno, pane."
            in 11..17 -> "Dobrý deň, pane."
            else -> "Dobrý večer, pane."
        }

        val sections = mutableListOf<String>()
        sections += "$greeting Je $time. Tu je stručný prehľad."

        if (includeSchedule) {
            val todaysLessons = prefs.getString("school_timetable", "").orEmpty()
                .lineSequence()
                .filter { it.startsWith("$dayName |", ignoreCase = true) }
                .toList()
            if (todaysLessons.isEmpty()) {
                sections += if (dayName == "Sobota" || dayName == "Nedeľa") {
                    "V rozvrhu dnes nemáte uložené vyučovanie. Aspoň raz má kalendár dobré správy."
                } else {
                    "Na dnešok nemám uložený školský rozvrh. Nechcem si ho vymýšľať."
                }
            } else {
                sections += "Dnešný rozvrh: " + todaysLessons.joinToString(". ") { it.substringAfter("|").trim() } + "."
            }
        }

        val access = GmailNotificationListener.hasNotificationAccess(context)
        if (!access) {
            sections += "K oznámeniam zatiaľ nemám prístup. Ak ho povolíte v Androide, budem vedieť zhrnúť aktívne upozornenia."
        } else if (!GmailNotificationListener.isConnected()) {
            sections += "Prístup k oznámeniam je povolený, ale služba Androidu sa ešte nepripojila. Skúste to o chvíľu znova."
        } else {
            val notifications = GmailNotificationListener.activePreviews()
            if (notifications.isEmpty()) {
                sections += "V podporovaných aplikáciách momentálne nevidím aktívne upozornenia. Digitálny pokoj. Podozrivé, ale príjemné."
            } else {
                sections += "Aktívne upozornenia: " + notifications.size + "."
                notifications.take(6).forEachIndexed { index, item ->
                    val ageMinutes = ((System.currentTimeMillis() - item.postTime).coerceAtLeast(0L) / 60_000L)
                    val whenText = when {
                        ageMinutes < 1 -> "práve teraz"
                        ageMinutes < 60 -> "pred $ageMinutes minútami"
                        else -> "pred ${ageMinutes / 60} h"
                    }
                    val body = item.text.ifBlank { "Bez textového náhľadu." }
                    sections += "${index + 1}. ${item.app}: ${item.title.ifBlank { "Oznámenie" }} ($whenText). $body"
                }
                if (notifications.size > 6) {
                    sections += "Ďalších ${notifications.size - 6} upozornení zostáva v zozname; prečítam ich na požiadanie."
                }
            }
        }

        val notes = prefs.getString("notes", "").orEmpty().lineSequence()
            .filter { it.isNotBlank() }.toList()
        if (notes.isNotEmpty()) {
            sections += "Máte uložené poznámky: ${notes.size}. Ak chcete, môžem ich prejsť."
        }

        sections += "To je zatiaľ všetko. Chcete podrobnejšie správy, rozvrh alebo plán na dnes?"
        return sections.joinToString("\n")
    }
}
