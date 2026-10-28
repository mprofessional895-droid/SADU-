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
        assertFalse("no tool JSON in the chat prompt", text.contains("\"tool\""))
        assertFalse("no tool menu in the chat prompt", text.contains("Your tools:"))
    }

    @Test
    fun chatInstructionsStaySmall() {
        val text = Prompt.system(Mode.Chat, facts = "", languageName = "")
        assertTrue("the chat prompt must stay tiny, was ${text.length}", text.length < 700)
    }

    @Test
    fun chatInstructionsSayNothingAboutThePhone() {
        val text = Prompt.system(Mode.Chat, facts = "", languageName = "")
        for (word in listOf("Control mode", "phone", "message", "alarm", "screen")) {
            assertFalse("Chat's permissions live in code, not here: $word", text.lowercase().contains(word))
        }
    }

    @Test
    fun controlInstructionsCarryTheToolMenu() {
        val text = Prompt.system(Mode.Control, facts = "", languageName = "", menu = "- flashlight(on)")
        assertTrue(text.contains("Your tools:"))
        assertTrue(text.contains("flashlight(on)"))
        assertTrue("commands are guessed the safe way", text.contains("choose CHAT"))
    }

    @Test
    fun theInstructionsAskForTheChosenLength() {
        val short = Prompt.system(Mode.Chat, facts = "", languageName = "", length = ReplyLength.Short)
        val long = Prompt.system(Mode.Chat, facts = "", languageName = "", length = ReplyLength.Long)

        assertTrue(short.contains(ReplyLength.Short.instruction))
        assertTrue(long.contains(ReplyLength.Long.instruction))
        assertNotEquals(short, long)
        assertTrue("the user's own ask wins over the setting", long.contains("do what the user asks"))
    }

    @Test
    fun aLongPromptNeverEatsTheUsersWords() {
        val long = "RULES ".repeat(600)
        val text = Prompt.local(long, emptyList(), "text mom I'm late")

        assertTrue("the user's own words must survive", text.contains("text mom I'm late"))
        assertTrue("the instructions are still there, just shorter", text.contains("RULES"))
    }

    @Test
    fun aLongPromptDoesNotEatTheFirstTurnEither() {
        val long = "RULES ".repeat(600)
        val text = Prompt.local(long, listOf(Turn("hi there", "hello")), "again")

        assertTrue(text.contains("hi there"))
        assertTrue(text.contains("again"))
    }

    @Test
    fun theOldBrevityRuleIsGone() {
        val text = Prompt.system(Mode.Chat, facts = "", languageName = "")

        assertFalse(text.contains("a sentence or two for simple things"))
        assertFalse("the old persona is gone too", text.contains("proper detail"))
        assertTrue(text.contains(ReplyLength.Default.instruction))
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
