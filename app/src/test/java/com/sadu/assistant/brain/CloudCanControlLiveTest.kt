package com.sadu.assistant.brain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadu.assistant.Actions
import com.sadu.assistant.Assistant
import com.sadu.assistant.Memory
import com.sadu.assistant.Mode
import com.sadu.assistant.Prefs
import com.sadu.assistant.Tools
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Can a cloud model actually drive Control mode? Answered with a real HTTP conversation
 * against a stub provider, so the wire format matters - not a fake Brain.
 */
@RunWith(AndroidJUnit4::class)
class CloudCanControlLiveTest {

    private lateinit var ctx: Context
    private lateinit var prefs: Prefs
    private var stub: ServerSocket? = null

    private val q = "\""
    private val blank = "\n\n"

    /** A provider that answers the first request with [first], then with [then]. */
    private fun start(
        first: String,
        then: String,
        provider: CloudProvider = CloudProvider.Compatible,
    ): CloudConfig {
        val socket = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        stub = socket
        Thread {
            var n = 0
            while (!socket.isClosed) {
                try {
                    socket.accept().use { client ->
                        val reader = client.getInputStream().bufferedReader()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val body = (if (n == 0) first else then).toByteArray(Charsets.UTF_8)
                        n++
                        val head = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n" +
                            "Content-Length: " + body.size + "\r\nConnection: close\r\n\r\n"
                        client.getOutputStream().apply {
                            write(head.toByteArray(Charsets.UTF_8))
                            write(body)
                            flush()
                        }
                    }
                } catch (_: IOException) {
                    break
                }
            }
        }.apply { isDaemon = true }.start()
        return CloudConfig(
            provider = provider,
            baseUrl = "http://127.0.0.1:" + socket.localPort + "/v1",
            model = "stub",
            apiKey = "sk-stub",
            maxTokens = 256,
            timeoutMs = 5_000,
        )
    }

    @After
    fun stop() {
        stub?.close()
        stub = null
    }

    private fun esc(t: String): String = t.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun sseContent(raw: String): String = sseContentRaw(esc(raw))

    private fun sseContentRaw(text: String): String =
        "data: " + "{" + q + "choices" + q + ":[" + "{" + q + "delta" + q + ":{" +
            q + "content" + q + ":" + q + text + q + "}}]}" + blank

    /** Exactly how OpenAI-compatible providers stream a function call. */
    private fun toolCallChunk(argsJson: String): String {
        val fn = "{" + q + "arguments" + q + ":" + q + esc(argsJson) + q + "}"
        val call = "{" + q + "index" + q + ":0," + q + "id" + q + ":" + q + "call_1" + q + "," +
            q + "type" + q + ":" + q + "function" + q + "," + q + "function" + q + ":" + fn + "}"
        val delta = "{" + q + "tool_calls" + q + ":[" + call + "]}"
        return "data: " + "{" + q + "choices" + q + ":[" + "{" + q + "delta" + q + ":" + delta +
            "}]}" + blank
    }

    /** Exactly how OpenAI-compatible providers stream a function call. */
    private fun sseToolCall(name: String, argsJson: String, split: Boolean = false): String {
        val nameFn = "{" + q + "name" + q + ":" + q + name + q + "," + q + "arguments" + q + ":" + q + q + "}"
        val head = "data: " + "{" + q + "choices" + q + ":[" + "{" + q + "delta" + q + ":{" +
            q + "tool_calls" + q + ":[{" + q + "index" + q + ":0," + q + "id" + q + ":" + q +
            "call_1" + q + "," + q + "type" + q + ":" + q + "function" + q + "," +
            q + "function" + q + ":" + nameFn + "}]}}]}" + blank
        if (!split) return head + toolCallChunk(argsJson)
        // Arguments arrive a few characters at a time, as every real provider does.
        val half = argsJson.length / 2
        return head + toolCallChunk(argsJson.substring(0, half)) + toolCallChunk(argsJson.substring(half))
    }

