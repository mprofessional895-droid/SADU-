package com.sadu.assistant

/**
 * Decides whether a message reads as a phone command or as ordinary conversation.
 *
 * This is the guard that stops Sadu mistaking chat for an order: a command must be
 * short, must not end in a question mark, and must open with an action word.
 *
 * Pure Kotlin on purpose - no Android types - so it can be unit-tested on the JVM.
 */
object CommandParser {

    private val WAKE_WORDS = listOf("hey sadu", "ok sadu", "okay sadu", "sadu")

    private val ACTION_WORDS = setOf(
        "set", "turn", "open", "launch", "start", "play", "call", "phone", "dial", "ring",
        "text", "message", "sms", "send", "write", "install", "download", "search", "google",
        "look", "remember", "note", "forget", "read", "tap", "type", "scroll", "mute", "unmute",
        "volume", "flashlight", "torch", "wake", "create", "increase", "decrease", "raise",
        "lower", "go", "navigate", "press", "home", "back", "recent", "recents", "close",
        "show", "enable", "disable", "connect", "put", "switch", "alarm", "screenshot",
    )

    /** Words that mean the "target" was really just conversational filler. */
    private val TARGET_STOP_WORDS = setOf(
        "me", "us", "you", "him", "her", "them", "it", "that", "this", "my", "your",
        "back", "later", "now", "again", "please", "a", "an", "the", "up", "about",
        "with", "for", "to", "on", "in", "of", "and", "or", "something", "anything",
    )

    private const val MAX_COMMAND_WORDS = 9
    private const val MAX_TARGET_WORDS = 4

    /**
     * Returns the text as a command, or null when it reads as conversation:
     * too long, a question, or not starting with an action word.
     */
    fun commandText(clean: String): String? {
        var s = clean.lowercase().trim()
        for (wake in WAKE_WORDS) {
            if (s.startsWith("$wake ")) {
                s = s.removePrefix("$wake ").trim()
                break
            }
        }
        if (s.startsWith("please ")) s = s.removePrefix("please ").trim()
        if (s.isEmpty() || s.endsWith("?")) return null
        val words = s.split(" ").filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > MAX_COMMAND_WORDS) return null
        return if (words.first() in ACTION_WORDS) s else null
    }

    /** True when the text looks like a real name or number, not a pronoun or filler. */
    fun isRealTarget(text: String): Boolean {
        val t = text.lowercase().trim().trim('.', '!', '?', ',')
        if (t.isEmpty()) return false
        val words = t.split(" ").filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > MAX_TARGET_WORDS) return false
        return words.none { it in TARGET_STOP_WORDS }
    }
}
