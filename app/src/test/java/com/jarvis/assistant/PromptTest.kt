package com.jarvis.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTest {

    @Test
    fun chatInstructionsCarryNoToolProtocol() {
        val text = Prompt.system(Mode.Chat, facts = "", languageName = "")
        assertTrue(text.contains("Chat mode"))
        assertFalse("no tool JSON in the chat prompt", text.contains("\"tool\""))
        assertFalse("no tool menu in the chat prompt", text.contains("Your tools:"))
    }

    @Test
    fun controlInstructionsCarryTheToolMenu() {
        val text = Prompt.system(Mode.Control, facts = "", languageName = "", menu = "- flashlight(on)")
        assertTrue(text.contains("Your tools:"))
        assertTrue(text.contains("flashlight(on)"))
        assertTrue("commands are guessed the safe way", text.contains("choose CHAT"))
    }

    @Test
    fun factsAndLanguageAreIncluded() {
        val text = Prompt.system(Mode.Chat, facts = "the user is Asha", languageName = "Hindi")
        assertTrue(text.contains("the user is Asha"))
        assertTrue(text.contains("Hindi"))
    }

    @Test
    fun theLocalPromptUsesGemmasOwnChatTemplate() {
        val text = Prompt.local("INSTRUCTIONS", listOf(Turn("hi", "hello")), "how are you")
        assertTrue(text.startsWith("<start_of_turn>user\nINSTRUCTIONS"))
        assertTrue("the first question is in the same turn as the instructions", text.contains("hi\n<end_of_turn>"))
        assertTrue(text.contains("<start_of_turn>model\nhello\n<end_of_turn>"))
        assertTrue("the new question is wrapped too", text.contains("how are you\n<end_of_turn>"))
        assertTrue("the model carries on from here", text.endsWith("<start_of_turn>model\n"))
    }

    @Test
    fun instructionsRideWithTheFirstTurnOnly() {
        val text = Prompt.local("RULES", listOf(Turn("hi", "hello")), "again")
        assertEquals(1, Regex("RULES").findAll(text).count())
        assertEquals(2, Regex("<start_of_turn>user").findAll(text).count())
        assertEquals(2, Regex("<start_of_turn>model").findAll(text).count())
    }

    @Test
    fun theChatPreludeStaysShort() {
        val text = Prompt.system(Mode.Chat, facts = "", languageName = "")
        assertTrue(
            "a small model should spend its window on the conversation, not on rules: ${text.length} characters",
            text.length < 1200,
        )
    }

    @Test
    fun chatAndControlAreDifferentPrompts() {
        val chat = Prompt.chat("", "", emptyList(), "hello")
        val control = Prompt.control("", "- flashlight", "", emptyList(), "hello")
        assertNotEquals(chat, control)
        assertFalse(chat.contains("Your tools:"))
        assertTrue(control.contains("Your tools:"))
    }
}
