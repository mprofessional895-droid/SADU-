package com.sadu.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The name-guessing rules, without Android in the way. */
class SuggestTest {

    private val saved = listOf("Papa", "Mummy", "Ravi", "Sundeep", "Sandip", "Anita")

    @Test
    fun oneLetterOffIsASuggestion() {
        assertEquals(listOf("Papa"), Suggest.closest("popa", saved))
    }

    @Test
    fun aSharedStartIsASuggestion() {
        assertEquals(listOf("Papa"), Suggest.closest("pa", saved))
    }

    @Test
    fun aStrangerIsNotASuggestion() {
        assertTrue(Suggest.closest("zzzz", saved).isEmpty())
    }

    @Test
    fun aspeechStyleSlipStillFindsTheName() {
        assertEquals(listOf("Mummy"), Suggest.closest("mumy", saved))
    }

    @Test
    fun neverMoreThanThreeOptions() {
        val many = listOf("Ravi", "Ravi1", "Ravi2", "Ravi3", "Ravi4")
        assertTrue(Suggest.closest("ravi", many).size <= Suggest.MAX)
    }

    @Test
    fun theQuestionNamesTheGuess() {
        val q = Suggest.question("popa", saved)
        assertTrue(q!!, q.contains("Papa"))
        assertTrue(q, q.contains("Did you mean"))
    }

    @Test
    fun noQuestionWhenNothingIsClose() {
        assertNull(Suggest.question("zzzz", saved))
    }

    @Test
    fun distanceIsSymmetricAndSane() {
        assertEquals(0, Suggest.distance("papa", "papa"))
        assertEquals(1, Suggest.distance("papa", "pappa"))
        assertEquals(3, Suggest.distance("abc", "xyz"))
        assertEquals(3, Suggest.distance("", "abc"))
        assertEquals(Suggest.distance("sunday", "monday"), Suggest.distance("monday", "sunday"))
    }

    @Test
    fun caseAndSpacingDoNotMatter() {
        assertEquals(listOf("Papa"), Suggest.closest("  POPA ", saved))
    }
}
