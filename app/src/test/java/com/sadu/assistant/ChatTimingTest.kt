package com.sadu.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTimingTest {

    @Test
    fun oneRequestIsCountedWithItsFirstWord() {
        var now = 1_000L
        val timing = ChatTiming { now }
        timing.begin()
        now = 1_010
        timing.request(900)
        now = 3_400
        timing.firstToken()
        now = 4_000
        val line = timing.done(120)

        assertEquals(1, timing.requests)
        assertEquals(2_400L, timing.timeToFirstTokenMs())
        assertTrue(line.contains("total=3000ms"))
        assertTrue(line.contains("ttft=2400ms"))
        assertTrue(line.contains("requests=1"))
        assertTrue(line.contains("prompt=301tok~"))
        assertTrue(line.contains("reply=41tok~"))
    }

    @Test
    fun theFirstTokenIsRecordedOnceOnly() {
        var now = 0L
        val timing = ChatTiming { now }
        timing.begin()
        now = 500
        timing.firstToken()
        now = 900
        timing.firstToken()
        assertEquals(500L, timing.timeToFirstTokenMs())
    }

    @Test
    fun aSearchRoundTripShowsTwoRequests() {
        var now = 0L
        val timing = ChatTiming { now }
        timing.begin()
        timing.request(300)
        now = 2_000
        timing.firstToken()
        timing.request(1_200)
        now = 5_000
        val line = timing.done(80)
        assertEquals(2, timing.requests)
        assertTrue(line.contains("requests=2"))
    }

    @Test
    fun aSilentModelSaysNone() {
        var now = 0L
        val timing = ChatTiming { now }
        timing.begin()
        now = 700
        val line = timing.done(0, "boom")
        assertNull(timing.timeToFirstTokenMs())
        assertTrue(line.contains("ttft=none"))
        assertTrue(line.contains("failed=boom"))
    }

    @Test
    fun aNewMessageStartsFromZero() {
        var now = 0L
        val timing = ChatTiming { now }
        timing.begin()
        timing.request(500)
        now = 100
        timing.done(10)
        now = 900
        timing.begin()
        assertEquals(0, timing.requests)
        assertEquals("", timing.line())
    }
}
