package com.jarvis.assistant.brain

import com.jarvis.assistant.Mode
import com.jarvis.assistant.Turn

/**
 * One request, in a shape every brain understands.
 *
 * [system] is the instructions, [turns] the conversation so far and [user] the new
 * message. A local model gets all of it flattened into one prompt; a cloud model maps it
 * onto real system/user/assistant roles.
 */
data class LlmRequest(
    val system: String,
    val turns: List<Turn>,
    val user: String,
    val mode: Mode,
)

/**
 * The seam between the app and whatever answers: the on-device model, a cloud model, or
 * the router that picks between them.
 */
interface Brain {

    val isReady: Boolean

    /** Short name shown in Settings, e.g. "On-device Gemma". */
    val name: String

    /**
     * True when the last reply stopped at the model's output limit rather than because it
     * was finished. Only a brain that is told so can say - the on-device engine does not
     * report a stop reason, so it keeps the default.
     */
    val lastReplyTruncated: Boolean get() = false

    fun generate(request: LlmRequest): String

    /**
     * Same answer, but [onDelta] is called as text arrives. The default keeps one-shot
     * brains honest: they hand over the whole reply in a single delta.
     */
    fun generateStream(request: LlmRequest, onDelta: (String) -> Unit): String {
        val reply = generate(request)
        if (reply.isNotEmpty()) onDelta(reply)
        return reply
    }
}
