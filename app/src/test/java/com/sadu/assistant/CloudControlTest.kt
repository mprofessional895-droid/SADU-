package com.sadu.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadu.assistant.brain.Brain
import com.sadu.assistant.brain.BrainRouter
import com.sadu.assistant.brain.LlmRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The cloud model drives Control mode too.
 *
 * Nothing in the loop may care which brain answered: the same menu, the same tool call, the
 * same phone action. These tests put a cloud brain in front of the router and check that the
 * on-device brain stays idle.
 */
@RunWith(AndroidJUnit4::class)
class CloudControlTest {

    private lateinit var ctx: Context
    private lateinit var prefs: Prefs

    private class FakeBrain(
        override val name: String,
        private val replies: List<String>,
        private val ready: Boolean = true,
    ) : Brain {
        override val isReady: Boolean get() = ready
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
        prefs.riskyControls = false
        prefs.internetEnabled = false
    }

    private fun assistant(brain: Brain): Assistant {
        val memory = Memory(ctx)
        val actions = Actions(ctx, memory, prefs, { null })
        return Assistant(
            brain = brain,
            actions = actions,
            memory = memory,
            tools = Tools(actions, memory, { prefs.mode }, { prefs.riskyControls }),
            language = { "auto" },
            mode = { prefs.mode },
            contextTokens = { ContextBudget.Default },
            replyLength = { ReplyLength.Short },
            riskyAllowed = { prefs.riskyControls },
            preferCloudForActions = { prefs.preferCloudForActions },
        )
    }

    @Test
    fun theCloudModelGetsTheToolMenuInControlMode() {
        val cloud = FakeBrain("cloud", listOf("All done."))
        val local = FakeBrain("local", listOf("local answer"))
        val router = BrainRouter(local, cloud, cloudWanted = { true })

        assistant(router).respond("do the thing I asked")

        assertEquals("the cloud answers", 1, cloud.calls)
        assertEquals("the phone model stays idle", 0, local.calls)
        val menu = cloud.prompts.first()
        assertTrue("it is shown the phone tools", menu.contains("\"tool\""))
        assertTrue("and told what they do", menu.contains("torch") || menu.contains("volume"))
    }

    @Test
    fun aToolCallFromTheCloudModelActuallyRuns() {
        // First turn: the cloud asks for the clock. Second: it says what it found.
        val cloud = FakeBrain("cloud", listOf("""{"tool":"date"}""", "It's that time."))
        val local = FakeBrain("local", listOf("local answer"))
        val router = BrainRouter(local, cloud, cloudWanted = { true })

        val sut = assistant(router)
        val answer = sut.respond("do the thing I asked")

        assertTrue(answer, answer.contains("time") || answer.contains("It's"))
        assertTrue("a step was recorded", sut.lastSteps().isNotEmpty())
        assertEquals("the clock tool ran", "date", sut.lastSteps().first().tool)
        assertEquals(0, local.calls)
    }

    @Test
    fun whenTheCloudFailsThePhoneModelTakesOver() {
        val cloud = FakeBrain("cloud", listOf("unused"), ready = false)
        val local = FakeBrain("local", listOf("answered locally"))
        val router = BrainRouter(local, cloud, cloudWanted = { true })

        val answer = assistant(router).respond("do the thing I asked")

        assertEquals("answered locally", answer)
        assertEquals(0, cloud.calls)
    }

    @Test
    fun withCloudSwitchedOffThePhoneModelAnswers() {
        val cloud = FakeBrain("cloud", listOf("cloud answer"))
        val local = FakeBrain("local", listOf("local answer"))
        val router = BrainRouter(local, cloud, cloudWanted = { false })

        val answer = assistant(router).respond("do the thing I asked")

        assertEquals("local answer", answer)
        assertEquals("the cloud is not asked at all", 0, cloud.calls)
    }

    @Test
    fun preferCloudForActionsBypassesLocalRulesInControlMode() {
        prefs.preferCloudForActions = true
        val cloud = FakeBrain("cloud", listOf("cloud handled action"))
        val local = FakeBrain("local", listOf("local answer"))
        val router = BrainRouter(local, cloud, cloudWanted = { true })

        // "what time is it" is normally handled by local ruleActions
        val answer = assistant(router).respond("what time is it")

        assertEquals(1, cloud.calls)
        assertEquals("cloud handled action", answer)
    }

    @Test
    fun chatModeStillGetsNoPhoneToolsFromTheCloud() {
        prefs.mode = Mode.Chat
        val cloud = FakeBrain("cloud", listOf("just talking"))
        val router = BrainRouter(FakeBrain("local", listOf("x")), cloud, cloudWanted = { true })

        assistant(router).respond("hello there")

        val menu = cloud.prompts.first()
        assertFalse("no tool menu in Chat mode", menu.contains("\"tool\""))
    }
}
