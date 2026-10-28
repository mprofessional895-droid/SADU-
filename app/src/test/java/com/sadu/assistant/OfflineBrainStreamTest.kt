package com.sadu.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadu.assistant.brain.LlmRequest
import com.sadu.assistant.brain.OfflineBrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The on-device model now streams, so a reply lands on screen as it is written instead of
 * after it is finished - and Chat mode asks the engine for the sampler the user chose.
 */
@RunWith(AndroidJUnit4::class)
class OfflineBrainStreamTest {

    /** Stands in for MediaPipe: hands out a fixed set of pieces. */
    private class FakeEngine(ctx: Context) : LlmEngine(ctx) {
        val samplings = mutableListOf<Sampling>()

        /** How long each reply was allowed to be. */
        val tokenCaps = mutableListOf<Int>()
        var prompt = ""
        var cancelled = false
            private set
        var pieces = listOf("Hel", "lo ", "there")

        override fun generateStream(
            prompt: String,
            sampling: Sampling,
            maxNewTokens: Int,
            onDelta: (String) -> Unit,
        ): String {
            this.prompt = prompt
            samplings += sampling
            tokenCaps += maxNewTokens
            pieces.forEach(onDelta)
            return pieces.joinToString("")
        }

        override fun cancel(): Boolean {
            cancelled = true
            return true
        }
    }

    private lateinit var engine: FakeEngine

    @Before
    fun setUp() {
        engine = FakeEngine(ApplicationProvider.getApplicationContext())
    }

    private fun request(mode: Mode) =
        LlmRequest(system = "INSTRUCTIONS", turns = emptyList(), user = "hello", mode = mode)

    @Test
    fun everyPieceIsHandedOnAsItArrives() {
        val seen = mutableListOf<String>()
        val brain = OfflineBrain(engine)
        val whole = brain.generateStream(request(Mode.Chat)) { seen += it }

        assertEquals(listOf("Hel", "lo ", "there"), seen)
        assertEquals("Hello there", whole)
    }

    @Test
    fun thePromptStillUsesGemmasOwnTemplate() {
        OfflineBrain(engine).generateStream(request(Mode.Chat)) { }
        assertTrue(engine.prompt.startsWith("<start_of_turn>user\nINSTRUCTIONS"))
        assertTrue(engine.prompt.endsWith("<start_of_turn>model\n"))
    }

    @Test
    fun theReplyLengthReachesTheEngine() {
        val brain = OfflineBrain(engine, length = { ReplyLength.Short })
        brain.generateStream(request(Mode.Chat)) { }
        assertEquals(ReplyLength.Short.tokens, engine.tokenCaps.last())
    }

    @Test
    fun chatModeUsesTheTemperatureTheUserChose() {
        val brain = OfflineBrain(engine, sampling = { mode ->
            if (mode == Mode.Chat) Sampling.chat(0.9f) else Sampling.Command
        })
        brain.generateStream(request(Mode.Chat)) { }
        assertEquals(0.9f, engine.samplings.last().temperature, 0.001f)

        brain.generateStream(request(Mode.Control)) { }
        assertEquals(Sampling.Command.temperature, engine.samplings.last().temperature, 0.001f)
    }

    @Test
    fun stoppingReachesTheEngine() {
        val brain = OfflineBrain(engine)
        assertTrue(brain.cancel())
        assertTrue(engine.cancelled)
    }
}
