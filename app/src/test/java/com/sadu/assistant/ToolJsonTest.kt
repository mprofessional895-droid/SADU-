package com.sadu.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The shapes a cloud model uses for a tool call. A model that wraps its JSON in a fence or
 * uses the OpenAI name/arguments pair is still asking for the same tool.
 */
class ToolJsonTest {

    @Test
    fun bareJsonIsRead() {
        val call = ToolJson.first("""{"tool":"torch","on":true}""")
        assertEquals("torch", call!!.first)
        assertEquals("true", call.second["on"])
    }

    @Test
    fun aFencedBlockIsRead() {
        val call = ToolJson.first("Sure.\n```json\n{\"tool\":\"volume\",\"direction\":\"up\"}\n```")
        assertEquals("volume", call!!.first)
        assertEquals("up", call.second["direction"])
    }

    @Test
    fun theOpenAiPairIsRead() {
        val call = ToolJson.first("""{"name":"call","arguments":{"who":"papa"}}""")
        assertEquals("call", call!!.first)
        assertEquals("papa", call.second["who"])
    }

    @Test
    fun argumentsAsAJsonStringAreRead() {
        val call = ToolJson.first("""{"name":"call","arguments":"{\"who\":\"papa\"}"}""")
        assertEquals("call", call!!.first)
        assertEquals("papa", call.second["who"])
    }

    @Test
    fun aNestedFunctionObjectIsRead() {
        val call = ToolJson.first("""{"function":{"name":"date","arguments":{}}}""")
        assertEquals("date", call!!.first)
    }

    @Test
    fun plainProseIsNotACall() {
        assertNull(ToolJson.first("I would rather just talk about that."))
    }

    @Test
    fun anUnclosedObjectIsNotACall() {
        assertNull(ToolJson.first("""{"tool":"torch"""))
    }

    @Test
    fun theFirstOfSeveralCallsWins() {
        val all = ToolJson.all("""{"tool":"date"} then {"tool":"torch"}""")
        assertEquals(2, all.size)
        assertEquals("date", all.first().first)
        assertEquals("torch", all.last().first)
    }

    @Test
    fun bracesInsideStringsDoNotEndTheObject() {
        val call = ToolJson.first("""{"tool":"message","body":"use {this} please"}""")
        assertEquals("message", call!!.first)
        assertEquals("use {this} please", call.second["body"])
    }
}
