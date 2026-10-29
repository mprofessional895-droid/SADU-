package com.sadu.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line between what Sadu may do on its own and what waits for the user.
 *
 * These names decide whether a control runs at all, so they are pinned here: a typo that
 * quietly moved "send_sms" to the safe side would be a real-world bug, not a style problem.
 */
class ToolPolicyTest {

    @Test
    fun actingAsTheUserIsElevated() {
        for (tool in listOf("call", "phone", "dial", "send_sms", "sms", "text", "install_app", "forget")) {
            assertEquals("$tool acts as the user", ToolRisk.Elevated, ToolPolicy.risk(tool))
        }
    }

    @Test
    fun readableAndReversibleControlsStaySafe() {
        for (tool in listOf(
            "battery", "time", "date", "flashlight", "volume", "set_alarm", "open_app",
            "play_music", "media", "remember", "recall", "web_search", "open_url",
            "wifi_settings", "bluetooth_settings",
        )) {
            assertEquals("$tool is reversible or only reads", ToolRisk.Safe, ToolPolicy.risk(tool))
        }
    }

    @Test
    fun screenNavigationIsSafeButTappingAndTypingAreNot() {
        for (action in listOf("back", "home", "recents", "notifications", "scroll_down", "scroll_up", "read_screen")) {
            assertEquals(action, ToolRisk.Safe, ToolPolicy.risk("screen", mapOf("action" to action)))
        }
        for (action in listOf("tap", "type")) {
            assertEquals(action, ToolRisk.Elevated, ToolPolicy.risk("screen", mapOf("action" to action)))
        }
        assertEquals("type", ToolRisk.Elevated, ToolPolicy.risk("screen", mapOf("cmd" to "type")))
    }

    @Test
    fun anUnnamedScreenActionIsTreatedAsElevated() {
        assertEquals(ToolRisk.Elevated, ToolPolicy.risk("screen"))
        assertEquals(ToolRisk.Elevated, ToolPolicy.risk("screen", mapOf("action" to "")))
        assertEquals(ToolRisk.Elevated, ToolPolicy.risk("screen", mapOf("action" to "tap the buy button")))
        assertEquals(ToolRisk.Elevated, ToolPolicy.screenRisk(null))
    }

    @Test
    fun onlyTheSwitchAllowsTheElevatedOnes() {
        assertFalse(ToolPolicy.allowed("call", risky = false))
        assertTrue(ToolPolicy.allowed("call", risky = true))
        assertTrue("a safe tool needs no switch", ToolPolicy.allowed("battery", risky = false))
        assertFalse(ToolPolicy.allowed("screen", risky = false, args = mapOf("action" to "tap")))
        assertTrue(ToolPolicy.allowed("screen", risky = false, args = mapOf("action" to "back")))
    }

    @Test
    fun theSentenceSaysWhatIsOffAndWhereTheSwitchIs() {
        val line = ToolPolicy.paused(ToolPolicy.subject("send_sms"))
        assertTrue(line, line.contains("held back"))
        assertTrue(line, line.contains("Risky controls"))
        assertEquals("Calling someone", ToolPolicy.subject("call"))
        assertEquals("Installing an app", ToolPolicy.subject("install_app"))
        assertEquals("Forgetting what I know about you", ToolPolicy.subject("forget"))
    }
}
