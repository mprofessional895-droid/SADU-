package com.sadu.assistant.brain

import com.sadu.assistant.Mode
import com.sadu.assistant.ToolSpec
import com.sadu.assistant.Turn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * Real calls to Google's generateContent API, using the exact setup reported from the phone:
 * service root, Gemini provider, gemini-3.8-flash.
 *
 * Skipped unless SADU_LIVE_GEMINI_KEY is in the environment, so CI and normal runs stay offline.
 */
@RunWith(RobolectricTestRunner::class)
class GeminiLiveTest {

    private val key = System.getenv("SADU_LIVE_GEMINI_KEY").orEmpty()
    private val base = "https://generativelanguage.googleapis.com/v1beta"
    private val model = "gemini-3.8-flash"

    private fun cfg(model: String = this.model) = CloudConfig(
        provider = CloudProvider.Gemini,
        baseUrl = base,
        model = model,
        apiKey = key,
        maxTokens = 160,
        timeoutMs = 90_000,
    )

    private fun brain(model: String = this.model) = CloudBrain({ emptyList() }) { cfg(model) }

    private fun ask(user: String, turns: List<Turn> = emptyList()) = LlmRequest(
        system = "Answer in one short line.",
        turns = turns,
        user = user,
        mode = Mode.Chat,
    )

    /** Google answers 503 "high demand" in bursts; that is not the thing under test. */
    private fun <T> retrying(block: () -> T): T {
        var last: Throwable? = null
        repeat(4) {
            try {
                return block()
            } catch (e: IOException) {
                last = e
                val busy = e is java.net.SocketTimeoutException ||
                    e.message.orEmpty().contains("Cloud error 503")
                if (!busy) throw e
                Thread.sleep(5_000)
            }
        }
        throw last!!
    }

    @Before
    fun needKey() {
        assumeTrue("no live key in this environment", key.isNotBlank())
        assertEquals(
            "$base/models/$model:streamGenerateContent?alt=sse",
            CloudRequest.url(cfg(), stream = true),
        )
    }

    @Test
    fun authenticationAndNonStreamingReply() {
        val said = retrying { brain().generate(ask("Reply with exactly: hello")) }
        assertTrue("empty reply", said.isNotBlank())
        assertTrue(said, said.contains("hello", ignoreCase = true))
    }

    @Test
    fun streamingDeltasAddUpToTheWholeReply() {
        val pieces = ArrayList<String>()
        val whole = retrying { brain().generateStream(ask("Count from 1 to 5, comma separated.")) { pieces += it } }
        assertTrue("no deltas arrived", pieces.isNotEmpty())
        assertTrue(whole, whole.contains("5"))
        assertEquals(pieces.joinToString(""), whole)
    }

    @Test
    fun conversationHistoryIsSentAndUsed() {
        val said = retrying {
            brain().generate(
                ask(
                    "What is my name? One word only.",
                    listOf(Turn("My name is Shubham.", "Noted.")),
                ),
            )
        }
        assertTrue(said, said.contains("Shubham", ignoreCase = true))
    }

    @Test
    fun anUnknownModelReportsTheAddressProblemInPlainWords() {
        val e = try {
            brain("gemini-3.8-flash-no-such-model").generate(ask("hi"))
            null
        } catch (thrown: IOException) {
            thrown
        }
        assertTrue("expected a 404, got none", e != null)
        val message = e!!.message.orEmpty()
        assertTrue(message, message.contains("Cloud error 404"))
        assertTrue(message, message.contains("check the base URL"))
    }

    @Test
    fun controlModeSendsTheToolMenuAndChatDoesNot() {
        val cfg = cfg()
        val tools = listOf(
            ToolSpec("flashlight", """{"on":"boolean"}""", "turn the torch on or off"),
        )
        val control = LlmRequest("rules", emptyList(), "torch on", Mode.Control)
        val chat = control.copy(mode = Mode.Chat)
        val controlBody = CloudRequest.body(cfg, control, stream = false, tools = tools)
        assertTrue(controlBody.contains("\"functionDeclarations\""))
        val chatBody = CloudRequest.body(cfg, chat, stream = false, tools = emptyList())
        assertTrue(!chatBody.contains("functionDeclarations"))
    }
}
