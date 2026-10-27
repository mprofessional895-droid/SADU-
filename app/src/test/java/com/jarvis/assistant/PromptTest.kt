package com.jarvis.assistant

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
    fun theTranscriptEndsWithTheNewQuestion() {
        val text = Prompt.flatten("INSTRUCTIONS\n", listOf(Turn("hi", "hello")), "how are you")
        assertTrue(text.startsWith("INSTRUCTIONS"))
        assertTrue(text.contains("User: hi"))
        assertTrue(text.contains("Jarvis: hello"))
        assertTrue("the model carries on from here", text.trimEnd().endsWith("Jarvis:"))
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
