package com.jarvis.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchSkillTest {
    private val skill = AppSearchSkill()

    @Test
    fun recognizesSlovakAppLaunch() {
        assertTrue(skill.canHandle("otvor Instagram"))
    }

    @Test
    fun recognizesSlovakWebSearch() {
        assertTrue(skill.canHandle("vyhľadaj predpoveď počasia"))
    }

    @Test
    fun recognizesCompoundYoutubeCommand() {
        assertTrue(skill.canHandle("otvor YouTube a vyhľadaj vesmír"))
    }

    @Test
    fun doesNotCaptureOrdinaryConversation() {
        assertFalse(skill.canHandle("Jarvis, ako sa dnes máš?"))
    }
}
