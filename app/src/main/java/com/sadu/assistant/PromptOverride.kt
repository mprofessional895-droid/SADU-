package com.sadu.assistant

/**
 * TEMPORARY - for manual testing of prompts, not a shipped feature.
 *
 * When [on] is false the app sends the prompt it ships, exactly as designed. When it is true the
 * app sends what the user typed for that mode and nothing else: no facts, no tool menu, no rules
 * added on top. An empty box while on means a request with no instructions at all - the user
 * asked for that, so it is not "fixed" behind their back.
 *
 * The app's own safety does not move with the prompt: Chat mode still cannot touch the phone,
 * and [Tools.run] still refuses what the mode does not allow.
 */
class PromptOverride(private val prefs: Prefs) {

    val on: Boolean get() = prefs.customPromptOn

    fun edited(mode: Mode): String =
        if (mode == Mode.Chat) prefs.customChatPrompt else prefs.customControlPrompt

    /** The instructions to send, or null to use the built-in prompt for this mode. */
    fun system(mode: Mode): String? = if (!on) null else edited(mode)

    /** What the user would get if they switched this on right now. */
    fun preview(mode: Mode): String = edited(mode)

    companion object {
        const val NOTE =
            "Custom prompt is on. Control mode works only if the prompt you wrote tells the " +
                "model the tool names and their arguments."
    }
}
