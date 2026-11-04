package com.sadu.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkTest {

    private val o = "<"
    private val c = ">"

    private fun tag(name: String) = o + name + c
    private fun end(name: String) = o + "/" + name + c

    @Test
    fun aThinkBlockBecomesThinkingAndTheAnswerStays() {
        val raw = "Hello. " + tag("think") + "the user wants a greeting" + end("think") + "Hi there!"
        val s = Think.split(raw)
        assertEquals("the user wants a greeting", s.thinking.trim())
        assertEquals("Hello. Hi there!", s.answer.trim())
        assertFalse(s.open)
    }

    @Test
    fun anOpenBlockShowsAsThinkingAndNoAnswer() {
        val raw = "Still working " + tag("think") + "it out"
        val s = Think.split(raw)
        assertTrue(s.open)
        assertEquals("it out", s.thinking)
        assertEquals("Still working", s.answer.trim())
    }

    @Test
    fun everyTagNameWorks() {
        for (name in listOf("think", "thinking", "thought", "reasoning")) {
            val s = Think.split(tag(name) + "why" + end(name) + "answer")
            assertEquals("why", s.thinking)
            assertEquals("answer", s.answer)
        }
    }

    @Test
    fun plainTextIsAnswerText() {
        val s = Think.split("Just an answer, no tags at all.")
        assertEquals("", s.thinking)
        assertEquals("Just an answer, no tags at all.", s.answer)
        assertFalse(s.open)
    }

    @Test
    fun severalBlocksAreAllCollected() {
        val raw = tag("think") + "one" + end("think") + "part one " + tag("think") + "two" + end("think") + "part two"
        assertEquals("part one part two", Think.answer(raw).trim())
        val thinking = Think.thinking(raw)
        assertTrue(thinking, thinking.contains("one"))
        assertTrue(thinking, thinking.contains("two"))
    }

    @Test
    fun theAnswerNeverCarriesAStrayTag() {
        val raw = tag("think") + "hidden" + end("think") + "visible"
        assertFalse(Think.answer(raw).contains("hidden"))
        assertEquals("visible", Think.answer(raw))
    }

    @Test
    fun aLongThinkBlockKeepsTheEndWhereTheAnswerForms() {
        val long = "a".repeat(Think.PreviewLimit + 25) + "tail-marker"
        val preview = Think.preview(long)
        assertTrue(preview.startsWith("\u2026"))
        assertTrue(preview.endsWith("tail-marker"))
        assertTrue(preview.length <= Think.PreviewLimit + 10)
    }

    @Test
    fun aShortThinkBlockIsShownUnchanged() {
        assertEquals("short thought", Think.preview("  short thought  "))
    }
}
