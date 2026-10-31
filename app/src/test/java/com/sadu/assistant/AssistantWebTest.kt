package com.sadu.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadu.assistant.brain.Brain
import com.sadu.assistant.brain.BrainRouter
import com.sadu.assistant.brain.LlmRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Search about 24byme" has to be looked up in the background and answered from what came
 * back - not thrown at the browser. The page fetch is injected, so this runs offline and
 * still goes through the real Assistant.
 */
@RunWith(AndroidJUnit4::class)
class AssistantWebTest {

    private lateinit var ctx: Context
    private lateinit var prefs: Prefs
    private lateinit var actions: Actions

    /** A brain that writes down what it was asked and always gives the same answer. */
    private class FakeBrain(
        private val reply: String,
        override val isReady: Boolean = true,
        private val fails: Boolean = false,
        var truncated: Boolean = false,
    ) : Brain {
        override val name: String get() = "fake"
        override val lastReplyTruncated: Boolean get() = truncated
        var last: LlmRequest? = null
        var asked = 0
            private set

        override fun generate(request: LlmRequest): String {
            asked++
            last = request
            if (fails) throw IllegalStateException("model exploded")
            return reply
        }
    }

    /** A brain that answers a different thing each time it is asked, e.g. "SEARCH: ..." then the answer. */
    private class SequenceBrain(vararg replies: String, override val isReady: Boolean = true) : Brain {
        override val name: String get() = "sequenced"
        val replies: List<String> = replies.toList()
        var asked = 0
            private set
        var last: LlmRequest? = null

        override fun generate(request: LlmRequest): String {
            last = request
            val reply = replies[asked.coerceAtMost(replies.lastIndex)]
            asked++
            return reply
        }
    }

