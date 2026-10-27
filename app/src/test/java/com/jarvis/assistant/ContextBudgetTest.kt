package com.jarvis.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBudgetTest {

    @Test
    fun estimateIsZeroForNothing() {
        assertEquals(0, ContextBudget.estimate(""))
    }

    @Test
    fun estimateIsDeliberatelyPessimistic() {
        // About three characters per token, which suits English and Hindi alike.
        assertEquals(4, ContextBudget.estimate("abcdefghij"))
    }

    @Test
    fun clampStaysWithinTheOfferedChoices() {
        assertEquals(ContextBudget.Default, ContextBudget.clamp(0))
        assertEquals(ContextBudget.Min, ContextBudget.clamp(100))
        assertEquals(ContextBudget.Max, ContextBudget.clamp(100_000))
        assertEquals(2048, ContextBudget.clamp(2048))
    }

    @Test
    fun labelReadsTheWayPeopleSayIt() {
        assertEquals("4k", ContextBudget.label(4096))
        assertEquals("512", ContextBudget.label(512))
    }

    @Test
    fun theNewestTurnsAreTheFirstToBeKept() {
        val turns = (1..40).map {
            Turn("question $it ".padEnd(300, '.'), "answer $it ".padEnd(300, '.'))
        }
        val chosen = ContextBudget.select(turns, windowTokens = 4096, instructionTokens = 500)
        assertTrue("something is kept", chosen.isNotEmpty())
        assertFalse("a long chat is trimmed", chosen.size == turns.size)
        assertEquals("the newest turn is kept", turns.last(), chosen.last())
    }

    @Test
    fun theOpeningTurnIsKeptWhenItStillFits() {
        val turns = listOf(
            Turn("my name is Asha", "Nice to meet you, Asha."),
            Turn("what is the weather", "I cannot check that here."),
            Turn("and tomorrow", "Still cannot check it here."),
        )
        val chosen = ContextBudget.select(turns, windowTokens = 4096, instructionTokens = 400)
        assertEquals(turns, chosen)
    }

    @Test
    fun nothingIsSentWhenTheWindowCannotHoldTheInstructions() {
        val turns = listOf(Turn("a".repeat(900), "b".repeat(900)))
        assertTrue(ContextBudget.select(turns, windowTokens = 1024, instructionTokens = 1000).isEmpty())
    }
}
