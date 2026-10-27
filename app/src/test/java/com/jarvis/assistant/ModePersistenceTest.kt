package com.jarvis.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * What the modes do with real Android pieces: the saved setting, the saved conversations,
 * and the rule layer that must not touch the phone in Chat mode.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ModePersistenceTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theModeStartsAsChatAndIsRemembered() {
        val prefs = Prefs(ctx)
        assertEquals(Mode.Chat, prefs.mode)
        prefs.mode = Mode.Control
        assertEquals(Mode.Control, Prefs(ctx).mode)
        prefs.mode = Mode.Chat
        assertEquals(Mode.Chat, Prefs(ctx).mode)
    }

    @Test
    fun aConversationRemembersTheModeItStartedIn() {
        val store = ChatHistory(ctx)
        store.save(
            listOf(
                ChatSession(1L, "Set an alarm", 1L, listOf(ChatMsg("set an alarm", true)), Mode.Control),
                ChatSession(2L, "How do I cook pasta?", 2L, listOf(ChatMsg("how?", true)), Mode.Chat),
            ),
        )
        val back = ChatHistory(ctx).load().sortedBy { it.id }
        assertEquals(2, back.size)
        assertEquals(Mode.Control, back[0].mode)
        assertEquals(Mode.Chat, back[1].mode)
    }

    @Test
    fun chatsSavedBeforeModesExistedStillLoad() {
        val file = File(ctx.filesDir, "chats.json")
        val msg = JSONObject().put("t", "hello").put("u", true).put("i", 1L).put("at", 2L)
        file.writeText(
            JSONArray().put(
                JSONObject()
                    .put("id", 5L)
                    .put("title", "Old chat")
                    .put("created", 3L)
                    .put("msgs", JSONArray().put(msg)),
            ).toString(),
        )
        val back = ChatHistory(ctx).load()
        assertEquals(1, back.size)
        assertEquals("Old chat", back[0].title)
        assertEquals(Mode.Chat, back[0].mode)
    }

    @Test
    fun chatModeRulesNeverReachThePhone() {
        val actions = Actions(ctx, Memory(ctx), Prefs(ctx)) { null }
        for (command in listOf(
            "turn on the flashlight",
            "set an alarm for 6:30 am",
            "read the screen",
            "what's my battery",
            "send a message to Mom saying I'm late",
            "call Mom",
            "open WhatsApp",
            "turn the volume up",
        )) {
            assertNull("Chat mode must not act on \"$command\"", actions.tryHandleChatMode(command))
        }
    }

    @Test
    fun chatModeRulesStillAnswerTheClockAndTheNetwork() {
        val actions = Actions(ctx, Memory(ctx), Prefs(ctx)) { null }
        assertTrue(actions.tryHandleChatMode("what time is it")!!.contains("It's"))
        // The internet toggle is off by default, so the network rule answers honestly.
        assertTrue(actions.tryHandleChatMode("search the web for cats")!!.contains("Internet is off"))
    }

    @Test
    fun chatModeRulesStillRememberThingsAboutTheUser() {
        val memory = Memory(ctx)
        val actions = Actions(ctx, memory, Prefs(ctx)) { null }
        assertEquals(
            "Got it, I'll remember: I like tea.",
            actions.tryHandleChatMode("remember that I like tea"),
        )
        assertTrue(memory.facts().any { it.contains("I like tea") })
        assertTrue(actions.tryHandleChatMode("what do you remember about me")!!.contains("I like tea"))
    }

    @Test
    fun controlModeStillReadsTheDevice() {
        val actions = Actions(ctx, Memory(ctx), Prefs(ctx)) { null }
        assertNotNull(actions.tryHandle("what's my battery"))
    }
}
