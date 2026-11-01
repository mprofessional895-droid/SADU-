package com.sadu.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sadu.assistant.brain.Brain
import com.sadu.assistant.brain.LlmRequest
import com.sadu.assistant.prompt.GemmaChatTemplate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The temporary prompt editor. Off must change nothing at all; on must send the typed text and
 * nothing else, including nothing else when the box is empty.
 */
@RunWith(RobolectricTestRunner::class)
class PromptOverrideTest {

    private lateinit var ctx: Context
    private lateinit var prefs: Prefs
    private lateinit var override: PromptOverride

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        prefs = Prefs(ctx)
        prefs.customPromptOn = false
        prefs.customChatPrompt = ""
        prefs.customControlPrompt = ""
        override = PromptOverride(prefs)
    }

    @After
    fun tearDown() {
        prefs.customPromptOn = false
        prefs.customChatPrompt = ""
        prefs.customControlPrompt = ""
    }

    @Test
    fun offMeansTheShippedPrompt() {
        prefs.customChatPrompt = "ignored"
        assertNull(override.system(Mode.Chat))
        assertNull(override.system(Mode.Control))
    }

    @Test
    fun onSendsWhatWasTypedForThatModeOnly() {
        prefs.customPromptOn = true
        prefs.customChatPrompt = "chat text"
        prefs.customControlPrompt = "control text"
        assertEquals("chat text", override.system(Mode.Chat))
        assertEquals("control text", override.system(Mode.Control))
    }

    @Test
    fun anEmptyBoxOnMeansNoInstructionsAtAll() {
        prefs.customPromptOn = true
        assertEquals("", override.system(Mode.Chat))
        assertEquals("", override.system(Mode.Control))
    }

    @Test
    fun theEmptyPromptStillCarriesTheUserMessageToTheModel() {
        val prompt = GemmaChatTemplate.build("", emptyList(), "what is 2 plus 2")
        assertTrue(prompt.contains("what is 2 plus 2"))
        assertFalse(prompt.contains("You are Sadu"))
    }
}
