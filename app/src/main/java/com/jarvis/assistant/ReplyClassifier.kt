package com.jarvis.assistant

/** How a reply should be presented. */
enum class ReplyKind { Answer, Notice, Failure }

/**
 * The router answers in plain sentences, so a failure is recognised from its wording.
 * This lives in one tested place instead of being guessed at in the UI.
 */
object ReplyClassifier {

    private val FAILURES = listOf(
        "Sorry,",
        "Model failed to load:",
        "Copy failed:",
        "I couldn't",
        "I cannot",
        "I can't",
    )

    private val NOTICES = listOf(
        "My AI model isn't loaded",
        "Screen control is off",
        "I need microphone permission",
        "Speech recognition isn't available",
        "This phone doesn't seem",
    )

    fun of(text: String): ReplyKind {
        val t = text.trim()
        return when {
            FAILURES.any { t.startsWith(it, ignoreCase = true) } -> ReplyKind.Failure
            NOTICES.any { t.startsWith(it, ignoreCase = true) } -> ReplyKind.Notice
            else -> ReplyKind.Answer
        }
    }
}
