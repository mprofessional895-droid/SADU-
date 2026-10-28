package com.jarvis.assistant.prompt

import com.jarvis.assistant.Turn

/**
 * Gemma's own chat template, in one place, frozen.
 *
 * Both modes ride on this: the instructions and the conversation are wrapped here, and the
 * prompt ends exactly where the model starts writing. Gemma has no system role, so the
 * instructions travel with the first user turn.
 *
 * Nothing in this file knows what a mode is. Changing it changes both modes, which is the
 * point - it is the model's format, not our policy.
 */
object GemmaChatTemplate : ChatTemplate {

    const val VERSION = "gemma-template-1"

    private const val TURN_USER = "<start_of_turn>user\n"
    private const val TURN_MODEL = "<start_of_turn>model\n"
    private const val TURN_END = "<end_of_turn>\n"

    /** Room for one message, in characters. The token budget is what really trims. */
    const val MESSAGE_CAP = 2000
    const val HISTORY_CAP = 1500

    /** Instructions, the conversation so far, and the new message, in Gemma's format. */
    override fun build(instructions: String, turns: List<Turn>, text: String): String {
        val sb = StringBuilder()
        val head = instructions.trim()
        if (turns.isEmpty()) {
            sb.append(turnWithPrompt(head, text, MESSAGE_CAP))
        } else {
            turns.forEachIndexed { index, (user, reply) ->
                val said = if (index == 0) turnWithPrompt(head, user, MESSAGE_CAP)
                else userTurn(user, MESSAGE_CAP)
                sb.append(said)
                sb.append(TURN_MODEL).append(reply.trim().take(HISTORY_CAP)).append("\n").append(TURN_END)
            }
            sb.append(userTurn(text, MESSAGE_CAP))
        }
        return sb.append(TURN_MODEL).toString()
    }

    fun userTurn(content: String, cap: Int = MESSAGE_CAP): String =
        TURN_USER + content.trim().take(cap) + "\n" + TURN_END

    /**
     * Instructions and one spoken line in the same turn, with the instructions giving way:
     * a long prompt must never trim away the words the user actually said.
     */
    fun turnWithPrompt(head: String, said: String, cap: Int = MESSAGE_CAP): String {
        val body = said.trim()
        // The two newlines that join them count against the cap as well.
        val room = (cap - body.length - 2).coerceAtLeast(cap / 2)
        return userTurn(join(head.take(room).trim(), body), cap)
    }

    fun join(head: String, body: String): String =
        if (head.isEmpty()) body else head + "\n\n" + body
}
