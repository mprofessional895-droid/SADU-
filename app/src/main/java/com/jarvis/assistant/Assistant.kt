package com.jarvis.assistant

import com.jarvis.assistant.brain.Brain
import org.json.JSONObject

/**
 * The router. For every message it:
 *   1. tries fast, reliable rules (Actions) - covers common commands instantly;
 *   2. otherwise asks the brain, which may answer with a tool call or plain chat;
 *   3. runs the tool and reports the result, or returns the chat reply.
 */
class Assistant(
    private val brain: Brain,
    private val actions: Actions,
    private val memory: Memory,
    private val tools: Tools,
) {
    private val history = ArrayList<Pair<String, String>>()

    fun resetHistory() {
        history.clear()
    }

    /** Blocking - call from a background thread. */
    fun respond(text: String): String {
        // 1. Fast rule layer
        try {
            actions.tryHandle(text)?.let { reply ->
                history.add(text to reply)
                return reply
            }
        } catch (_: Throwable) {
            // fall through to the brain
        }

        // 2. Brain
        if (!brain.isReady) {
            return "My AI model isn't loaded yet. Tap \"Choose model file\" in Settings, " +
                "or give me a simple command like \"set an alarm for 7 am\"."
        }
        val raw = try {
            brain.generate(buildPrompt(text))
        } catch (e: Throwable) {
            return "Sorry, my brain hit an error: ${e.message}"
        }

        // 3. Tool call?
        parseToolCall(raw)?.let { (name, args) ->
            val outcome = tools.run(name, args)
            if (outcome.handled) {
                history.add(text to outcome.display)
                return outcome.display
            }
        }

        val reply = cleanChat(raw)
        history.add(text to reply)
        return reply
    }

    private fun buildPrompt(text: String): String {
        val sb = StringBuilder()
        sb.append("You are Jarvis, a friendly, capable assistant that runs privately on the user's Android phone. ")
        sb.append("Reply briefly and naturally, in the same language the user uses.\n\n")
        sb.append("You can control the phone with these tools:\n")
        sb.append(tools.menu()).append("\n\n")
        val facts = memory.asContext()
        if (facts.isNotEmpty()) {
            sb.append("Things you know about the user: ").append(facts).append("\n\n")
        }
        sb.append("If the user asks you to DO something on the phone and a tool fits, reply with ONLY a JSON object, ")
        sb.append("for example {\"tool\":\"send_sms\",\"args\":{\"contact\":\"Mom\",\"message\":\"on my way\"}}. ")
        sb.append("For normal conversation reply with plain text and NO JSON. ")
        sb.append("Never claim to have done something you did not do with a tool.\n\n")
        for ((u, a) in history.takeLast(3)) {
            sb.append("User: ").append(u.take(200)).append("\n")
            sb.append("Jarvis: ").append(a.take(200)).append("\n")
        }
        sb.append("User: ").append(text.take(400)).append("\nJarvis:")
        return sb.toString()
    }

    private fun parseToolCall(raw: String): Pair<String, Map<String, String>>? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            val obj = JSONObject(raw.substring(start, end + 1))
            val name = obj.optString("tool", obj.optString("action", "")).trim()
            if (name.isBlank()) return null
            val args = HashMap<String, String>()
            val a = obj.optJSONObject("args") ?: obj.optJSONObject("arguments")
            if (a != null) {
                val keys = a.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    args[k] = a.opt(k)?.toString() ?: ""
                }
            }
            name to args
        } catch (_: Throwable) {
            null
        }
    }

    private fun cleanChat(raw: String): String {
        var s = raw
            .replace("<end_of_turn>", "")
            .replace("<eos>", "")
            .replace("<start_of_turn>", "")
        if (s.contains("Jarvis:")) s = s.substringAfterLast("Jarvis:")
        s = s.trim().removePrefix("model").trim()
        if (s.isBlank()) s = "Hmm, I'm not sure what to say to that."
        return s
    }
}
