package com.jarvis.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisMessageReactionsTest {
    @Test fun addsHumorousCommentToClearlyFunnyMessage() {
        val result = JarvisMessageReactions.comment("Messenger", "Martin", "Zajtra máme tri testy 😂")
        assertTrue(result.orEmpty().contains("školské povinnosti"))
    }

    @Test fun recognizesInvitation() {
        val result = JarvisMessageReactions.comment("Instagram", "Peter", "Ideš zajtra von?")
        assertTrue(result.orEmpty().contains("pozvanie"))
    }

    @Test fun recognizesCompliment() {
        val result = JarvisMessageReactions.comment("Messenger", "Tomáš", "Máš talent, gratulujem!")
        assertTrue(result.orEmpty().contains("pochvala"))
    }

    @Test fun avoidsHumorForSeriousMessage() {
        val result = JarvisMessageReactions.comment("Messenger", "Martin", "Som v nemocnici, potrebujem pomoc.")
        assertTrue(result.orEmpty().contains("pôsobí vážne"))
    }

    @Test fun avoidsHumorForHostileMessage() {
        val result = JarvisMessageReactions.comment("Instagram", "Unknown", "Si fakt trápny.")
        assertTrue(result.orEmpty().contains("nepríjemný"))
    }

    @Test fun recognizesHostileMessageEvenWhenInsultHasExtraWords() {
        val result = JarvisMessageReactions.comment("Instagram", "Unknown", "Si fakt trápny.")
        assertTrue(result.orEmpty().contains("nepríjemný"))
    }

    @Test fun respectsExplicitNoContactBoundary() {
        val result = JarvisMessageReactions.comment("Messenger", "Martin", "Už mi nikdy nepíš.")
        assertTrue(result.orEmpty().contains("žiada ukončiť kontakt"))
        assertTrue(result.orEmpty().contains("neposielajme ďalšiu správu"))
    }

    @Test fun parsesNumberedAiCommentsAndIgnoresNone() {
        val result = JarvisMessageReactions.parseAiOutput("1|To znie ako pozvanie.\n2|NONE\n3|Pýta sa na čas.", 3)
        assertEquals("To znie ako pozvanie.", result[0])
        assertEquals(null, result[1])
        assertEquals("Pýta sa na čas.", result[2])
    }

    @Test fun ignoresOutOfRangeAiItems() {
        val result = JarvisMessageReactions.parseAiOutput("4|Neplatný komentár", 2)
        assertTrue(result.isEmpty())
    }

    @Test fun doesNotCommentOnSchoolPortalNotifications() {
        assertNull(JarvisMessageReactions.comment("EduPage", "Učiteľ", "Zajtra test z matematiky"))
    }

    @Test fun doesNotInventCommentForNeutralMessage() {
        assertNull(JarvisMessageReactions.comment("Messenger", "Martin", "Dobre, prídem o piatej."))
    }
}
