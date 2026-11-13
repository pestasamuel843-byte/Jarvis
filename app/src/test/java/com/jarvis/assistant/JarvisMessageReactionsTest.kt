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

    @Test fun doesNotCommentOnSchoolPortalNotifications() {
        assertNull(JarvisMessageReactions.comment("EduPage", "Učiteľ", "Zajtra test z matematiky"))
    }

    @Test fun doesNotInventCommentForNeutralMessage() {
        assertNull(JarvisMessageReactions.comment("Messenger", "Martin", "Dobre, prídem o piatej."))
    }
}
