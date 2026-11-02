package com.sadu.assistant.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    private fun kinds(blocks: List<MdBlock>) = blocks.map { it::class.simpleName }

    @Test
    fun aHeadingIsABlockOfItsOwn() {
        val blocks = Markdown.parse("# Title\nsome words")
        assertEquals(listOf("Heading", "Paragraph"), kinds(blocks))
        assertEquals(1, (blocks[0] as MdBlock.Heading).level)
        assertEquals("Title", (blocks[0] as MdBlock.Heading).text)
    }

    @Test
    fun bulletsStayTogether() {
        val blocks = Markdown.parse("- one\n- two\n- three")
        val list = blocks.single() as MdBlock.Bullet
        assertEquals(listOf("one", "two", "three"), list.items)
    }

    @Test
    fun numberedItemsKeepTheirNumbers() {
        val blocks = Markdown.parse("1. first\n2. second")
        val list = blocks.single() as MdBlock.Numbered
        assertEquals("1" to "first", list.items[0])
        assertEquals("2" to "second", list.items[1])
    }

    @Test
    fun aFencedBlockKeepsItsCodeAndLanguage() {
        val text = "Here:\n" + "```kotlin\nval a = 1\nval b = 2\n```\n" + "after"
        val blocks = Markdown.parse(text)
        assertEquals(listOf("Paragraph", "Code", "Paragraph"), kinds(blocks))
        val code = blocks[1] as MdBlock.Code
        assertEquals("kotlin", code.language)
        assertEquals("val a = 1\nval b = 2", code.code)
    }

    @Test
    fun anUnfinishedFenceStillShowsItsCode() {
        val blocks = Markdown.parse("```\nval a = 1")
        val code = blocks.single() as MdBlock.Code
        assertEquals("val a = 1", code.code)
    }

    @Test
    fun aTableIsReadWithItsHeaderAndRows() {
        val text = "| Name | Age |\n| --- | --- |\n| Asha | 30 |\n| Ravi | 28 |"
        val table = Markdown.parse(text).single() as MdBlock.Table
        assertEquals(listOf("Name", "Age"), table.header)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("Asha", "30"), table.rows[0])
    }

    @Test
    fun aSeparatorLineIsOnlyATableRow() {
        assertTrue(Markdown.isSeparator("|---|:--:|"))
        assertFalse(Markdown.isSeparator("| a | b |"))
    }

    @Test
    fun emptyTextHasNoBlocks() {
        assertTrue(Markdown.parse("   ").isEmpty())
    }

    @Test
    fun aHalfArrivedAnswerDoesNotThrow() {
        Markdown.parse("# Head\n- one\n| a |")
    }
}
