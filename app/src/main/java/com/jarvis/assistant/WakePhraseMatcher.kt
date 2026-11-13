package com.jarvis.assistant

import java.util.Locale

/**
 * Normalizes Slovak speech-recognition variants and extracts a command only
 * when an accepted wake phrase occurs at the beginning of the utterance.
 */
internal object WakePhraseMatcher {
    private val wakePattern = Regex(
        "^(?:hey|hej)\\s*(?:jarvis)\\b[ ,:]*",
        RegexOption.IGNORE_CASE
    )

    fun commandAfterWakePhrase(phrase: String): String? {
        val normalized = phrase.lowercase(Locale.ROOT)
            .replace("džarvis", "jarvis")
            .replace("džervis", "jarvis")
            .replace("ďarvis", "jarvis")
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) return null
        val match = wakePattern.find(normalized) ?: return null
        return normalized.substring(match.range.last + 1).trim()
    }
}
