package com.sadu.assistant.brain

import com.sadu.assistant.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The choice between the on-device model and the cloud one. The rules that matter: the
 * cloud answers when it is switched on and set up, and a cloud that fails never leaves
 * the user without an answer.
 */
class BrainRouterTest {

    private class Stub(
        override val name: String,
        private val reply: String = "reply",
        override val isReady: Boolean = true,
        private val fails: Boolean = false,
    ) : Brain {
        var asked = 0
            private set

        override fun generate(request: LlmRequest): String {
            asked++
            if (fails) throw java.io.IOException("no route to host")
            return reply
        }
    }

    private fun request() = LlmRequest("system", emptyList(), "hello", Mode.Chat)

    private val local = Stub("On-device Gemma", reply = "local")
    private val cloud = Stub("Cloud (gpt-4o-mini)", reply = "cloud")

    @Test
    fun theCloudAnswersWhenItIsOnAndSetUp() {
        val router = BrainRouter(local, cloud) { true }
        assertEquals("cloud", router.generate(request()))
        assertEquals("Cloud (gpt-4o-mini)", router.lastUsed)
        assertNull(router.lastFallback)
        assertEquals(0, local.asked)
    }

    @Test
    fun theDeviceAnswersWhileTheCloudIsOff() {
        val router = BrainRouter(local, cloud) { false }
        assertEquals("local", router.generate(request()))
        assertEquals("On-device Gemma", router.lastUsed)
        assertEquals(0, cloud.asked)
    }

    @Test
    fun aCloudThatFailsIsExplainedAndAnsweredOnTheDevice() {
        val broken = Stub("Cloud (gpt-4o-mini)", fails = true)
        val router = BrainRouter(local, broken) { true }

        assertEquals("local", router.generate(request()))
        assertEquals("On-device Gemma", router.lastUsed)
        assertEquals("no route to host", router.lastFallback)
    }

    @Test
    fun aCloudThatFailsThrowsExceptionWhenNoLocalModelIsReady() {
        val brokenCloud = Stub("Cloud (gpt-4o-mini)", fails = true)
        val unreadyLocal = Stub("On-device Gemma", isReady = false)
        val router = BrainRouter(unreadyLocal, brokenCloud) { true }

        try {
            router.generate(request())
            org.junit.Assert.fail("Should have thrown IOException")
        } catch (e: java.io.IOException) {
            assertEquals("no route to host", e.message)
        }
    }

    @Test
    fun theReasonIsClearedWhenTheCloudWorksAgain() {
        val sometimes = Stub("Cloud (gpt-4o-mini)")
        val router = BrainRouter(local, sometimes) { true }
        router.generate(request())
        assertNull(router.lastFallback)
        assertTrue(router.isReady)
        assertTrue(router.name.contains("Cloud"))
    }

    @Test
    fun switchingTheCloudOnIsAllTheConsentItNeeds() {
        val set = CloudConfig(provider = CloudProvider.Compatible, baseUrl = "https://api.openai.com/v1", model = "gpt-4o-mini")
        assertTrue("on and set up is enough", set.shouldAnswer(true))
        assertTrue("the internet switch is about the phone tools, not this", set.shouldAnswer(true))
        assertTrue(!set.shouldAnswer(false))
        assertTrue(!CloudConfig().shouldAnswer(true))
    }
}
