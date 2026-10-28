package com.sadu.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The reply-length choice: what it means for each brain, and that it is remembered. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ReplyLengthTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun longerChoicesAllowMoreTokens() {
        assertTrue(ReplyLength.Short.tokens < ReplyLength.Normal.tokens)
        assertTrue(ReplyLength.Normal.tokens < ReplyLength.Long.tokens)
        assertEquals(ReplyLength.Choices, ReplyLength.Choices.sortedBy { it.tokens })
    }

    @Test
    fun everyChoiceTellsTheModelSomething() {
        ReplyLength.Choices.forEach { choice ->
            assertTrue(choice.key.isNotBlank())
            assertTrue(choice.label.isNotBlank())
            assertTrue("$choice must tell the on-device model what to do", choice.instruction.length > 20)
        }
    }

    @Test
    fun anUnknownOrMissingChoiceFallsBackToNormal() {
        assertEquals(ReplyLength.Normal, ReplyLength.of(null))
        assertEquals(ReplyLength.Normal, ReplyLength.of(""))
        assertEquals(ReplyLength.Normal, ReplyLength.of("enormous"))
        assertEquals(ReplyLength.Long, ReplyLength.of("long"))
    }

    @Test
    fun theChoiceSurvivesARestart() {
        val prefs = Prefs(ctx)
        val before = prefs.replyLength
        try {
            prefs.replyLength = ReplyLength.Long
            assertEquals(ReplyLength.Long, Prefs(ctx).replyLength)
        } finally {
            prefs.replyLength = before
        }
    }
}
