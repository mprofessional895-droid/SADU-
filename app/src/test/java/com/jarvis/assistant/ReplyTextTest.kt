package com.jarvis.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyTextTest {

    @Test
    fun literalEscapesBecomeRealLineBreaks() {
        assertEquals("line one\nline two", ReplyText.clean("line one\\nline two"))
    }

    @Test
    fun neverLeavesABackslashNBehind() {
        val out = ReplyText.clean("a\\n\\nb\\nc")
        assertFalse("should contain no literal \\\\n", out.contains("\\n"))
        assertEquals("a\n\nb\nc", out)
    }

    @Test
    fun collapsesRunsOfBlankLines() {
        assertEquals("a\n\nb", ReplyText.clean("a\\n\\n\\n\\nb"))
    }

    @Test
    fun unescapesQuotes() {
        assertEquals("he said \"hi\"", ReplyText.clean("he said \\\"hi\\\""))
    }

    @Test
    fun dropsCodeFenceLines() {
        assertEquals("hello", ReplyText.clean("```json\\nhello\\n```"))
    }

    @Test
    fun dropsPromptTokensAndSpeakerLabel() {
        assertEquals("Sure thing.", ReplyText.clean("<end_of_turn>Jarvis: Sure thing.<eos>"))
    }

    @Test
    fun keepsMarkdownForTheRendererToStyle() {
        assertEquals("**Fact:** rain is wet", ReplyText.clean("**Fact:** rain is wet"))
    }

    @Test
    fun blankReplyStillSaysSomething() {
        val out = ReplyText.clean("   ")
        assertTrue(out.isNotBlank())
    }

    @Test
    fun theUserReportedCaseIsCleanedUp() {
        val raw = "That's an interesting question! Let's consider some facts.\\n\\n" +
            "**Fact:** The Great Dying was severe.\\n\\n**Fact:** Species counts fell.\\n\\n" +
            "Would you like more?"
        val out = ReplyText.clean(raw)
        assertFalse(out.contains("\\n"))
        assertTrue(out.contains("\n\n"))
        assertTrue(out.startsWith("That's an interesting question!"))
        assertTrue(out.endsWith("Would you like more?"))
    }
}
