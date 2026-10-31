package com.sadu.assistant.brain

/**
 * Ends a turn at the first stop mark, without letting the tail of a mark reach the screen.
 *
 * The marks have to be cut here rather than by the tokenizer: in the model this project targets
 * `<end_function_call>` is an ordinary piece of text, not an end-of-generation token, so a small
 * model will happily write `<escape>` a hundred more times after it. Holding back the last few
 * characters means a mark split across two pieces is still caught whole.
 */
class StopMarks(marks: List<String>) {

    private val marks = marks.filter { it.isNotEmpty() }
    private val hold = (marks.maxOfOrNull { it.length } ?: 0) - 1
    private val pending = StringBuilder()

    var isStopped: Boolean = false
        private set

    /** The text that may be shown for this piece, possibly empty. "" forever after a mark. */
    fun take(piece: String): String {
        if (isStopped) return ""
        if (marks.isEmpty()) return piece
        pending.append(piece)

        val cut = marks.mapNotNull { mark -> pending.indexOf(mark).takeIf { it >= 0 } }.minOrNull()
        if (cut != null) {
            isStopped = true
            val out = pending.substring(0, cut)
            pending.setLength(0)
            return out
        }
        val safe = pending.length - hold
        if (safe <= 0) return ""
        val out = pending.substring(0, safe)
        pending.delete(0, safe)
        return out
    }

    /** The whole reply with everything from the first mark on dropped. */
    fun truncate(text: String): String {
        if (marks.isEmpty()) return text
        val cut = marks.mapNotNull { mark -> text.indexOf(mark).takeIf { it >= 0 } }.minOrNull()
            ?: return text
        return text.substring(0, cut)
    }
}
