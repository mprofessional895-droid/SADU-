package com.sadu.assistant

/** What a tool could do if it ran when it should not have. */
enum class ToolRisk { Safe, Elevated }

/**
 * Which controls Sadu may use on its own, and which are held back until the user says so.
 *
 * Elevated is not "forbidden", it is "not yet". The switch is [Prefs.riskyControls], off by
 * default, so neither a model that ignores its instructions nor a misheard voice command can
 * send a message, place a call, install an app, or tap somewhere inside another app.
 *
 * This is the second gate. [ModePolicy] answers "may this mode touch the phone at all"; this
 * one answers "is this particular control switched on". Both are checked in code, at every
 * entry point, so a prompt that is ignored still cannot act.
 */
object ToolPolicy {

    /** Tools that act as the user, spend money, or delete something the user asked to keep. */
    val ElevatedTools = setOf(
        "call", "phone", "dial", "ring",
        "send_sms", "sms", "text", "message", "whatsapp",
        "install_app", "forget",
    )

    /** Screen actions that only navigate or read. tap and type can do anything a finger can. */
    val SafeScreenActions = setOf(
        "back", "home", "recents", "notifications",
        "scroll", "scroll_down", "scroll_up", "read_screen",
    )

    fun risk(tool: String, args: Map<String, String> = emptyMap()): ToolRisk {
        val key = tool.trim().lowercase()
        if (key in ElevatedTools) return ToolRisk.Elevated
        if (key == "screen" || key == "accessibility") {
            return screenRisk(args["action"] ?: args["cmd"] ?: args["value"])
        }
        return ToolRisk.Safe
    }

    /** An unnamed screen action is treated as elevated: only the known-safe ones are allowed. */
    fun screenRisk(action: String?): ToolRisk =
        if (action?.trim()?.lowercase() in SafeScreenActions) ToolRisk.Safe else ToolRisk.Elevated

    fun allowed(tool: String, risky: Boolean, args: Map<String, String> = emptyMap()): Boolean =
        risky || risk(tool, args) == ToolRisk.Safe

    /** A short, human name for the control that was held back, for the sentence Sadu says. */
    fun subject(tool: String, args: Map<String, String> = emptyMap()): String =
        when (tool.trim().lowercase()) {
            "call", "phone", "dial", "ring" -> "Calling someone"
            "send_sms", "sms", "text", "message" -> "Sending a message"
            "whatsapp" -> "Messaging someone on WhatsApp"
            "install_app" -> "Installing an app"
            "forget" -> "Forgetting what I know about you"
            "screen", "accessibility" -> "Tapping and typing inside other apps"
            else -> "That"
        }

    /** What Sadu says instead of doing it: honest, and it points at the switch. */
    fun paused(what: String): String =
        "$what is held back for now, so I didn't do it. " +
            "Settings > What Sadu may do > Risky controls turns it on."
}
