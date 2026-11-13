package com.jarvis.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingDraftSkillTest {
    private val skill = ListingDraftSkill()

    @Test
    fun recognizesListingDraftWithAccents() {
        assertTrue(skill.canHandle("priprav inzerát na bicykel"))
    }

    @Test
    fun recognizesListingDraftWithoutAccents() {
        assertTrue(skill.canHandle("priprav inzerat na bicykel"))
    }

    @Test
    fun recognizesSavedDraftListingRequest() {
        assertTrue(skill.canHandle("ukáž moje inzeráty"))
    }

    @Test
    fun ignoresUnrelatedConversation() {
        assertFalse(skill.canHandle("Jarvis, ako sa dnes máš?"))
    }
}
