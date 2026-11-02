package com.sadu.assistant

import com.sadu.assistant.perf.DeviceState
import com.sadu.assistant.perf.Heat
import com.sadu.assistant.perf.PerfMode
import com.sadu.assistant.perf.PerfPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The performance rules, checked without a phone. These are the only numbers that decide how
 * hard the model is allowed to run, so they are worth pinning down.
 */
class PerfPlanTest {

    private val cool = DeviceState(heat = Heat.None, availableMb = 3000, totalMb = 8000)
    private val hot = DeviceState(heat = Heat.Severe, availableMb = 3000, totalMb = 8000)
    private val tight = DeviceState(heat = Heat.None, availableMb = 400, totalMb = 8000)

    @Test
    fun performance_uses_every_big_core_when_cool() {
        assertEquals(4, PerfPlan.threads(PerfMode.Performance, cool, base = 4))
    }

    @Test
    fun balanced_leaves_one_core_free() {
        assertEquals(3, PerfPlan.threads(PerfMode.Balanced, cool, base = 4))
    }

    @Test
    fun saver_sticks_to_two() {
        assertEquals(2, PerfPlan.threads(PerfMode.Saver, cool, base = 4))
    }

    @Test
    fun heat_costs_a_thread() {
        assertEquals(3, PerfPlan.threads(PerfMode.Performance, hot, base = 4))
        assertEquals(2, PerfPlan.threads(PerfMode.Balanced, hot, base = 4))
    }

    @Test
    fun memory_pressure_costs_a_thread() {
        assertEquals(3, PerfPlan.threads(PerfMode.Performance, tight, base = 4))
    }

    @Test
    fun never_below_one_thread() {
        assertEquals(1, PerfPlan.threads(PerfMode.Saver, hot, base = 2))
    }

    @Test
    fun long_answers_are_capped_when_hot_or_tight() {
        assertEquals(256, PerfPlan.tokenBudget(PerfMode.Performance, hot, asked = 3000))
        assertEquals(384, PerfPlan.tokenBudget(PerfMode.Performance, tight, asked = 3000))
    }

    @Test
    fun cap_never_lengthens_a_short_answer() {
        assertEquals(120, PerfPlan.tokenBudget(PerfMode.Performance, hot, asked = 120))
    }

    @Test
    fun context_shrinks_under_pressure() {
        assertEquals(2048, PerfPlan.contextTokens(PerfMode.Performance, tight, asked = 4096))
        assertEquals(3072, PerfPlan.contextTokens(PerfMode.Performance, hot, asked = 4096))
        assertEquals(4096, PerfPlan.contextTokens(PerfMode.Performance, cool, asked = 4096))
    }

    @Test
    fun loading_is_refused_when_the_model_cannot_fit() {
        assertTrue(PerfPlan.canLoad(1L * 1024 * 1024 * 1024, cool))
        assertFalse(PerfPlan.canLoad(1L * 1024 * 1024 * 1024, tight))
    }

    @Test
    fun unknown_memory_never_blocks_a_load() {
        assertTrue(PerfPlan.canLoad(4L * 1024 * 1024 * 1024, DeviceState()))
    }

    @Test
    fun mode_tags_round_trip_and_fall_back_to_balanced() {
        PerfMode.entries.forEach { assertEquals(it, PerfMode.from(it.tag)) }
        assertEquals(PerfMode.Balanced, PerfMode.from("nonsense"))
        assertEquals(PerfMode.Balanced, PerfMode.from(null))
    }
}
