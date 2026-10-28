package com.sadu.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The permission rules behind Chat and Control, and the two prompts they use. */
class ModeTest {

    // ----------------------------------------------------------------- the policy

    @Test
    fun chatModeAllowsOnlyTheNetwork() {
        for (tool in ModePolicy.NetworkTools) {
            assertTrue("$tool should work in Chat mode", ModePolicy.allowed(Mode.Chat, tool))
            assertTrue("$tool should work in Control mode", ModePolicy.allowed(Mode.Control, tool))
        }
    }

    @Test
    fun chatModeRefusesEveryPhoneTool() {
        for (tool in ModePolicy.PhoneTools) {
            assertFalse("$tool must not run in Chat mode", ModePolicy.allowed(Mode.Chat, tool))
            assertTrue("$tool must run in Control mode", ModePolicy.allowed(Mode.Control, tool))
            assertTrue("$tool should be recognised as a phone tool", ModePolicy.isPhoneTool(tool))
        }
    }

    @Test
    fun toolNamesAreMatchedLoosely() {
        assertTrue(ModePolicy.isPhoneTool("  SEND_SMS  "))
        assertTrue(ModePolicy.allowed(Mode.Chat, " Web_Search "))
        assertFalse(ModePolicy.allowed(Mode.Chat, "Flashlight"))
    }

    @Test
    fun refusalPointsAtControlMode() {
        assertTrue(ModePolicy.refusal().contains("Control mode"))
    }

    // -------------------------------------------------------------------- the mode

    @Test
    fun tagsRoundTripAndAnythingOddMeansChat() {
        assertEquals(Mode.Chat, Mode.from("chat"))
        assertEquals(Mode.Control, Mode.from("control"))
        assertEquals(Mode.Control, Mode.from(" CONTROL "))
        assertEquals(Mode.Chat, Mode.from(null))
        assertEquals(Mode.Chat, Mode.from(""))
        assertEquals(Mode.Chat, Mode.from("locked"))
        assertEquals(Mode.Default, Mode.Chat)
    }

    // ------------------------------------------------------------------ the prompts

    @Test
    fun chatPromptCarriesNoToolProtocol() {
        val prompt = Prompt.chat(
            facts = "The user's name is Ada",
            languageName = "Hindi",
            turns = listOf(Turn("hi", "Hello!")),
            text = "how are you",
        )
        assertFalse("Chat mode must not mention JSON", prompt.contains("JSON"))
        assertFalse("Chat mode must not mention tools", prompt.contains("tool"))
        assertFalse("Chat mode must not mention {\"", prompt.contains("{\""))
        assertFalse("no mode talk in the chat prompt", prompt.contains("Control mode"))
        assertFalse("no phone talk in the chat prompt", prompt.contains("touch the phone"))
        assertTrue(prompt.contains("The user's name is Ada"))
        assertTrue(prompt.contains("Hindi"))
        assertTrue(prompt.contains("how are you"))
        assertTrue(prompt.endsWith("<start_of_turn>model\n"))
    }

    @Test
    fun chatPromptStaysQuietAboutFactsItDoesNotHave() {
        val prompt = Prompt.chat("", "", emptyList(), "hello")
        assertFalse(prompt.contains("Things you know about the user"))
        assertFalse(prompt.contains("Always write your reply in"))
    }

    @Test
    fun controlPromptKeepsTheToolProtocol() {
        val prompt = Prompt.control(
            facts = "The user's name is Ada",
            menu = "- send_sms(contact, message): send a text",
            languageName = "Hindi",
            turns = emptyList(),
            text = "text mom I'm late",
        )
        assertTrue(prompt.contains("JSON"))
        assertTrue(prompt.contains("send_sms"))
        assertTrue(prompt.contains("text mom I'm late"))
        assertTrue(prompt.contains("Hindi"))
        assertTrue(prompt.contains("choose CHAT"))
        assertTrue(prompt.endsWith("<start_of_turn>model\n"))
    }

    @Test
    fun controlPromptStillAsksForCleanText() {
        val control = Prompt.control("", "- menu", "", emptyList(), "hi")
        assertTrue(control.contains("real line breaks"))
        assertTrue(control.contains("escape codes"))
    }

    @Test
    fun chatPromptNoLongerBansFormatting() {
        val chat = Prompt.chat("", "", emptyList(), "hi")
        assertFalse("Chat may use markdown now", chat.contains("escape codes"))
        assertFalse("no heading ban", chat.contains("no # headings"))
        assertTrue("and is told that lists are welcome", chat.contains("dashes"))
    }

    @Test
    fun longInputIsTrimmedRatherThanPastedWhole() {
        val long = "x".repeat(6000)
        val prompt = Prompt.chat("", "", emptyList(), long)
        assertFalse(prompt.contains("x".repeat(2500)))
    }
}
