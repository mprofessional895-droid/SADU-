package com.sadu.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerfLineTest {

    private val text =
        "prompt=12.5tok/s (400 tok in 32000ms) gen=3.1tok/s (20 tok in 6451ms) ctx=4096 threads=4"

    @Test
    fun promptAndGenerationAreKeptApart() {
        val line = PerfLine.parse(text)!!
        assertEquals(12.5, line.promptTokS, 0.01)
        assertEquals(400, line.promptTokens)
        assertEquals(32000L, line.promptMs)
        assertEquals(3.1, line.genTokS, 0.01)
        assertEquals(20, line.genTokens)
        assertEquals(4, line.threads)
    }

    @Test
    fun nothingToMeasureIsNotZero() {
        assertNull(PerfLine.parse(""))
        assertNull(PerfLine.parse(null))
        assertNull(PerfLine.parse("generated 12 chars"))
    }

    @Test
    fun theLogLineCarriesEveryPhase() {
        val log = PerfLine.parse(text)!!.line("On-device llama.cpp", Mode.Control, 2, 340)
        assertEquals(true, log.contains("prefill_ms=32000"))
        assertEquals(true, log.contains("gen_tok_s=3.1"))
        assertEquals(true, log.contains("steps=2"))
        assertEquals(true, log.contains("tool_ms=340"))
    }
}
