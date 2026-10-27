package com.jarvis.assistant

/**
 * How long Jarvis should make its answers.
 *
 * [tokens] is what a cloud model is allowed to produce, and [instruction] is how the
 * on-device model is asked for the same thing - MediaPipe gives it no length switch of
 * its own, only the words in the prompt.
 */
enum class ReplyLength(
    val key: String,
    val label: String,
    val tokens: Int,
    val instruction: String,
) {
    Short("short", "Short", 512, "Keep this reply short: one or two sentences."),

    Normal(
        "normal",
        "Normal",
        1200,
        "Aim for a few short paragraphs, and write less only when the question is small.",
    ),

    Long(
        "long",
        "Long",
        3000,
        "Give this question the room it needs: several paragraphs, with steps, examples " +
            "or numbers where they help.",
    ),
    ;

    companion object {
        val Default = Normal

        /** What the Settings screen offers, in order. */
        val Choices: List<ReplyLength> = values().toList()

        /** The stored choice, or [Default] when it is missing or unfamiliar. */
        fun of(key: String?): ReplyLength = values().firstOrNull { it.key == key } ?: Default
    }
}
