package com.jarvis.assistant

/** One capability the AI can call. */
data class ToolSpec(val name: String, val args: String, val description: String)

/** What came back from running a tool. */
data class ToolOutcome(
    val display: String,
    val speak: String = display,
    val handled: Boolean = true,
)

/**
 * The toolbox. The AI chooses a tool by name; this runs it.
 * Anything that needs the network checks the Internet toggle inside Actions.
 */
class Tools(private val actions: Actions, private val memory: Memory) {

    val specs: List<ToolSpec> = listOf(
        ToolSpec("flashlight", "on (true|false)", "turn the phone flashlight on or off"),
        ToolSpec("battery", "", "report the battery level"),
        ToolSpec("volume", "direction (up|down|mute|unmute)", "change media volume"),
        ToolSpec("wifi_settings", "", "open Wi-Fi settings"),
        ToolSpec("bluetooth_settings", "", "open Bluetooth settings"),
        ToolSpec("time", "", "tell the current time"),
        ToolSpec("date", "", "tell today's date"),
        ToolSpec("set_alarm", "time", "set an alarm, e.g. 6:30 am"),
        ToolSpec("call", "contact", "call a saved contact by name"),
        ToolSpec("send_sms", "contact, message", "send a text message to a contact"),
        ToolSpec("open_app", "app", "open an installed app by name"),
        ToolSpec("play_music", "query", "play music or a song"),
        ToolSpec("remember", "fact", "save a fact about the user"),
        ToolSpec("recall", "", "tell what Jarvis remembers about the user"),
        ToolSpec("forget", "", "forget everything about the user"),
        ToolSpec("web_search", "query", "search the web (needs Internet on)"),
        ToolSpec("open_url", "url", "open a website (needs Internet on)"),
        ToolSpec("install_app", "app", "open the app store to install an app (needs Internet on)"),
        ToolSpec(
            "screen",
            "action (back|home|recents|tap|type|scroll_down|scroll_up|read_screen), text",
            "control or read the screen via accessibility; text is used for tap/type"
        ),
    )

    fun menu(): String = specs.joinToString("\n") { "- ${it.name}(${it.args}): ${it.description}" }

    fun run(name: String, args: Map<String, String>): ToolOutcome {
        fun arg(vararg keys: String): String {
            for (k in keys) args[k]?.let { if (it.isNotBlank()) return it }
            return ""
        }
        return try {
            when (name.lowercase()) {
                "flashlight" -> {
                    val v = arg("on", "state", "value")
                    val on = v.equals("true", true) || v.equals("on", true) || v.contains("yes")
                    ToolOutcome(actions.setTorch(on))
                }
                "battery" -> ToolOutcome(actions.battery())
                "volume" -> ToolOutcome(
                    actions.volume(arg("direction", "action", "value").ifBlank { "up" }.lowercase())
                )
                "wifi_settings", "wifi" -> ToolOutcome(
                    runCatching { actions.tryHandle("open wifi settings") }.getOrNull()
                        ?: "I couldn't open Wi-Fi settings."
                )
                "bluetooth_settings", "bluetooth" -> ToolOutcome(
                    runCatching { actions.tryHandle("open bluetooth settings") }.getOrNull()
                        ?: "I couldn't open Bluetooth settings."
                )
                "time" -> ToolOutcome(
                    runCatching { actions.tryHandle("what time is it") }.getOrNull() ?: "I'm not sure of the time."
                )
                "date" -> ToolOutcome(
                    runCatching { actions.tryHandle("what is the date") }.getOrNull() ?: "I'm not sure of the date."
                )
                "set_alarm", "alarm" -> ToolOutcome(actions.setAlarm(arg("time", "value", "when")))
                "call" -> ToolOutcome(actions.call(arg("contact", "who", "name", "number")))
                "send_sms", "sms", "text" -> ToolOutcome(
                    actions.sendSms(arg("contact", "who", "name", "number"), arg("message", "body", "text"))
                )
                "open_app", "open" -> ToolOutcome(actions.openApp(arg("app", "name", "value")))
                "play_music", "play" -> ToolOutcome(actions.play(arg("query", "song", "value")))
                "remember" -> {
                    val fact = arg("fact", "value", "text")
                    if (fact.isBlank()) ToolOutcome("What should I remember?", handled = false)
                    else { memory.add(fact); ToolOutcome("Got it, I'll remember: $fact.") }
                }
                "recall" -> {
                    val f = memory.facts()
                    ToolOutcome(
                        if (f.isEmpty()) "I don't know anything about you yet."
                        else "Here's what I know: " + f.joinToString("; ") + "."
                    )
                }
                "forget" -> { memory.clear(); ToolOutcome("Done. I've forgotten everything about you.") }
                "web_search" -> ToolOutcome(actions.webSearch(arg("query", "value", "q")))
                "open_url" -> ToolOutcome(actions.openUrl(arg("url", "value")))
                "install_app" -> ToolOutcome(actions.installApp(arg("app", "name", "value")))
                "screen", "accessibility" -> {
                    val action = arg("action", "cmd", "value")
                    val text = arg("text", "label", "value2")
                    if (action == "read_screen") ToolOutcome(actions.readScreen())
                    else ToolOutcome(actions.accessibilityAction(action, text))
                }
                else -> ToolOutcome("I don't have a tool called \"$name\".", handled = false)
            }
        } catch (e: Throwable) {
            ToolOutcome("That action failed: ${e.message}")
        }
    }
}
