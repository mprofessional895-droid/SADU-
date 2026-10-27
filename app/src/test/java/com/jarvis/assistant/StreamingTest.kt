package com.jarvis.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingTest {

    @Test
    fun pacerRevealsAShortAnswerOneCharacterAtATime() {
        assertEquals(1, Pacer.next(0, 10))
        var shown = 0
        repeat(20) { shown = Pacer.next(shown, 10) }
        assertEquals(10, shown)
        assertTrue(Pacer.done(10, 10, finished = true))
        assertFalse("not finished while text is still arriving", Pacer.done(10, 10, finished = false))
    }

    @Test
    fun pacerNeverRunsPastTheText() {
        var shown = 0
        repeat(200) { shown = Pacer.next(shown, 40) }
        assertEquals(40, shown)
    }

    @Test
    fun pacerShowsALongReplyInSeveralSteps() {
        var shown = 0
        var steps = 0
        while (shown < 1700) {
            val next = Pacer.next(shown, 1700)
            if (next == shown) break
            shown = next
            steps++
        }
        assertTrue("revealed in steps, not all at once", steps > 4)
        assertEquals(1700, shown)
    }

    @Test
    fun theGateShowsProseImmediately() {
        val shown = StringBuilder()
        val gate = StreamGate { delta -> shown.append(delta) }
        gate.feed("Hello")
        gate.feed(" there")
        gate.finish()
        assertEquals("Hello there", shown.toString())
        assertFalse(gate.looksLikeToolCall)
    }

    @Test
    fun theGateKeepsToolJsonOffTheScreen() {
        val shown = StringBuilder()
        val gate = StreamGate { delta -> shown.append(delta) }
        gate.feed("{\"tool\":\"flashlight\"")
        gate.feed(",\"args\":{\"on\":\"true\"}}")
        gate.finish()
        assertEquals("", shown.toString())
        assertTrue(gate.looksLikeToolCall)
    }

    @Test
    fun theGateWaitsOutLeadingWhitespaceBeforeDeciding() {
        val shown = StringBuilder()
        val gate = StreamGate { delta -> shown.append(delta) }
        gate.feed("  ")
        assertEquals("nothing is shown for whitespace alone", "", shown.toString())
        gate.feed("Sure, here you go")
        assertEquals("  Sure, here you go", shown.toString())
        assertFalse(gate.looksLikeToolCall)
    }

    @Test
    fun theBufferCollectsDeltas() {
        val buffer = StreamBuffer()
        buffer.append("one ")
        buffer.append("two")
        assertEquals(7, buffer.length)
        assertEquals("one two", buffer.snapshot())
    }
}
