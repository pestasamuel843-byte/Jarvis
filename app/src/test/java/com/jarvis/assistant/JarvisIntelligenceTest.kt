package com.jarvis.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class JarvisIntelligenceTest {
    @Test
    fun parsesAndEncodesLearnedProcedure() {
        val parsed = JarvisIntelligence.parseProcedureCommand(
            "nauč sa postup Ranná príprava: skontroluj rozvrh, zbaľ zošity, priprav vodu"
        )
        assertNotNull(parsed)
        assertEquals("Ranná príprava", parsed?.title)
        assertEquals(
            "NAUČENÝ POSTUP: Ranná príprava — skontroluj rozvrh, zbaľ zošity, priprav vodu",
            JarvisIntelligence.encodeProcedure(parsed!!)
        )
    }

    @Test
    fun rejectsProcedureWithoutSteps() {
        assertNull(JarvisIntelligence.parseProcedureCommand("nauč sa postup Ranná príprava:"))
    }

    @Test
    fun findsProcedureByTitleFromMemory() {
        val memory = "Preferuje stručné odpovede\n" +
            "NAUČENÝ POSTUP: Ranná príprava — skontroluj rozvrh, zbaľ zošity"
        val procedure = JarvisIntelligence.findProcedure(memory, "príprava")
        assertEquals("Ranná príprava", procedure?.title)
        assertEquals("skontroluj rozvrh, zbaľ zošity", procedure?.steps)
    }

    @Test
    fun doesNotFindUnrelatedProcedure() {
        assertNull(
            JarvisIntelligence.findProcedure(
                "NAUČENÝ POSTUP: Ranná príprava — skontroluj rozvrh",
                "večerné učenie"
            )
        )
    }
}
