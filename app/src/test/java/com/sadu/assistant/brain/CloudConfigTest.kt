package com.sadu.assistant.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whatever the user types has to be checked before a request goes anywhere. */
class CloudConfigTest {

    private fun config(
        provider: CloudProvider = CloudProvider.Compatible,
        url: String = "",
        model: String = "gpt-4o-mini",
        key: String = "",
    ) = CloudConfig(provider = provider, baseUrl = url, model = model, apiKey = key)

    @Test
    fun aMissingModelIsExplainedWithAnExample() {
        val why = CloudConfig(model = "").problem()
        assertNotNull(why)
        assertTrue(why!!, why.contains("model name"))
        assertTrue(why, why.contains("gpt-4o-mini"))
    }

    @Test
    fun anAddressWithoutASchemeStillWorks() {
        assertEquals("https://api.groq.com/v1", config(url = "api.groq.com/v1").base)
        assertEquals("https://api.groq.com/v1", config(url = "https://api.groq.com/v1/").base)
        assertEquals("https://api.groq.com/v1", config(url = "  api.groq.com/v1  ").base)
        assertNull(config(url = "api.groq.com/v1").problem())
    }

    @Test
    fun anEmptyAddressFallsBackToTheProvider() {
        assertEquals(
            CloudProvider.Anthropic.defaultBase,
            config(CloudProvider.Anthropic, "", "claude-3-5-sonnet-latest", "k").base,
        )
        assertEquals(CloudProvider.Gemini.defaultBase, config(CloudProvider.Gemini, "", "gemini-2.0-flash", "k").base)
    }

    @Test
    fun theHostedProvidersNeedAKey() {
        assertTrue(config(CloudProvider.Anthropic, model = "claude-3-5-sonnet-latest").problem()!!.contains("API key"))
        assertTrue(config(CloudProvider.Gemini, model = "gemini-2.0-flash").problem()!!.contains("API key"))
        assertNull(config(CloudProvider.Anthropic, model = "claude-3-5-sonnet-latest", key = "sk-ant").problem())
        assertNull(config(CloudProvider.Gemini, model = "gemini-2.0-flash", key = "AIza").problem())
        assertNull("a server on your own network needs no key", config(model = "llama3").problem())
    }

    @Test
    fun aServerNameWithoutADomainIsAllowed() {
        assertNull(config(url = "my-server").problem())
        assertNotNull(config(url = "http://my server/v1").problem())
    }

    @Test
    fun cleartextIsAWarningNotARefusal() {
        val remote = config(url = "http://example.com/v1")
        assertNull(remote.problem())
        assertNotNull(remote.warning())
        assertTrue(remote.warning()!!.contains("unencrypted"))
        assertNull("the local network is fine", config(url = "http://192.168.1.9:11434/v1").warning())
        assertNull(config(url = "http://localhost:11434/v1").warning())
        assertNull("https needs no warning", config(url = "https://example.com/v1").warning())
    }

    @Test
    fun eachProviderGetsItsOwnAddress() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            CloudRequest.url(config(url = "api.example.com/v1"), stream = false),
        )
        assertEquals(
            "https://api.anthropic.com/v1/messages",
            CloudRequest.url(config(CloudProvider.Anthropic, "api.anthropic.com", "claude", "k"), stream = false),
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:streamGenerateContent?alt=sse",
            CloudRequest.url(
                config(CloudProvider.Gemini, "generativelanguage.googleapis.com/v1beta", "gemini-2.0-flash", "k"),
                stream = true,
            ),
        )
    }
}
