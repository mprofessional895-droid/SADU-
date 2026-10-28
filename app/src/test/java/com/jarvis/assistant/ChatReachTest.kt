package com.jarvis.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Chat mode has to stay a conversation. Only Jarvis's own memory, the clock, the calendar
 * and the network may answer before the model does; everything else must reach the model.
 * This is what the wording of an answer depends on, so it is pinned down here.
 */
@RunWith(AndroidJUnit4::class)
class ChatReachTest {

    private lateinit var actions: Actions
    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        prefs = Prefs(ctx)
        prefs.webAnswers = true
        actions = Actions(ctx, Memory(ctx), prefs, { null })
    }

    private fun ruled(text: String) = actions.tryHandleChatMode(text)

    /** The internet toggle is off by default in this app, and the network rule keys on it. */
    private fun online(body: () -> Unit) {
        val was = prefs.internetEnabled
        prefs.internetEnabled = true
        try {
            body()
        } finally {
            prefs.internetEnabled = was
        }
    }

    private fun withWebAnswers(on: Boolean, body: () -> Unit) {
        prefs.webAnswers = on
        try {
            body()
        } finally {
            prefs.webAnswers = true
        }
    }

    @Test
    fun ordinaryConversationReachesTheModel() {
        val lines = listOf(
            "I am feeling tired today",
            "what's up",
            "tell me about the moon",
            "who are you?",
            "I don't know what time I will finish work",
            "how was your day",
            "what day is it best to travel to Delhi",
            "I remember my first bike",
        )
        for (line in lines) {
            assertNull("this is chat, not a command: $line", ruled(line))
        }
    }

    @Test
    fun theClockAndCalendarStillAnswer() {
        assertNotNull(ruled("what time is it"))
        assertNotNull(ruled("jarvis, what's the time please"))
        assertNotNull(ruled("what's the date"))
    }

    @Test
    fun jarvisOwnMemoryStillAnswers() {
        assertNotNull(ruled("remember that I like tea"))
        assertNotNull(ruled("my name is Asha"))
    }

    @Test
    fun onlyAnExplicitWebRequestCountsAsASearch() {
        online {
            assertNull("with web answers on, the Assistant looks this up", ruled("search for cat videos"))
            assertNull("mentioning a search is not a search", ruled("I search for meaning sometimes"))
        }
    }

    @Test
    fun withNothingToLookItUpWithChatModeSaysSoAndOpensNothing() {
        prefs.internetEnabled = false
        val answer = ruled("search for cat videos")
        assertNotNull("an honest sentence instead of a made-up answer", answer)
        assertTrue(answer!!.contains("Internet is off"))
    }

    @Test
    fun chatModeNeverOpensAnythingItself() {
        online {
            assertNull("no browser tab from Chat mode", ruled("search for cat videos"))
            withWebAnswers(false) {
                assertNull("not even with background answers off", ruled("search for cat videos"))
            }
            assertNull("no Play Store from Chat mode", ruled("install whatsapp"))
            assertNull("no link opened from Chat mode", ruled("open google.com"))
        }
    }

    @Test
    fun theQueryIsReadOutOfTheRequest() {
        assertEquals("cat videos", actions.searchQuery("search for cat videos"))
        assertEquals("24byme", actions.searchQuery("search about 24byme"))
        assertEquals("the eiffel tower", actions.searchQuery("look up the eiffel tower"))
        assertEquals("python", actions.searchQuery("google python"))
        assertNull("a sentence is not a search", actions.searchQuery("I search for meaning sometimes"))
        assertNull("a question mark means chat", actions.searchQuery("search for cat videos?"))
        assertNull(actions.searchQuery("search"))
    }
}
