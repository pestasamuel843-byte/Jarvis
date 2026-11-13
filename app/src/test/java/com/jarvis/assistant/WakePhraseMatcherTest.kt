package com.jarvis.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakePhraseMatcherTest {
    @Test fun recognizesSlovakSpeechRecognitionVariantWithCommand() {
        assertEquals("otvor youtube", WakePhraseMatcher.commandAfterWakePhrase("Hej Jarvis, otvor YouTube!"))
    }

    @Test fun recognizesExactEnglishWakePhrase() {
        assertEquals("otvor youtube", WakePhraseMatcher.commandAfterWakePhrase("Hey Jarvis, otvor YouTube!"))
    }

    @Test fun recognizesJoinedWakePhrase() {
        assertEquals("koľko je hodín", WakePhraseMatcher.commandAfterWakePhrase("hejjarvis koľko je hodín"))
    }

    @Test fun recognizesCommonSpeechToTextVariants() {
        assertEquals("otvor nastavenia", WakePhraseMatcher.commandAfterWakePhrase("Hej Džarvis, otvor nastavenia"))
    }

    @Test fun doesNotWakeOnJarvisAlone() {
        assertNull(WakePhraseMatcher.commandAfterWakePhrase("Jarvis, prečítaj správy"))
    }

    @Test fun doesNotWakeOnOtherWords() {
        assertNull(WakePhraseMatcher.commandAfterWakePhrase("Počuj, prečítaj správy"))
    }

    @Test fun doesNotWakeOnWordInMiddleOfSentence() {
        assertNull(WakePhraseMatcher.commandAfterWakePhrase("dnes som pozeral Jarvisov film"))
    }

    @Test fun doesNotWakeOnUnrelatedSpeech() {
        assertNull(WakePhraseMatcher.commandAfterWakePhrase("otvor YouTube"))
    }
}
