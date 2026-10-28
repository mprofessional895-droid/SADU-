package com.sadu.assistant.brain

import com.sadu.assistant.Mode
import com.sadu.assistant.Turn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRequestTest {

    private val openAi = CloudConfig(
        provider = CloudProvider.Compatible,
        baseUrl = "https://api.example.com/v1",
        model = "gpt-4o-mini",
        apiKey = "sk-secret",
        maxTokens = 256,
    )

    private val claude = openAi.copy(provider = CloudProvider.Anthropic, baseUrl = "https://api.anthropic.com")
    private val gemini = openAi.copy(provider = CloudProvider.Gemini, baseUrl = "https://generativelanguage.googleapis.com/v1beta", model = "gemini-1.5-flash")

    private fun request() = LlmRequest(
        system = "SYSTEM RULES",
        turns = listOf(Turn("hi", "hello")),
        user = "how are you",
        mode = Mode.Chat,
    )

    @Test
    fun theAddressFollowsTheProvider() {
        assertEquals("https://api.example.com/v1/chat/completions", CloudRequest.url(openAi, stream = false))
        assertEquals("https://api.anthropic.com/v1/messages", CloudRequest.url(claude, stream = false))
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent",
            CloudRequest.url(gemini, stream = false),
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:streamGenerateContent?alt=sse",
            CloudRequest.url(gemini, stream = true),
        )
    }

    @Test
    fun anAddressThatIsAlreadyTheEndpointIsLeftAlone() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            CloudRequest.url(openAi.copy(baseUrl = "https://api.example.com/v1/chat/completions"), stream = false),
        )
    }

    @Test
    fun openAiStyleBodyCarriesTheWholeConversation() {
        val body = CloudRequest.body(openAi, request(), stream = true)
        assertTrue(body.contains("gpt-4o-mini"))
        assertTrue(body.contains("SYSTEM RULES"))
        assertTrue(body.contains("how are you"))
        assertTrue(body.contains("\"stream\":true"))
        assertTrue("earlier turns are sent too", body.contains("hi") && body.contains("hello"))
        assertTrue("the reply length is capped", body.contains("256"))
    }

    @Test
    fun anthropicKeepsTheSystemPromptSeparate() {
        val body = CloudRequest.body(claude, request(), stream = false)
        assertTrue(body.contains("\"system\":\"SYSTEM RULES\""))
        assertTrue(body.contains("\"max_tokens\":256"))
        assertFalse("the system line is not repeated as a message", body.contains("\"role\":\"system\""))
    }

    @Test
    fun geminiUsesItsOwnShape() {
        val body = CloudRequest.body(gemini, request(), stream = false)
        assertTrue(body.contains("systemInstruction"))
        assertTrue(body.contains("generationConfig"))
        assertTrue(body.contains("\"role\":\"model\""))
        assertTrue(body.contains("how are you"))
    }

    @Test
    fun theHeadersFollowTheProvider() {
        assertEquals("Bearer sk-secret", CloudRequest.headers(openAi, stream = false)["Authorization"])
        assertEquals("sk-secret", CloudRequest.headers(claude, stream = false)["x-api-key"])
        assertEquals("2023-06-01", CloudRequest.headers(claude, stream = false)["anthropic-version"])
        assertEquals("sk-secret", CloudRequest.headers(gemini, stream = false)["x-goog-api-key"])
        assertEquals("text/event-stream", CloudRequest.headers(openAi, stream = true)["Accept"])
    }

    @Test
    fun aConfigWithoutAModelIsNotUsable() {
        assertFalse(openAi.copy(model = "").isUsable)
        assertTrue(openAi.isUsable)
        assertEquals(CloudProvider.Compatible.defaultBase, openAi.copy(baseUrl = "  ").base)
    }
}
