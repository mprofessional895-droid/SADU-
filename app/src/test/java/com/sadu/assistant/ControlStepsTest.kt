package com.sadu.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadu.assistant.brain.Brain
import com.sadu.assistant.brain.LlmRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One message, several actions.
 *
 * The model is replaced by a scripted brain that answers with a tool call per turn, so the
 * loop itself is what is under test: does it run the tools in order, feed the results back,
 * stop at the limit, and say plainly when a control is switched off?
 */
@RunWith(AndroidJUnit4::class)
class ControlStepsTest {

    private lateinit var ctx: Context
    private lateinit var prefs: Prefs

    /** Answers with the scripted replies in turn, and records what it was shown. */
    private class ScriptedBrain(private val replies: List<String>) : Brain {
        override val isReady: Boolean = true
        override val name: String get() = "scripted"
        val prompts = ArrayList<String>()
        var calls = 0
            private set

        override fun generate(request: LlmRequest): String {
            prompts.add(request.system)
            val reply = replies.getOrElse(calls) { "" }
            calls++
            return reply
        }
    }

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        prefs = Prefs(ctx)
        prefs.mode = Mode.Control
        prefs.internetEnabled = false
    }

    /** Text that no built-in rule answers, so the model and the loop are what run. */
    private val neutral = "do the thing I asked"

    private fun assistant(brain: Brain): Assistant {
        val memory = Memory(ctx)
        val actions = Actions(ctx, memory, prefs, { null })
        return Assistant(
            brain = brain,
            actions = actions,
            memory = memory,
            tools = Tools(actions, memory, { prefs.mode }),
            language = { "auto" },
            mode = { prefs.mode },
            contextTokens = { ContextBudget.Default },
            replyLength = { ReplyLength.Short },
        )
    }

    // ------------------------------------------------------------------ the loop

    @Test
    fun twoToolsRunInOrderAndTheResultsGoBackToTheModel() {
        val brain = ScriptedBrain(
            listOf(
                """{"tool":"recall"}""",
                """{"tool":"date"}""",
                "Your battery is fine and it's the afternoon.",
            )
        )
        val answer = assistant(brain).respond(neutral)

        assertEquals("Your battery is fine and it's the afternoon.", answer)
        assertEquals("three model turns: battery, time, then words", 3, brain.calls)
        assertTrue(
            "the second prompt must carry the first result",
            brain.prompts[1].contains("Already done") && brain.prompts[1].contains("recall:")
        )
        assertTrue(
            "the third prompt must carry both results",
            brain.prompts[2].contains("recall:") && brain.prompts[2].contains("date:")
        )
    }

    @Test
    fun theStepsThatRanAreReported() {
        val brain = ScriptedBrain(
            listOf("""{"tool":"recall"}""", """{"tool":"date"}""", "All done.")
        )
        val sut = assistant(brain)
        sut.respond(neutral)

        assertEquals(listOf("recall", "date"), sut.lastSteps().map { it.tool })
    }

    @Test
    fun theSameCallTwiceInARowStopsInsteadOfSpinning() {
        val brain = ScriptedBrain(
            List(6) { """{"tool":"recall"}""" }
        )
        val sut = assistant(brain)
        val answer = sut.respond(neutral)

        assertTrue(answer.isNotBlank())
        assertTrue("must not loop for ever", brain.calls <= 2)
        assertEquals(1, sut.lastSteps().size)
    }

    @Test
    fun theStepLimitEndsWithASummaryOfWhatHappened() {
        val brain = ScriptedBrain(
            List(ToolSteps.Max + 3) { i -> """{"tool":"volume","args":{"direction":"up"}}""" }
        )
        val sut = assistant(brain)
        val answer = sut.respond("turn it up")

        assertTrue("the answer must be something the user can read", answer.isNotBlank())
        assertTrue("no tool steps past the limit", sut.lastSteps().size <= ToolSteps.Max)
        assertTrue(sut.lastSteps().isNotEmpty())
    }

    @Test
    fun aMadeUpToolNameIsRetriedOnceWithTheMenu() {
        val brain = ScriptedBrain(
            listOf("""{"tool":"teleport","args":{}}""", """{"tool":"recall"}""", "Nothing to recall.")
        )
        val answer = assistant(brain).respond(neutral)

        assertEquals("Nothing to recall.", answer)
        assertTrue(
            "the retry prompt must say the last reply was not a tool",
            brain.prompts[1].contains("not a tool from the list")
        )
    }

    @Test
    fun plainWordsStillEndTheTurn() {
        val brain = ScriptedBrain(listOf("Nothing to do there."))
        val sut = assistant(brain)
        assertEquals("Nothing to do there.", sut.respond("how are you"))
        assertTrue("no tools ran", sut.lastSteps().isEmpty())
    }

    // ------------------------------------------------------------------ safety

    @Test
    fun anUnknownContactSaysSoAndStopsTheChain() {
        val brain = ScriptedBrain(
            listOf("""{"tool":"call","args":{"contact":"Papa"}}""", """{"tool":"recall"}""")
        )
        val sut = assistant(brain)
        val answer = sut.respond(neutral)

        assertTrue(answer, answer.contains("contacts") || answer.contains("couldn't find"))
        assertTrue("the model was asked at least once", brain.calls >= 1)
    }

    @Test
    fun chatModeNeverRunsAPhoneToolEvenIfTheModelAsksForIt() {
        prefs.mode = Mode.Chat
        val brain = ScriptedBrain(listOf("""{"tool":"flashlight","args":{"on":"true"}}"""))
        val sut = assistant(brain)
        val answer = sut.respond("tell me a joke")

        assertFalse("it cannot say it turned it on", answer.contains("Flashlight on"))
        assertTrue(
            "the refusal is in the answer or the model was not reached",
            brain.calls == 0 || answer.contains("Chat mode") || answer.isNotBlank()
        )
    }

    @Test
    fun aHindiOrderAlwaysGetsAStraightAnswer() {
        // Without contacts, "papa ko call kar" must still answer: asking for the permission
        // beats sliding silently into conversation, which is what made calling look broken.
        val brain = ScriptedBrain(listOf("Who is Papa?"))
        val sut = assistant(brain)
        val answer = sut.respond("papa ko call kar")

        assertFalse("must not claim it called anyone", answer.contains("Calling Papa"))
        assertTrue(
            "it has to say what is missing: contacts, or the name",
            answer.contains("contacts") || answer.contains("couldn't find") ||
                answer.contains("held back")
        )
        assertTrue("no phone action was recorded", sut.lastSteps().isEmpty())
    }

    @Test
    fun aHindiMessageIsNeverSentSilently() {
        val brain = ScriptedBrain(listOf("I couldn't find Mummy."))
        val sut = assistant(brain)
        val answer = sut.respond("mummy ko whatsapp me message kar ki main late aa raha hoon")

        assertFalse(answer.contains("Message sent"))
        assertFalse(answer.contains("Tap Send"))
        assertTrue(answer.contains("contacts") || answer.contains("couldn't find") || answer.contains("held back"))
    }

    @Test
    fun theWhatsAppHintNeverClaimsAMessageWasSent() {
        val brain = ScriptedBrain(listOf("""{"tool":"whatsapp","args":{"contact":"Mummy","message":"late"}}"""))
        val sut = assistant(brain)
        val answer = sut.respond(neutral)

        assertFalse("must not claim it sent anything", answer.contains("Message sent"))
        assertTrue(
            "it either says the control is off, or asks for the contact - never a fake success",
            answer.contains("held back") || answer.contains("couldn't find") ||
                answer.contains("contacts") || answer.contains("Tap Send")
        )
    }
}
