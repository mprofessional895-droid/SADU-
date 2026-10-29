package com.sadu.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The words people use for family, and the safety of the step budget.
 *
 * Pure Kotlin, no Android: the point is that "papa" reaches a contact saved as "Dad", and
 * that an unknown word is only ever itself - nothing is guessed.
 */
class RelationsTest {

    @Test
    fun everySpellingOfParentReachesTheSameName() {
        val papa = Relations.variants("papa")
        assertTrue(papa.contains("dad"))
        assertTrue(papa.contains("father"))
        assertTrue(papa.contains("appa"))
        // The reverse direction too: a user who says "dad" still finds "Papa".
        assertTrue(Relations.variants("dad").contains("papa"))
        assertTrue(Relations.variants("mummy").contains("mom"))
        assertTrue(Relations.variants("mom").contains("maa"))
    }

    @Test
    fun anUnknownNameIsOnlyItself() {
        assertEquals(listOf("ravi"), Relations.variants("ravi"))
        assertEquals(listOf("ravi"), Relations.variants("  Ravi  "))
    }

    @Test
    fun theStepBudgetIsSmallAndReal() {
        assertTrue(ToolSteps.Max in 2..6)
    }

    @Test
    fun aToolTheMenuDoesNotOfferIsCaught() {
        val menu = "- battery(): report the battery level\n- time(): tell the current time"
        assertTrue(Steps.unknownTool("teleport", menu))
        assertTrue(Steps.unknownTool("", menu))
        assertEquals(false, Steps.unknownTool("battery", menu))
        assertEquals(false, Steps.unknownTool("TIME", menu))
    }

    @Test
    fun theStepLogReadsBackWhatHappened() {
        val log = StepLog()
        assertTrue(log.summary().isNotBlank())
        log.add("call", "Calling Papa.")
        log.add("send_sms", "Message sent to Papa.")
        assertEquals(2, log.size)
        assertNotNull(log.all().first())
        assertTrue(log.summary().contains("Calling Papa."))
        assertTrue(log.summary().contains("Message sent to Papa."))
    }
}
