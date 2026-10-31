package com.sadu.assistant.brain

import com.sadu.assistant.ToolSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two things that make a tool turn cheap: a shaped grammar, and a hard stop at the end. */
class ToolTurnTest {

    private val specs = listOf(
        ToolSpec("open_app", "app", "open an installed app by name"),
        ToolSpec("whatsapp", "contact, message", "write a WhatsApp message"),
    )

    @Test
    fun theGrammarOnlyAllowsCallsToRealTools() {
        val grammar = ToolGrammar.forTools(specs)!!
        assertTrue(grammar.startsWith("root ::="))
        assertTrue(grammar.contains("\"open_app\" | \"whatsapp\""))
        assertTrue("all keys are declared", grammar.contains("\"app\" | \"contact\" | \"message\""))
        assertTrue("escaped values", grammar.contains("\"<escape>\""))
        assertTrue("the turn ends itself", grammar.contains("\"<end_function_call>\""))
    }

    @Test
    fun noToolsMeansNoGrammar() {
        assertNull(ToolGrammar.forTools(emptyList()))
        assertNull(ToolGrammar.forTools(listOf(ToolSpec("open_app", "", "open an app"))))
    }

    @Test
    fun aReplyIsCutAtTheEndMark() {
        val marks = StopMarks(ToolGrammar.STOPS)
        val whole = "call:open_app{app:<escape>youtube<escape>}<end_function_call><escape><escape>"
        assertEquals(
            "call:open_app{app:<escape>youtube<escape>}",
            marks.truncate(whole),
        )
    }

    @Test
    fun aMarkSplitAcrossPiecesIsStillCaught() {
        val marks = StopMarks(listOf("<end_function_call>"))
        val sb = StringBuilder()
        // Every character arrives on its own, the way a tokenizer hands them over.
        for (ch in "ok<end_function_call>tail") sb.append(marks.take(ch.toString()))
        assertEquals("ok", sb.toString())
        assertTrue(marks.isStopped)
    }

    @Test
    fun nothingIsHeldBackForever() {
        val marks = StopMarks(listOf("<end_function_call>"))
        val out = StringBuilder()
        for (ch in "a plain sentence with no mark at all.") out.append(marks.take(ch.toString()))
        assertFalse(marks.isStopped)
        assertTrue(out.toString().startsWith("a plain sentence"))
    }

    @Test
    fun noStopsMeansNothingChanges() {
        val marks = StopMarks(emptyList())
        assertEquals("hello", marks.take("hello"))
        assertEquals("a<b", marks.truncate("a<b"))
    }

    @Test
    fun anEmptyPieceIsHarmless() {
        val marks = StopMarks(listOf("<end_function_call>"))
        assertEquals("", marks.take(""))
        assertFalse(marks.isStopped)
    }

    @Test
    fun theStopListHasTheMarkTheModelWrites() {
        assertNotNull(ToolGrammar.forTools(specs))
        assertEquals(listOf("<end_function_call>"), ToolGrammar.STOPS)
    }
}