    private val pages = mutableListOf<String>()

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        prefs = Prefs(ctx)
        prefs.webAnswers = true
        prefs.mode = Mode.Chat
        actions = Actions(ctx, Memory(ctx), prefs, { null })
    }

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/web/$name")?.bufferedReader()?.use { it.readText() }
            ?: error("missing fixture $name")

    /** Serves the real fixtures, and remembers which pages were asked for. */
    private fun reader(wiki: Boolean = true): (String) -> String = { url ->
        pages += url
        when {
            url.contains("wikipedia") -> if (wiki) fixture("wiki.json") else "<html></html>"
            else -> "<html></html>"
        }
    }

    private fun assistant(
        brain: Brain,
        fetch: (String) -> String = reader(),
        webOn: Boolean = true,
        internetOn: Boolean = true,
    ) = Assistant(
        brain,
        actions,
        Memory(ctx),
        Tools(actions, Memory(ctx), { prefs.mode }),
        { "auto" },
        { prefs.mode },
        { ContextBudget.Default },
        replyLength = { ReplyLength.Short },
        webAnswers = { webOn },
        internet = { internetOn },
        fetch = fetch,
    )

    @Test
    fun aSearchIsAnsweredFromTheWeb() {
        val brain = FakeBrain("Python was created by Guido van Rossum, and that comes from a web search.")
        val answer = assistant(brain).respond("search about python programming")

        assertTrue(answer.startsWith("Python was created"))
        assertTrue("the page was really fetched", pages.any { it.contains("wikipedia") })
        val asked = brain.last?.user.orEmpty()
        assertTrue("the results went to the model", asked.contains("[web results for"))
        assertTrue(asked, asked.contains("Python (programming language)"))
        assertTrue("the question is still in there", asked.contains("python programming"))
    }

    /**
     * Search is not a cloud-only feature. Whichever brain is answering - the cloud model or
     * the one on the phone - the results go into its prompt and it writes the answer.
     */
    @Test
    fun theOnDeviceModelAlsoAnswersFromTheResults() {
        val cloud = FakeBrain("cloud answer", fails = true)
        val local = FakeBrain("The on-device model wrote this from the search results.")
        val router = BrainRouter(local, cloud, cloudWanted = { false })

        val answer = assistant(router).respond("search about python programming")

        assertEquals("The on-device model wrote this from the search results.", answer)
        assertEquals("the cloud was not used", 0, cloud.asked)
        val asked = local.last?.user.orEmpty()
        assertTrue("the results reach the on-device prompt", asked.contains("[web results for"))
        assertTrue(asked, asked.contains("Python (programming language)"))
    }

    /** And the on-device model may ask for a look-up itself, the same as the cloud one. */
    @Test
    fun theOnDeviceModelCanAskForTheSearchOnItsOwn() {
        val cloud = FakeBrain("cloud answer", fails = true)
        val local = SequenceBrain("SEARCH: python programming", "Guido van Rossum, according to what I found.")
        val router = BrainRouter(local, cloud, cloudWanted = { false })

        val answer = assistant(router).respond("who made python")

        assertTrue(answer, answer.startsWith("Guido van Rossum"))
        assertEquals("asked once, then again with the results", 2, local.asked)
        assertTrue(local.last?.user.orEmpty().contains("[web results for"))
    }

    @Test
    fun theSearchItselfIsWhatIsRemembered() {
        val brain = FakeBrain("An answer from the web.")
        val sut = assistant(brain)
        sut.respond("search about python programming")

        val turns = sut.remembered()
        assertEquals(1, turns.size)
        assertEquals("search about python programming", turns[0].user)
        assertEquals("An answer from the web.", turns[0].reply)
    }

    @Test
    fun withoutAModelTheResultsAreReadOut() {
        val brain = FakeBrain("ignored", isReady = false)
        val answer = assistant(brain).respond("search about python programming")

        assertTrue(answer.startsWith("Here is what I found about \"python programming\""))
        assertTrue(answer.contains("Python (programming language)"))
        assertTrue(answer.contains("Sources:"))
        assertNull("a brain that is not ready is not asked", brain.last)
    }

    @Test
    fun aModelFailureStillLeavesTheResults() {
        val brain = FakeBrain("", fails = true)
        val answer = assistant(brain).respond("search about python programming")
        assertTrue(answer.startsWith("Here is what I found about"))
    }

    @Test
    fun ordinaryConversationIsUntouched() {
        val brain = FakeBrain("Hello!")
        assertEquals("Hello!", assistant(brain).respond("how are you today"))
        assertTrue("nothing was fetched", pages.isEmpty())
        assertTrue(brain.last?.user?.contains("[web results") != true)
    }

    @Test
    fun aModelAnswerIsMarkedAsAModelAnswer() {
        val brain = FakeBrain("A normal reply.")
        val sut = assistant(brain)
        sut.respond("how are you today")
        assertTrue("the app must be able to say the model answered", sut.answeredWithModel)
    }

    @Test
    fun aBuiltinRuleIsNotAModelAnswer() {
        val brain = FakeBrain("A normal reply.")
        val sut = assistant(brain)
        sut.respond("what is the time")
        assertFalse("a rule answered, so no model was used", sut.answeredWithModel)
        assertEquals(0, brain.asked)
    }

    @Test
    fun aWebAnswerIsAModelAnswer() {
        val sut = assistant(FakeBrain("Answered from the web results."))
        sut.respond("search about python programming")
        assertTrue(sut.answeredWithModel)
    }

    @Test
    fun nothingFoundIsSaidPlainly() {
        val brain = FakeBrain("The model should not be asked.")
        val answer = assistant(brain, reader(wiki = false)).respond("search about nothing at all")
        assertNull("the model is not asked about an empty search", brain.last)
        assertTrue("the user is told what happened", answer.contains("nothing useful"))
        assertFalse("an empty search must not be dressed up as an answer", answer.contains("Here is what I found"))
    }

    @Test
    fun aRobotCheckIsExplained() {
        val page = "<html><body><div class=\"anomaly-modal\">Unfortunately, bots use too.</div></body></html>"
        val brain = FakeBrain("The model should not be asked.")
        val answer = assistant(brain, fetch = { if (it.contains("bing")) "" else page })
            .respond("search about 24byme")
        assertTrue(answer, answer.contains("robot check"))
        assertNull(brain.last)
    }

    @Test
    fun withTheInternetOffNothingIsFetched() {
        val brain = FakeBrain("The model should not be asked.")
        val answer = assistant(brain, internetOn = false).respond("search about python programming")
        assertTrue(answer, answer.contains("Internet is off"))
        assertTrue("no page is fetched with the switch off", pages.isEmpty())
        assertNull(brain.last)
    }

    @Test
    fun theModelCanAskForASearchInChatMode() {
        // Chat mode's prompt has no tool protocol: one line, "SEARCH: ...", is the whole of it.
        val brain = SequenceBrain("SEARCH: python programming", "Python was created by Guido van Rossum.")
        val sut = assistant(brain)
        val answer = sut.respond("who created python, and when?")

        assertEquals("Python was created by Guido van Rossum.", answer)
        assertEquals("the model is asked once more with the results", 2, brain.asked)
        assertTrue("the results were handed over", brain.last?.user.orEmpty().contains("[web results for"))
        assertTrue("the question is asked", brain.replies[0].contains("SEARCH:"))
    }

    @Test
    fun anOrdinaryReplyIsNotASearchRequest() {
        val brain = FakeBrain("I searched my memory for that one.")
        val answer = assistant(brain).respond("what do you know about python?")
        assertEquals("I searched my memory for that one.", answer)
        assertTrue("a normal answer stands", pages.isEmpty())
    }

    @Test
    fun aReplyCutOffAtTheLengthLimitIsReported() {
        val brain = FakeBrain("Once upon a time, and then the model ran out of")
        val assistant = assistant(brain)

        brain.truncated = true
        assistant.respond("tell me a long story")
        assertTrue("the app must know it was cut off", assistant.lastReplyTruncated)

        brain.truncated = false
        assistant.respond("and now something short")
        assertFalse("a finished reply clears it", assistant.lastReplyTruncated)
    }
}
