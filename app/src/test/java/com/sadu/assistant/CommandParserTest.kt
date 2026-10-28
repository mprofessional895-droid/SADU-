package com.sadu.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the boundary between "do this on my phone" and "let's just talk",
 * which is exactly where a small on-device model struggles.
 */
class CommandParserTest {

    // ---- real commands must pass through ----

    @Test
    fun plainCommandsAreRecognised() {
        val commands = listOf(
            "text mom that I'm on my way",
            "send a message to john saying hello",
            "call mom",
            "open camera",
            "launch settings",
            "turn on the flashlight",
            "turn off the torch",
            "set an alarm for 6:30 am",
            "wake me at 7",
            "play some music",
            "search for cats",
            "google the news",
            "install whatsapp",
            "remember that I like tea",
            "volume up",
            "scroll down",
            "go back",
            "read the screen",
        )
        for (c in commands) {
            assertNotNull("should be a command: $c", CommandParser.commandText(c))
        }
    }

    @Test
    fun wakeWordAndPleaseAreStripped() {
        assertEquals("open whatsapp", CommandParser.commandText("hey sadu open whatsapp"))
        assertEquals("call mom", CommandParser.commandText("sadu call mom"))
        assertEquals("open camera", CommandParser.commandText("please open camera"))
    }

    // ---- conversation must NOT be mistaken for a command ----

    @Test
    fun conversationIsNotACommand() {
        val chat = listOf(
            "how are you today?",
            "what's the weather like",
            "tell me a joke",
            "I'm feeling tired",
            "the volume is too loud",
            "my alarm is broken",
            "I love this song",
            "why is the sky blue?",
            "this is a really long sentence that should be treated as conversation and not a command",
        )
        for (c in chat) {
            assertNull("should be conversation: $c", CommandParser.commandText(c))
        }
    }

    // ---- filler words must not be read as a contact or app name ----

    @Test
    fun fillerIsNotARealTarget() {
        val filler = listOf(
            "me later",
            "up about it",
            "the",
            "a conversation with me",
            "with me",
            "us",
            "something",
            "",
        )
        for (f in filler) {
            assertFalse("should not be a target: '$f'", CommandParser.isRealTarget(f))
        }
    }

    @Test
    fun realNamesAndNumbersAreTargets() {
        val real = listOf("mom", "John Smith", "sarah", "+919876543210", "play store")
        for (r in real) {
            assertTrue("should be a target: '$r'", CommandParser.isRealTarget(r))
        }
    }
}
