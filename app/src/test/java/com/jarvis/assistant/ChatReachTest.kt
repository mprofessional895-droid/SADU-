package com.jarvis.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        actions = Actions(ctx, Memory(ctx), Prefs(ctx), { null })
    }

    private fun ruled(text: String) = actions.tryHandleChatMode(text)

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
    fun onlyAnExplicitWebRequestGoesToTheBrowser() {
        assertNotNull(ruled("search for cat videos"))
        assertNull("mentioning a search is not a search", ruled("I search for meaning sometimes"))
    }
}
