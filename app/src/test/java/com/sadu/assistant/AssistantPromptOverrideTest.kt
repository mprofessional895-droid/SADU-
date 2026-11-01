package com.sadu.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sadu.assistant.brain.Brain
import com.sadu.assistant.brain.LlmRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A hand-written prompt replaces the shipped one completely; off leaves it untouched. */
@RunWith(RobolectricTestRunner::class)
class AssistantPromptOverrideTest {

    private class Recorder(private val reply: String) : Brain {
        override val isReady = true
        override val name: String get() = "recorder"
        var last: LlmRequest? = null

        override fun generate(request: LlmRequest): String {
            last = request
            return reply
        }
    }

    /** A message no rule catches, so the model is really asked. */
    private fun answer(said: String?): Recorder {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val prefs = Prefs(ctx)
        prefs.mode = Mode.Control
        val memory = Memory(ctx)
        memory.clear()
        memory.add("The user's name is Shubham")
        val actions = Actions(ctx, memory, prefs, { null })
        val brain = Recorder("ok")
        val assistant = Assistant(
            brain,
            actions,
            memory,
            Tools(actions, memory) { prefs.mode },
            { "auto" },
            { Mode.Control },
            { ContextBudget.Default },
            replyLength = { ReplyLength.Short },
            webAnswers = { false },
            internet = { false },
            fetch = { "" },
            cloudWanted = { false },
            brainProblem = { null },
            promptOverride = { said },
        )
        assistant.respond("kuch acha batao yaar")
        return brain
    }

    @Test
    fun aHandWrittenPromptReplacesEverythingTheAppWouldHaveSent() {
        assertEquals("MY OWN PROMPT", answer("MY OWN PROMPT").last?.system)
    }

    @Test
    fun noOverrideLeavesTheShippedPromptInPlace() {
        val system = answer(null).last?.system.orEmpty()
        assertTrue(system.contains("You are Sadu"))
        assertTrue(system.contains("Your tools:"))
        assertTrue(system.contains("The user's name is Shubham"))
        assertFalse(system.contains("MY OWN PROMPT"))
    }
}
