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
 * The whole point of the tiny Chat prompt: the app, not the model, knows that Chat mode
 * cannot touch the phone - and it still refuses the same request Control mode carries out.
 */
@RunWith(AndroidJUnit4::class)
class AssistantPhoneGuardTest {

    private lateinit var ctx: Context
    private lateinit var prefs: Prefs

    private class FakeBrain(private val reply: String) : Brain {
        override val isReady: Boolean = true
        override val name: String get() = "fake"
        var asked = 0
            private set

        override fun generate(request: LlmRequest): String {
            asked++
            return reply
        }
    }

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        prefs = Prefs(ctx)
        prefs.mode = Mode.Chat
    }

    private fun assistant(brain: Brain, mode: Mode): Assistant {
        prefs.mode = mode
        val actions = Actions(ctx, Memory(ctx), prefs, { null })
        return Assistant(
            brain = brain,
            actions = actions,
            memory = Memory(ctx),
            tools = Tools(actions, Memory(ctx), { prefs.mode }),
            language = { "auto" },
            mode = { prefs.mode },
            contextTokens = { ContextBudget.Default },
            replyLength = { ReplyLength.Short },
        )
    }

    @Test
    fun chatModeAnswersAPhoneRequestItselfWithoutBotheringTheModel() {
        val brain = FakeBrain("Sure, I've sent it!")
        val answer = assistant(brain, Mode.Chat).respond("text mom I'm on my way")

        assertEquals(ChatPolicy.NOT_HERE, answer)
        assertEquals("the model must not even be asked", 0, brain.asked)
        assertFalse("and nothing is claimed", answer.contains("sent"))
    }

    @Test
    fun controlModeStillAsksTheModel() {
        val brain = FakeBrain("Alright, on its way.")
        val answer = assistant(brain, Mode.Control).respond("text mom I'm on my way")

        assertEquals("Alright, on its way.", answer)
        assertEquals(1, brain.asked)
    }

    @Test
    fun aClaimedPhoneActionIsFlaggedInChatMode() {
        val brain = FakeBrain("Done! I've sent the message to mom.")
        val sut = assistant(brain, Mode.Chat)
        sut.respond("how are you today")

        assertTrue("the claim has to be caught", sut.lastActionClaim)
    }

    @Test
    fun anHonestAnswerIsNotFlagged() {
        val brain = FakeBrain("I'm well, thanks - how are you?")
        val sut = assistant(brain, Mode.Chat)
        sut.respond("how are you today")

        assertFalse(sut.lastActionClaim)
    }

    @Test
    fun controlModeIsNotFlaggedEvenWhenItSaysIt() {
        val brain = FakeBrain("Done! I've sent the message to mom.")
        val sut = assistant(brain, Mode.Control)
        sut.respond("how are you today")

        assertFalse("Control mode really can send it", sut.lastActionClaim)
    }

    @Test
    fun anOrdinaryQuestionStillReachesTheModelInChatMode() {
        val brain = FakeBrain("Hello!")
        val sut = assistant(brain, Mode.Chat)
        assertEquals("Hello!", sut.respond("how are you today"))
        assertEquals(1, brain.asked)
    }
}
