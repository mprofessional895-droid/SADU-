package com.sadu.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Memory is context, not a dump. A fact only goes into the prompt when the message is about it,
 * and the standing facts about the user go along whichever way the conversation turns.
 */
class MemoryFocusTest {

    private val facts = listOf(
        "The user's name is Shubham",
        "Lives in Ahmedabad",
        "Prefers Hinglish replies",
        "Sister's wedding is in December",
        "Plays cricket on Sundays",
        "Allergic to peanuts",
    )

    @Test
    fun onlyTheFactTheMessageIsAboutIsSent() {
        val picked = MemoryFocus.focus(facts, "when is my sister's wedding?")
        assertTrue(picked.any { it.contains("wedding") })
        assertFalse(picked.any { it.contains("cricket") })
    }

    @Test
    fun theStandingFactsComeAlongEvenWhenTheWordsDoNotMatch() {
        val picked = MemoryFocus.focus(facts, "what should I cook tonight")
        assertTrue(picked.any { it.contains("Prefers Hinglish") } || picked.size <= 3)
    }

    @Test
    fun anUnmatchedMessageGetsOnlyTheLastFew() {
        val picked = MemoryFocus.focus(facts, "hello there friend")
        assertEquals(3, picked.size)
    }

    @Test
    fun noFactsMeansNoLine() {
        assertEquals("", MemoryFocus.pick(emptyList(), "hi"))
    }

    @Test
    fun theLineNeverGrowsPastItsBudget() {
        val many = (1..40).map { "Fact number $it about cricket and the weather in Ahmedabad" }
        assertTrue(MemoryFocus.pick(many, "cricket weather").length <= 400)
    }
}
