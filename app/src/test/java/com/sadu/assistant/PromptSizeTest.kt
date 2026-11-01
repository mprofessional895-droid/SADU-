package com.sadu.assistant

import com.sadu.assistant.prompt.ChatPrompt
import com.sadu.assistant.prompt.ChatStyle
import com.sadu.assistant.prompt.ControlPrompt
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prompt budgets, in characters, measured on the real menus.
 *
 * Control mode is re-read in full on every message and again after every tool step, so its size
 * is a speed setting, not a style choice. These are ceilings with room to spare, not exact
 * targets: a change that pushes past one has to be argued for.
 */
class PromptSizeTest {

    private val fullMenu =
        "- flashlight(on (true|false)): turn the phone flashlight on or off\n" +
        "- battery(): report the battery level\n" +
        "- volume(direction (up|down|mute|unmute), level (0-100)): change media volume; level sets it exactly when given\n" +
        "- wifi_settings(): open Wi-Fi settings\n" +
        "- bluetooth_settings(): open Bluetooth settings\n" +
        "- time(): tell the current time\n" +
        "- date(): tell today's date\n" +
        "- set_alarm(time): set an alarm, e.g. 6:30 am\n" +
        "- call(contact): call a saved contact by name\n" +
        "- send_sms(contact, message): send a text message to a contact\n" +
        "- open_app(app): open an installed app by name\n" +
        "- play_music(query): play music or a song\n" +
        "- remember(fact): save a fact about the user\n" +
        "- recall(): tell what Sadu remembers about the user\n" +
        "- forget(): forget everything about the user\n" +
        "- web_search(query): search the web (needs Internet on)\n" +
        "- open_url(url): open a website (needs Internet on)\n" +
        "- media(action (pause|resume|next|previous|stop)): control the music that is playing\n" +
        "- install_app(app): open the app store to install an app (needs Internet on)\n" +
        "- screen(action (back|home|recents|tap|type|scroll_down|scroll_up|read_screen), text): control or read the screen via accessibility; text is used for tap/type\n";

    private val smallMenu = "- whatsapp(contact, message): write a WhatsApp message to a contact or a group\n" +
        "- open_app(app): open an installed app by name\n" +
        "- remember(fact): save a fact about the user"

    private val facts = "The user's name is Shubham; lives in Ahmedabad"

    @Test
    fun controlModeWithEveryToolStaysUnderBudget() {
        val prompt = ControlPrompt.build(facts, fullMenu, "")
        assertTrue("control prompt is ${prompt.length} chars", prompt.length <= 2650)
    }

    @Test
    fun aNarrowedMenuIsMuchShorter() {
        val full = ControlPrompt.build(facts, fullMenu, "").length
        val narrow = ControlPrompt.build(facts, smallMenu, "").length
        assertTrue("narrow $narrow vs full $full", narrow < full - 500)
    }

    @Test
    fun chatModeStaysTiny() {
        val prompt = ChatPrompt.build(facts, "", ReplyLength.Default, ChatStyle.Minimal)
        assertTrue("chat prompt is ${prompt.length} chars", prompt.length <= 900)
    }

    @Test
    fun theRulesThatMatterSurviveTheTrim() {
        val prompt = ControlPrompt.build(facts, smallMenu, "")
        assertTrue(prompt.contains("ONLY one JSON object"))
        assertTrue(prompt.contains("Never say something was done unless its tool result says it was"))
        assertTrue(prompt.contains("Use only the tools above"))
        assertTrue(prompt.contains("escape codes"))
        assertTrue(prompt.contains("One tool call per reply"))
    }
}
