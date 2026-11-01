package com.sadu.assistant

/** One exchange: what the user said and what Sadu answered. */
data class Turn(val user: String, val reply: String)

/**
 * Works out how much of a conversation fits in the model's context window.
 *
 * The window belongs to the model, not to the app: the on-device Gemma runs with
 * whatever `LlmEngine` asked for, and a cloud model has its own limit. So nothing here
 * is fixed - it is told the window, what the instructions cost, and how much room to
 * leave for the reply, and it returns the turns that fit.
 */
object ContextBudget {

    const val Min = 1024
    const val Max = 8192

    /** What the Settings screen offers. */
    val Choices = listOf(1024, 2048, 4096, 8192)

    const val Default = 4096

    /** Room kept free for the reply itself. */
    const val ReplyReserve = 512

    /**
     * Rough token count. Deliberately pessimistic: most English and Hindi text runs
     * nearer three characters per token than four, and undercounting here means a
     * prompt that is silently cut off by the model.
     */
    fun estimate(text: String): Int = if (text.isEmpty()) 0 else text.length / 3 + 1

    /** Turns per-exchange overhead (role markers, separator lines). */
    private const val TurnOverhead = 8

    /**
     * The turns to send, with the dropped ones condensed into one clearly-labelled line.
     *
     * History that silently disappears makes the model repeat itself; history that is cut in half
     * makes it answer the wrong thing. So a middle turn that does not fit becomes a short note
     * about what was already discussed, and the note is marked as a summary, not as the user's
     * words.
     */
    fun compact(
        turns: List<Turn>,
        windowTokens: Int,
        instructionTokens: Int,
        reserveTokens: Int = ReplyReserve,
    ): List<Turn> {
        val kept = select(turns, windowTokens, instructionTokens, reserveTokens)
        if (kept.size == turns.size || kept.isEmpty()) return kept

        val keptSet = kept.toSet()
        val dropped = turns.filterNot { it in keptSet }
        val gist = dropped.mapNotNull { it.user.trim().take(GIST) }.filter { it.isNotBlank() }
        if (gist.isEmpty()) return kept

        val note = Turn("(earlier in this conversation: " + gist.joinToString("; ") + ")", "")
        val budget = windowTokens - instructionTokens - reserveTokens
        if (estimate(note.user) + TurnOverhead > budget) return kept
        return listOf(note) + kept
    }

    /** How much of a dropped message the note keeps. */
    private const val GIST = 60

    fun clamp(requested: Int): Int = when {
        requested <= 0 -> Default
        requested < Min -> Min
        requested > Max -> Max
        else -> requested
    }

    fun label(tokens: Int): String = when {
        tokens >= 1024 -> "${tokens / 1024}k"
        else -> "$tokens"
    }

    /** Largest window that fits the given choices, for showing what is really in use. */
    fun usableWindow(requested: Int, acceptedUpTo: Int): Int =
        Choices.lastOrNull { it <= acceptedUpTo } ?: Min

    /**
     * The turns to send, oldest first. Newest turns win, and the very first turn is always
     * kept when it still fits so the subject of the conversation is never lost.
     */
    fun select(
        turns: List<Turn>,
        windowTokens: Int,
        instructionTokens: Int,
        reserveTokens: Int = ReplyReserve,
    ): List<Turn> {
        if (turns.isEmpty()) return emptyList()
        val budget = windowTokens - instructionTokens - reserveTokens
        if (budget <= 0) return emptyList()

        val cost = { t: Turn -> estimate(t.user) + estimate(t.reply) + TurnOverhead }
        val chosen = ArrayDeque<Int>()
        var used = 0
        for (i in turns.indices.reversed()) {
            val c = cost(turns[i])
            if (used + c > budget) break
            chosen.addFirst(i)
            used += c
        }
        if (chosen.isNotEmpty() && chosen.first() != 0 && used + cost(turns[0]) <= budget) {
            chosen.addFirst(0)
        }
        return chosen.map { turns[it] }
    }
}