    private fun assistant(cfg: CloudConfig): Pair<Assistant, Memory> {
        val memory = Memory(ctx)
        val actions = Actions(ctx, memory, prefs, { null })
        val cloud = CloudBrain { cfg }
        val assistant = Assistant(
            brain = cloud,
            actions = actions,
            memory = memory,
            tools = Tools(actions, memory, { prefs.mode }, { prefs.riskyControls }),
            language = { prefs.language },
            mode = { prefs.mode },
            contextTokens = { prefs.contextTokens },
            replyLength = { prefs.replyLength },
            chatStyle = { prefs.chatStyle },
            extraInstructions = { prefs.chatInstructions },
            webAnswers = { false },
            internet = { false },
            fetch = { "" },
            riskyAllowed = { prefs.riskyControls },
            brainProblem = { null },
        )
        return assistant to memory
    }

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        prefs = Prefs(ctx)
        prefs.mode = Mode.Control
        prefs.riskyControls = false
        prefs.internetEnabled = false
        prefs.cloudEnabled = true
        prefs.cloudProvider = CloudProvider.Compatible
    }

    /** A model that writes the call as text in the content stream: this works end to end. */
    @Test
    fun jsonInTheContentStreamRunsTheTool() {
        val callJson = "{\"tool\":\"remember\",\"args\":{\"fact\":\"the sky is green\"}}"
        val cfg = start(sseContent(callJson), sseContent("Saved that."))
        val (a, memory) = assistant(cfg)

        // A sentence no rule claims, so the tool call can only have come from the cloud.
        val reply = a.respond("kya haal hai bhai")

        println("JSON-IN-TEXT -> reply=$reply memory=${memory.facts()} steps=${a.lastSteps()}")
        assertEquals(listOf("the sky is green"), memory.facts())
    }

    /** OpenAI streaming function calling: the tool must actually run. */
    @Test
    fun aStreamedOpenAiToolCallRunsTheTool() {
        val args = "{\"fact\":\"the sky is blue\"}"
        val cfg = start(
            sseToolCall("remember", args, split = true),
            sseContent("Saved that."),
        )
        val (a, memory) = assistant(cfg)

        val reply = a.respond("kya haal hai bhai")

        println("OPENAI STREAM -> $reply memory=${memory.facts()} steps=${a.lastSteps()}")
        assertEquals(listOf("the sky is blue"), memory.facts())
        assertEquals("remember", a.lastSteps().firstOrNull()?.tool)
    }

    /** A provider that ignores "stream" and answers with tool_calls in one body. */
    @Test
    fun aNonStreamedOpenAiToolCallRunsTheTool() {
        val whole = """{"choices":[{"message":{"content":null,"tool_calls":[
            {"id":"c1","type":"function","function":{"name":"remember",
             "arguments":"{\"fact\":\"the sky is grey\"}"}}]}}]}"""
        // A provider that ignores "stream" answers the same way twice.
        val cfg = start(whole, whole)
        val (a, memory) = assistant(cfg)

        val reply = a.respond("kya haal hai bhai")

        println("OPENAI WHOLE -> $reply memory=${memory.facts()}")
        assertEquals(listOf("the sky is grey"), memory.facts())
    }

    /** Anthropic streaming: content_block_start names the tool, input_json_delta fills it. */
    @Test
    fun aStreamedAnthropicToolUseRunsTheTool() {
        val start = "data: " + """{"type":"content_block_start","index":0,"content_block":""" +
            """{"type":"tool_use","id":"toolu_1","name":"remember","input":{}}}""" + blank
        val one = "data: " + """{"type":"content_block_delta","index":0,"delta":""" +
            """{"type":"input_json_delta","partial_json":"{\"fact\":\"the sky is pink\"}"}}""" + blank
        val two = "data: " + """{"type":"content_block_delta","index":0,"delta":""" +
            """{"type":"text_delta","text":"Saved that."}}""" + blank
        val cfg = start(start + one, two, provider = CloudProvider.Anthropic)
        val (a, memory) = assistant(cfg)

        val reply = a.respond("kya haal hai bhai")

        println("ANTHROPIC STREAM -> $reply memory=${memory.facts()} steps=${a.lastSteps()}")
        assertEquals(listOf("the sky is pink"), memory.facts())
    }

    /** Gemini streaming: the whole call arrives as a functionCall part. */
    @Test
    fun aStreamedGeminiFunctionCallRunsTheTool() {
        val call = "data: " + """{"candidates":[{"content":{"parts":[""" +
            """{"functionCall":{"name":"remember","args":{"fact":"the sky is orange"}}}]}}]}""" + blank
        val text = "data: " + """{"candidates":[{"content":{"parts":[{"text":"Saved that."}]}}]}""" + blank
        val cfg = start(call, text, provider = CloudProvider.Gemini)
        val (a, memory) = assistant(cfg)

        val reply = a.respond("kya haal hai bhai")

        println("GEMINI STREAM -> $reply memory=${memory.facts()} steps=${a.lastSteps()}")
        assertEquals(listOf("the sky is orange"), memory.facts())
    }
}
