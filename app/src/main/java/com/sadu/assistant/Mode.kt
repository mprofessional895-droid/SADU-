package com.sadu.assistant

/**
 * What Sadu is allowed to do right now.
 *
 * [Chat] is conversation only: the model gets a clean prompt with no tool protocol at all,
 * and every phone action is refused. [Control] keeps the whole toolbox.
 *
 * The mode is a permission, not a hint - it is enforced in code (see [ModePolicy]) so a
 * model that ignores its instructions still cannot touch the phone.
 */
enum class Mode(val tag: String) {
    Chat("chat"),
    Control("control"),
    ;

    companion object {
        val Default = Chat

        /** Anything unreadable, including the missing setting in older files, is Chat. */
        fun from(tag: String?): Mode =
            entries.firstOrNull { it.tag == tag?.trim()?.lowercase() } ?: Default
    }
}

/** The one place that decides whether a tool may run in the current mode. */
object ModePolicy {

    /** Chat mode may still reach the network - the user asked for that, and nothing else. */
    val NetworkTools = setOf("web_search", "open_url", "install_app")

    /** Names that mean "do something on the phone", tool names and their aliases. */
    val PhoneTools = setOf(
        "flashlight", "torch", "battery", "volume", "wifi_settings", "wifi",
        "bluetooth_settings", "bluetooth", "set_alarm", "alarm", "call", "phone",
        "send_sms", "sms", "text", "open_app", "open", "play_music", "play",
        "screen", "accessibility",
    )

    /** Phone actions are Control mode only; the internet is fine in both. */
    fun allowed(mode: Mode, tool: String): Boolean =
        mode == Mode.Control || tool.trim().lowercase() in NetworkTools

    /** True when this name asks for something that only Control mode may do. */
    fun isPhoneTool(tool: String): Boolean = tool.trim().lowercase() in PhoneTools

    fun refusal(): String =
        "That's a phone action and I'm in Chat mode, so I didn't do it. " +
            "Switch to Control mode and ask me again."
}
