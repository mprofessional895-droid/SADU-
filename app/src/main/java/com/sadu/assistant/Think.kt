package com.sadu.assistant

/**
 * Splits a model's private reasoning from the answer it actually gives.
 *
 * Small local models and most cloud ones wrap their working-out in a pair of tags -
 * ` thinking...</think>`, `<thought>...</thought>`, or the channel form some builds use.
 * Those tags must never reach the reader, so the thinking goes into its own card and the
 * answer is what stays in the conversation.
 *
 * Pure Kotlin so it can be tested without Android. [open] is true while a think block has
 * started but not closed, which is exactly the period the thinking card is live.
 */
object Think {

    /**
     * Instructions added when thinking is switched off in Settings. The suffix is the switch
     * Qwen3 reads inside the user message; the sentence covers every other model.
     */
    const val OFF_INSTRUCTION =
        "Do not use a thinking or reasoning block. Answer directly, without working-out, and " +
            "never write  thinking, <thought> or <|channel|> tags."

    const val OFF_SUFFIX = "\n/no_think"

    data class Split(val thinking: String, val answer: String, val open: Boolean)

    private val OPEN = Regex(
        "(&lt;|<)\\|?\\s*(?:think|thinking|thought|reasoning|analysis)\\s*\\|?(&gt;|>)",
        RegexOption.IGNORE_CASE,
    )
    private val CLOSE = Regex(
        "(&lt;|<)\\s*/\\s*(?:think|thinking|thought|reasoning|analysis)\\s*(&gt;|>)",
        RegexOption.IGNORE_CASE,
    )
    private val CHANNEL_CLOSE = Regex("<\\s*channel\\s*\\|", RegexOption.IGNORE_CASE)

    fun split(raw: String): Split {
        if (raw.isEmpty()) return Split("", "", false)
        val open = OPEN.find(raw) ?: return Split("", raw, false)
        val before = raw.substring(0, open.range.first)
        val rest = raw.substring(open.range.last + 1)
        val shut = CLOSE.find(rest) ?: CHANNEL_CLOSE.find(rest)
        if (shut == null) return Split(rest, before, true)
        val thinking = rest.substring(0, shut.range.first)
        val answer = before + rest.substring(shut.range.last + 1)
        return Split(thinking, answer, false)
    }

    /** The answer alone, with every think block removed - for saved messages. */
    fun answer(raw: String): String {
        var text = raw
        while (true) {
            val split = split(text)
            if (split.thinking.isEmpty() && split.answer == text) return text
            if (split.open) return split.answer
            text = split.answer
            if (OPEN.find(text) == null) return text
        }
    }

    /** Everything the model thought, joined - for the card the reader can open. */
    fun thinking(raw: String): String {
        val parts = ArrayList<String>()
        var text = raw
        while (true) {
            val split = split(text)
            if (split.thinking.isEmpty() && split.answer == text) break
            if (split.thinking.isNotBlank()) parts.add(split.thinking)
            if (split.open) break
            text = split.answer
            if (OPEN.find(text) == null) break
        }
        return parts.joinToString("\n\n").trim()
    }
}
