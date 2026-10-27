package com.jarvis.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.assistant.brain.Brain
import com.jarvis.assistant.brain.LlmRequest
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
    ) : Brain {
        override val name: String get() = "fake"
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
    ) = Assistant(
        brain,
        actions,
        Memory(ctx),
        Tools(actions, Memory(ctx)) { prefs.mode },
        { "auto" },
        { prefs.mode },
        { ContextBudget.Default },
        { webOn },
        fetch,
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
    fun nothingFoundFallsBackToTheBrowser() {
        prefs.webAnswers = false
        val brain = FakeBrain("The model should not be asked.")
        val answer = assistant(brain, reader(wiki = false)).respond("search about nothing at all")
        assertNull("the model is not asked about an empty search", brain.last)
        assertFalse(answer.contains("Here is what I found"))
    }
}
