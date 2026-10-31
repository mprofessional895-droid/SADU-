package com.sadu.assistant.brain

import com.sadu.assistant.Mode
import com.sadu.assistant.ToolSpec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tools are declared to the provider, so the model decides which one to call. A tool
 * declared here is also what stops a strict provider rejecting the reply with
 * "Tool choice is none, but model called a tool".
 */
class CloudToolsTest {

    private val specs = listOf(
        ToolSpec("open_app", "app", "open an installed app by name"),
        ToolSpec("flashlight", "on (true|false)", "turn the flashlight on or off"),
    )

    private val cfg = CloudConfig(
        provider = CloudProvider.Compatible,
        baseUrl = "https://api.example.com/v1",
        model = "gpt-4o-mini",
        apiKey = "sk",
        maxTokens = 128,
    )

    private fun request(mode: Mode) = com.sadu.assistant.brain.LlmRequest(
        system = "SYSTEM",
        turns = emptyList(),
        user = "open whatsapp",
        mode = mode,
    )

    @Test
    fun openAiGetsAFunctionForEveryTool() {
        val body = JSONObject(CloudRequest.body(cfg, request(Mode.Control), stream = true, tools = specs))
        val tools = body.getJSONArray("tools")
        assertEquals(2, tools.length())
        val first = tools.getJSONObject(0).getJSONObject("function")
        assertEquals("open_app", first.getString("name"))
        assertEquals(
            "app",
            first.getJSONObject("parameters").getJSONObject("properties").names().getString(0),
        )
        assertEquals("auto", body.getString("tool_choice"))
    }

    @Test
    fun chatModeDeclaresNoTools() {
        val body = CloudRequest.body(cfg, request(Mode.Chat), stream = true, tools = specs)
        assertFalse(body.contains("\"tools\""))
        assertFalse(body.contains("tool_choice"))
    }

    @Test
    fun theFlagArgumentIsASingleProperty() {
        val declared = CloudTools.openAi(specs)
        val props = declared.getJSONObject(1).getJSONObject("function")
            .getJSONObject("parameters").getJSONObject("properties")
        assertEquals(1, props.length())
        assertTrue(props.has("on"))
    }

    @Test
    fun anthropicAndGeminiUseTheirOwnShapes() {
        val claude = CloudTools.anthropic(specs)
        assertEquals("open_app", claude.getJSONObject(0).getString("name"))
        assertTrue(claude.getJSONObject(0).has("input_schema"))

        val g = CloudTools.gemini(specs)
        val names = g.getJSONObject(0).getJSONArray("functionDeclarations")
        assertEquals("open_app", names.getJSONObject(0).getString("name"))
        assertEquals(
            "STRING",
            names.getJSONObject(0).getJSONObject("parameters")
                .getJSONObject("properties").getJSONObject("app").getString("type"),
        )
    }
}
