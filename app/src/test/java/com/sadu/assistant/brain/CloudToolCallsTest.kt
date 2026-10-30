package com.sadu.assistant.brain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Each provider's own function-call shape, turned into the one shape the app speaks. */
class CloudToolCallsTest {

    private fun json(s: String) = JSONObject(s)

    @Test
    fun openAiSaysItInToolCalls() {
        val body = """
            {"choices":[{"message":{"content":"","tool_calls":[
              {"id":"c1","type":"function","function":{"name":"volume","arguments":"{\"direction\":\"up\"}"}}
            ]}}]}
        """.trimIndent()

        val calls = CloudReply.calls(CloudProvider.Compatible, json(body))

        assertEquals(1, calls.size)
        assertEquals("volume", calls.first().name)
        assertEquals("""{"tool":"volume","args":{"direction":"up"}}""", CloudReply.asToolJson(calls))
        assertEquals(
            """{"tool":"volume","args":{"direction":"up"}}""",
            CloudReply.text(CloudProvider.Compatible, body),
        )
    }

    @Test
    fun openAiWholeReplyBecomesToolJson() {
        val body = """{"choices":[{"message":{"content":null,"tool_calls":[
            {"function":{"name":"flashlight","arguments":"{\"on\":true}"}}]}}]}"""
        assertEquals(
            """{"tool":"flashlight","args":{"on":true}}""",
            CloudReply.text(CloudProvider.Compatible, body),
        )
    }

    @Test
    fun openAiTextStillWinsOverACall() {
        val body = """{"choices":[{"message":{"content":"Sure, here you go.",
            "tool_calls":[{"function":{"name":"flashlight","arguments":"{}"}}]}}]}"""
        assertEquals("Sure, here you go.", CloudReply.text(CloudProvider.Compatible, body))
    }

    @Test
    fun anthropicSaysItInAToolUseBlock() {
        val body = """{"content":[
            {"type":"text","text":"Checking."},
            {"type":"tool_use","id":"toolu_1","name":"remember","input":{"fact":"sky is blue"}}
        ]}"""
        val calls = CloudReply.calls(CloudProvider.Anthropic, json(body))
        assertEquals("remember", calls.first().name)
        assertEquals(
            """{"tool":"remember","args":{"fact":"sky is blue"}}""",
            CloudReply.asToolJson(calls),
        )
    }

    @Test
    fun anthropicWholeReplyWithOnlyAToolIsACall() {
        val body = """{"content":[{"type":"tool_use","name":"battery","input":{}}]}"""
        assertEquals("""{"tool":"battery","args":{}}""", CloudReply.text(CloudProvider.Anthropic, body))
    }

    @Test
    fun geminiSaysItInAFunctionCallPart() {
        val body = """{"candidates":[{"content":{"parts":[
            {"functionCall":{"name":"open_app","args":{"app":"chrome"}}}]}}]}"""
        val calls = CloudReply.calls(CloudProvider.Gemini, json(body))
        assertEquals("open_app", calls.first().name)
        assertEquals("""{"tool":"open_app","args":{"app":"chrome"}}""", CloudReply.asToolJson(calls))
    }

    @Test
    fun openAiStreamsTheCallInPieces() {
        val nameChunk = """{"choices":[{"delta":{"tool_calls":[
            {"index":0,"id":"c1","function":{"name":"volume","arguments":""}}]}}]}"""
        val argsOne = """{"choices":[{"delta":{"tool_calls":[
            {"index":0,"function":{"arguments":"{\"direction\":"}}]}}]}"""
        val argsTwo = """{"choices":[{"delta":{"tool_calls":[
            {"index":0,"function":{"arguments":"\"up\"}"}}]}}]}"""

        val pieces = listOf(nameChunk, argsOne, argsTwo).mapNotNull {
            CloudReply.toolDelta(CloudProvider.Compatible, it)
        }
        assertEquals(3, pieces.size)

        val merged = pieces.reduce { a, b -> a.merge(b) }
        assertEquals("volume", merged.name)
        assertEquals("""{"direction":"up"}""", merged.arguments)
        assertEquals("""{"tool":"volume","args":{"direction":"up"}}""", CloudReply.asToolJson(listOf(merged)))
    }

    @Test
    fun anthropicStreamsTheNameThenTheArguments() {
        val start = """{"type":"content_block_start","index":0,
            "content_block":{"type":"tool_use","id":"toolu_1","name":"remember","input":{}}}"""
        val part1 = """{"type":"content_block_delta","index":0,
            "delta":{"type":"input_json_delta","partial_json":"{\"fact\":\"a\"}"}}"""
        val text = """{"type":"content_block_delta","index":0,
            "delta":{"type":"text_delta","text":"hi"}}"""

        val first = CloudReply.toolDelta(CloudProvider.Anthropic, start)
        val second = CloudReply.toolDelta(CloudProvider.Anthropic, part1)

        assertEquals("remember", first?.name)
        assertEquals("""{"fact":"a"}""", second?.arguments)
        assertNull("a text chunk is not a call", CloudReply.toolDelta(CloudProvider.Anthropic, text))
        assertEquals(
            """{"tool":"remember","args":{"fact":"a"}}""",
            CloudReply.asToolJson(listOf(first!!.merge(second!!))),
        )
    }

    @Test
    fun geminiStreamsTheWholeCallInOnePart() {
        val chunk = """{"candidates":[{"content":{"parts":[
            {"functionCall":{"name":"call","args":{"contact":"papa"}}}]}}]}"""
        val call = CloudReply.toolDelta(CloudProvider.Gemini, chunk)
        assertEquals("""{"tool":"call","args":{"contact":"papa"}}""", CloudReply.asToolJson(listOf(call!!)))
    }

    @Test
    fun aPlainSentenceIsNotACall() {
        assertNull(CloudReply.toolDelta(CloudProvider.Compatible, """{"choices":[{"delta":{"content":"hi"}}]}"""))
        assertNull(CloudReply.toolDelta(CloudProvider.Gemini, """{"candidates":[{"content":{"parts":[{"text":"hi"}]}}]}"""))
        assertEquals("", CloudReply.asToolJson(emptyList()))
    }
}
