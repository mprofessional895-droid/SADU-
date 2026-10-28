package com.jarvis.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SamplingTest {

    @Test
    fun chatStartsSteadyEnoughToStayOnSubject() {
        assertEquals(Sampling.DefaultTemperature, Sampling.chat().temperature, 0.001f)
        assertTrue("Gemma's lively default was the problem", Sampling.chat().temperature < 1.0f)
    }

    @Test
    fun temperatureIsClampedToTheRangeSettingsOffer() {
        assertEquals(Sampling.MinTemperature, Sampling.chat(0.1f).temperature, 0.001f)
        assertEquals(Sampling.MaxTemperature, Sampling.chat(2f).temperature, 0.001f)
        assertEquals(0.8f, Sampling.chat(0.8f).temperature, 0.001f)
    }

    @Test
    fun nonsenseFallsBackToTheDefault() {
        assertEquals(Sampling.DefaultTemperature, Sampling.clampTemperature(Float.NaN), 0.001f)
    }

    @Test
    fun controlKeepsTheSteadierSamplerForToolCalls() {
        assertEquals(0.6f, Sampling.Command.temperature, 0.001f)
        assertNotEquals(Sampling.Command.temperature, Sampling.chat().temperature)
    }

    @Test
    fun theSessionMayAskForMoreThanTheEnginesOwnCap() {
        assertTrue("MediaPipe caps top-k at 40 unless told otherwise", Sampling.MaxTopK > 40)
        assertTrue(Sampling.chat().topK <= Sampling.MaxTopK)
        assertTrue(Sampling.chat().topP in 0f..1f)
    }

    @Test
    fun theLabelSaysWhatTheNumberMeans() {
        assertEquals("0.7", Sampling.temperatureLabel(0.7f))
        assertEquals("Balanced", Sampling.temperatureHint(0.7f))
        assertEquals("Careful", Sampling.temperatureHint(0.5f))
        assertEquals("Lively", Sampling.temperatureHint(1.0f))
    }
}
