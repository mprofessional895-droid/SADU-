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
    private fun start(first: String, then: String): CloudConfig {
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
            provider = CloudProvider.Compatible,
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
    private fun sseToolCall(name: String, argsJson: String): String {
        val fn = "{" + q + "name" + q + ":" + q + name + q + "," +
            q + "arguments" + q + ":" + q + esc(argsJson) + q + "}"
        val call = "{" + q + "index" + q + ":0," + q + "id" + q + ":" + q + "call_1" + q + "," +
            q + "type" + q + ":" + q + "function" + q + "," + q + "function" + q + ":" + fn + "}"
        val delta = "{" + q + "tool_calls" + q + ":[" + call + "]}"
        return "data: " + "{" + q + "choices" + q + ":[" + "{" + q + "delta" + q + ":" + delta +
            "}]}" + blank
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

    /**
     * The question that matters: does a provider's streamed function call reach the
     * parser at all? Prints the text the stream produced, so the answer is evidence.
     */
    @Test
    fun nativeToolCallsAreDroppedToday() {
        val cfg = start(
            sseToolCall("remember", "{\"fact\":\"the sky is blue\"}"),
            sseContent("Saved that."),
        )
        val req = LlmRequest("system", emptyList(), "do the thing", Mode.Control)
        val outcome = runCatching { CloudBrain { cfg }.generateStream(req) { } }
        println("STREAM OUTCOME = ${outcome.getOrNull()} / ${outcome.exceptionOrNull()}")

        // The known gap: CloudReply.delta reads only "content", so a provider's
        // "tool_calls" (and the Anthropic / Gemini equivalents) never reach ToolJson.
        // When CloudReply flattens function calls into the stream, this flips to a value.
        assertTrue(
            "native tool calls now reach the parser - flip this assertion",
            outcome.getOrNull().isNullOrEmpty(),
        )
    }
}
