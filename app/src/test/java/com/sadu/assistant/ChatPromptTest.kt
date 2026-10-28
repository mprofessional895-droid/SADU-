package com.sadu.assistant

import com.sadu.assistant.prompt.ChatPrompt
import com.sadu.assistant.prompt.ChatStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPromptTest {

    @Test
    fun theDefaultStyleIsThePlainOne() {
        assertEquals(ChatStyle.Minimal, ChatStyle.Default)
        assertEquals(ChatStyle.Minimal, ChatStyle.of(null))
        assertEquals(ChatStyle.Friendly, ChatStyle.of("friendly"))
        assertEquals("an unknown key falls back", ChatStyle.Minimal, ChatStyle.of("nonsense"))
    }

    @Test
    fun theTwoStylesDifferButBothStaySmall() {
        val minimal = ChatPrompt.build("", "", ReplyLength.Default, ChatStyle.Minimal)
        val friendly = ChatPrompt.build("", "", ReplyLength.Default, ChatStyle.Friendly)

        assertNotEquals(minimal, friendly)
        assertTrue("Minimal was ${minimal.length} characters", minimal.length < 700)
        assertTrue("Friendly was ${friendly.length} characters", friendly.length < 700)
    }

    @Test
    fun theUsersOwnInstructionsAreAddedAndCapped() {
        val short = ChatPrompt.build("", "", extra = "answer in Hinglish")
        assertTrue(short.contains("answer in Hinglish"))

        val huge = ChatPrompt.build("", "", extra = "x".repeat(ChatPrompt.MAX_EXTRA + 500))
        assertFalse(huge.contains("x".repeat(ChatPrompt.MAX_EXTRA + 1)))
    }

    @Test
    fun factsAndLanguageAreStillHonoured() {
        val text = ChatPrompt.build("the user is 24", "Hindi")
        assertTrue(text.contains("the user is 24"))
        assertTrue(text.contains("Hindi"))
    }

    @Test
    fun theWebLineOnlyAppearsWhenResultsAreThere() {
        assertFalse(ChatPrompt.build("", "").contains("web results"))
        assertTrue(ChatPrompt.build("", "", webResults = true).contains("web results"))
    }

    @Test
    fun nothingAboutModesOrThePhoneIsEverSaid() {
        for (style in ChatStyle.Choices) {
            val text = ChatPrompt.build("", "", style = style).lowercase()
            for (word in listOf("control mode", "chat mode", "phone", "json", "tool", "escape code")) {
                assertFalse("${style.key} prompt mentions $word", text.contains(word))
            }
        }
    }

    @Test
    fun theLengthChoiceStillReachesTheModel() {
        val short = ChatPrompt.build("", "", ReplyLength.Short)
        val long = ChatPrompt.build("", "", ReplyLength.Long)
        assertTrue(short.contains(ReplyLength.Short.instruction))
        assertTrue(long.contains(ReplyLength.Long.instruction))
    }
}
