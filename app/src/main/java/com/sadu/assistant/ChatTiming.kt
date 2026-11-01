package com.sadu.assistant

/**
 * Wall-clock numbers for one Chat-mode message, so a slow reply can be explained instead of
 * guessed at: how long the model took to say its first word (the prompt was being read), how
 * long the rest took (it was writing), and how many model requests one message needed.
 *
 * Control mode does not use this: its turns are a chain of tool steps, not one answer.
 */
class ChatTiming(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {

    private var start = 0L
    private var first: Long? = null
    private var promptChars = 0
    private var replyChars = 0
    private var last = ""

    var requests: Int = 0
        private set

    fun begin() {
        start = clock()
        first = null
        promptChars = 0
        replyChars = 0
        requests = 0
        last = ""
    }

    /** One model request went out, carrying [chars] characters of prompt. */
    fun request(chars: Int) {
        requests++
        promptChars += chars
    }

    fun firstToken() {
        if (first == null) first = clock()
    }

    /** The message is over. Returns the line worth logging. */
    fun done(chars: Int, failed: String? = null): String {
        replyChars = chars
        val total = clock() - start
        val ttft = first?.let { it - start }
        last = buildString {
            append("chat total=").append(total).append("ms")
            append(" ttft=").append(ttft?.toString() ?: "none").append("ms")
            append(" requests=").append(requests)
            append(" prompt=").append(tokens(promptChars)).append("tok~")
            append(" reply=").append(tokens(replyChars)).append("tok~")
            if (failed != null) append(" failed=").append(failed)
        }
        return last
    }

    fun line(): String = last

    private fun tokens(chars: Int): Int = if (chars <= 0) 0 else chars / 3 + 1

    /** Time to first token, or null when the model never said anything. */
    fun timeToFirstTokenMs(): Long? = first?.let { it - start }
}
