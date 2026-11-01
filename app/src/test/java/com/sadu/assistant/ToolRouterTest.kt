package com.sadu.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRouterTest {

    private val specs = listOf(
        ToolSpec("whatsapp", "contact, message", "send a WhatsApp message"),
        ToolSpec("flashlight", "on", "turn the flashlight on or off"),
        ToolSpec("battery", "", "battery level"),
        ToolSpec("play_music", "query", "play music"),
        ToolSpec("screen", "action, text", "control the screen"),
        ToolSpec("web_search", "query", "search the web"),
        ToolSpec("remember", "fact", "save a fact"),
        ToolSpec("open_app", "app", "open an app"),
        ToolSpec("set_alarm", "time", "set an alarm"),
    )

    private fun names(text: String) = ToolRouter.select(text, specs).map { it.name }

    @Test
    fun aWhatsAppMessageBringsTheMessagingTools() {
        assertEquals(listOf("whatsapp", "remember", "open_app"), names("shubham ko whatsapp par message bhejo ki hello"))
    }

    @Test
    fun aBatteryQuestionKeepsTheSystemTools() {
        val picked = names("battery kitni hai")
        assertTrue(picked.contains("battery"))
        assertFalse(picked.contains("whatsapp"))
        assertTrue(picked.size < specs.size)
    }

    @Test
    fun aFlashlightCommandKeepsTheSystemTools() {
        val picked = names("flashlight on karo")
        assertTrue(picked.contains("flashlight"))
        assertFalse(picked.contains("play_music"))
    }

    @Test
    fun anUnknownMessageKeepsEveryTool() {
        assertEquals(specs, ToolRouter.select("what is a good name for a cat", specs))
    }

    @Test
    fun aScreenTaskKeepsTheAppToolForTheNextStep() {
        val picked = names("screen par back dabao")
        assertTrue(picked.contains("screen"))
        assertTrue(picked.contains("open_app"))
    }

    @Test
    fun aMenuWithNothingToOfferIsLeftAlone() {
        val only = listOf(ToolSpec("battery", "", "battery level"))
        assertEquals(only, ToolRouter.select("battery", only))
    }
}
