package com.sadu.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Safe control, with the real Android pieces: the saved switch, the toolbox the model is
 * shown, and the rules that used to run before the model was ever asked.
 *
 * The point of these tests is that "off" means off. A tool call from the cloud model, a tool
 * call from the on-device model, and a plain typed command all have to end up in the same
 * place: the sentence that says the control waits for the user.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SafeControlTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs
    private lateinit var memory: Memory
    private lateinit var actions: Actions
    private lateinit var tools: Tools

    private fun toolsNow(): Tools = Tools(actions, memory, { Mode.Control }, { prefs.riskyControls })

    @Before
    fun setUp() {
        prefs = Prefs(ctx)
        // The two switches this test depends on, put back to their defaults.
        prefs.riskyControls = false
        prefs.internetEnabled = false
        memory = Memory(ctx)
        memory.clear()
        actions = Actions(ctx, memory, prefs) { null }
        tools = toolsNow()
    }

    // ------------------------------------------------------------------ held back

    @Test
    fun sendingAMessageIsHeldBack() {
        val out = tools.run("send_sms", mapOf("contact" to "Mom", "message" to "I'm late"))
        assertTrue(out.display, out.display.contains("held back"))
        assertTrue(out.display, out.display.contains("Sending a message"))
    }

    @Test
    fun callingInstallingAndTappingAreHeldBack() {
        assertTrue(tools.run("call", mapOf("contact" to "Ravi")).display.contains("Calling someone"))
        assertTrue(tools.run("install_app", mapOf("app" to "WhatsApp")).display.contains("Installing an app"))
        assertTrue(tools.run("screen", mapOf("action" to "tap", "text" to "Send")).display.contains("Tapping and typing"))
        assertTrue(tools.run("screen", mapOf("action" to "type", "text" to "hello")).display.contains("Tapping and typing"))
    }

    @Test
    fun forgettingYourDetailsIsHeldBack() {
        memory.add("I like tea")
        assertTrue(tools.run("forget", emptyMap()).display.contains("Forgetting what I know about you"))
        assertTrue("nothing was deleted", memory.facts().isNotEmpty())
        assertTrue(actions.tryHandle("forget everything")!!.contains("held back"))
        assertTrue("nothing was deleted", memory.facts().isNotEmpty())
    }

    @Test
    fun theTypedCommandIsHeldBackToo() {
        // These reach the rules before the model is ever asked, so they need the same gate.
        assertTrue(actions.tryHandle("send a message to Ravi saying hello")!!.contains("held back"))
        assertTrue(actions.tryHandle("install the app WhatsApp")!!.contains("held back"))
        assertTrue(actions.tryHandle("tap on Send")!!.contains("held back"))
        assertTrue(actions.tryHandle("type hello there")!!.contains("held back"))
    }

    // ------------------------------------------------------------------ still works

    @Test
    fun theSafeControlsStillRun() {
        assertTrue(actions.tryHandle("turn on the flashlight")!!.isNotEmpty())
        assertTrue(actions.tryHandle("mute the volume")!!.isNotEmpty())
        assertTrue(actions.tryHandle("what's my battery")!!.isNotEmpty())
        assertTrue(tools.run("battery", emptyMap()).display.isNotEmpty())
        assertTrue(tools.run("media", mapOf("action" to "pause")).display.isNotEmpty())
        assertTrue(actions.tryHandle("pause the music")!!.isNotEmpty())
    }

    @Test
    fun navigatingTheScreenIsNotHeldBack() {
        // With no accessibility service the answer is "screen control is off" - which proves
        // it got past the risk gate instead of being stopped by it.
        val back = tools.run("screen", mapOf("action" to "back"))
        assertFalse(back.display, back.display.contains("held back"))
        assertTrue(back.display, back.display.contains("Screen control is off"))
    }

    // ------------------------------------------------------------------ the switch

    @Test
    fun turningTheSwitchOnLetsTheGatedOnesThrough() {
        prefs.riskyControls = true
        val tools = toolsNow()
        val sms = tools.run("send_sms", mapOf("contact" to "Mom", "message" to "hi"))
        assertFalse(sms.display, sms.display.contains("held back"))
        val call = tools.run("call", mapOf("contact" to "Ravi"))
        assertFalse(call.display, call.display.contains("held back"))
        val tap = tools.run("screen", mapOf("action" to "tap", "text" to "Send"))
        assertFalse(tap.display, tap.display.contains("held back"))
    }

    @Test
    fun theSwitchIsRemembered() {
        prefs.riskyControls = true
        assertTrue(Prefs(ctx).riskyControls)
        prefs.riskyControls = false
        assertFalse("off is the default, and it is remembered", Prefs(ctx).riskyControls)
    }

    // ------------------------------------------------------------------ the menu

    @Test
    fun theModelIsNotShownToolsThatAreSwitchedOff() {
        val safe = tools.menu(risky = false)
        for (gone in listOf("send_sms", "call", "install_app")) {
            assertFalse("$gone is not offered", safe.contains("- $gone("))
        }
        assertTrue("no tap or type either", safe.contains("back|home|recents"))
        assertFalse(safe.contains("tap"))

        val full = tools.menu(risky = true)
        assertTrue(full.contains("send_sms"))
        assertTrue(full.contains("call"))
        assertTrue(full.contains("install_app"))
        assertTrue(full.contains("tap"))
    }

    @Test
    fun bothModelsGetTheSameToolbox() {
        // The menu is built from the toolbox, not from whichever brain answers, so the cloud
        // model and the on-device model are offered exactly the same controls.
        val once = tools.menu(risky = true)
        val again = toolsNow().menu(risky = true)
        assertEquals(once, again)
        assertTrue(once.contains("media"))
        assertTrue(once.contains("screen"))
    }
}
