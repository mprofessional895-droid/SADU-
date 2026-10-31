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
 * What may run, and where that is decided.
 *
 * Control mode is the permission: in it the whole toolbox runs, and in Chat mode the phone
 * tools are refused by [ModePolicy]. There is no second switch, so these tests pin down that
 * Control mode acts and Chat mode does not, and that every model is offered the same tools.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SafeControlTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs
    private lateinit var memory: Memory
    private lateinit var actions: Actions

    private fun toolsNow(mode: Mode = Mode.Control): Tools = Tools(actions, memory, { mode })

    @Before
    fun setUp() {
        prefs = Prefs(ctx)
        prefs.internetEnabled = false
        memory = Memory(ctx)
        memory.clear()
        actions = Actions(ctx, memory, prefs) { null }
    }

    // ------------------------------------------------------------------ control mode acts

    @Test
    fun controlModeMaySendMessageCallAndInstall() {
        val tools = toolsNow()
        val answers = listOf(
            tools.run("send_sms", mapOf("contact" to "Mom", "message" to "I'm late")).display,
            tools.run("call", mapOf("contact" to "Ravi")).display,
            tools.run("install_app", mapOf("app" to "WhatsApp")).display,
        )
        for (answer in answers) {
            assertFalse(answer, answer.contains("held back"))
            assertFalse(answer, answer.contains("Risky"))
        }
    }

    @Test
    fun controlModeMayTapAndType() {
        val tap = toolsNow().run("screen", mapOf("action" to "tap", "text" to "Send")).display
        assertFalse(tap, tap.contains("held back"))
        assertTrue(tap, tap.contains("Screen control is off"))
    }

    @Test
    fun forgettingDetailsRunsInControlMode() {
        memory.add("I like tea")
        val out = toolsNow().run("forget", emptyMap()).display
        assertFalse(out, out.contains("held back"))
        assertTrue("the details are gone", memory.facts().isEmpty())
    }

    @Test
    fun theTypedCommandReachesTheSameTools() {
        assertFalse(actions.tryHandle("forget everything")!!.contains("held back"))
        assertFalse(actions.tryHandle("send a message to Ravi saying hello")!!.contains("held back"))
        assertFalse(actions.tryHandle("install the app WhatsApp")!!.contains("held back"))
    }

    // ------------------------------------------------------------------ chat mode refuses

    @Test
    fun chatModeRefusesPhoneTools() {
        val chat = toolsNow(Mode.Chat)
        val call = chat.run("call", mapOf("contact" to "Ravi")).display
        assertTrue(call, call.contains("Chat mode"))
        val torch = chat.run("flashlight", mapOf("on" to "true")).display
        assertTrue(torch, torch.contains("Chat mode"))
    }

    // ------------------------------------------------------------------ the menu

    @Test
    fun theModelSeesEveryTool() {
        val menu = toolsNow().menu()
        for (name in listOf("send_sms", "call", "install_app", "screen", "media")) {
            assertTrue("$name is offered", menu.contains("- $name("))
        }
    }

    @Test
    fun bothModelsGetTheSameToolbox() {
        assertEquals(toolsNow().menu(), toolsNow().menu())
    }
}
