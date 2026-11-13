package com.jarvis.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakePhraseMatcherTest {
    @Test fun recognizesSlovakWakePhraseWithCommand() {
        assertEquals("otvor youtube", WakePhraseMatcher.commandAfterWakePhrase("Hej Jarvis, otvor YouTube!"))
    }

    @Test fun recognizesJoinedWakePhrase() {
        assertEquals("koľko je hodín", WakePhraseMatcher.commandAfterWakePhrase("hejjarvis koľko je hodín"))
    }

    @Test fun recognizesCommonSpeechToTextVariants() {
        assertEquals("otvor nastavenia", WakePhraseMatcher.commandAfterWakePhrase("Džarvis, otvor nastavenia"))
    }

    @Test fun recognizesRequestedAlternativeWakeWord() {
        assertEquals("prečítaj správy", WakePhraseMatcher.commandAfterWakePhrase("Počuj, prečítaj správy"))
    }

    @Test fun doesNotWakeOnWordInMiddleOfSentence() {
        assertNull(WakePhraseMatcher.commandAfterWakePhrase("dnes som pozeral Jarvisov film"))
    }

    @Test fun doesNotWakeOnUnrelatedSpeech() {
        assertNull(WakePhraseMatcher.commandAfterWakePhrase("otvor YouTube"))
    }
}
