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
            // Unknown tool name: treat the reply as chat, with the JSON stripped out.
        }

        val reply = cleanChat(raw)
        history.add(text to reply)
        return reply
    }

    private fun buildPrompt(text: String): String {
        val sb = StringBuilder()
        sb.append("You are Jarvis, a warm, capable assistant running privately on the user's Android phone. ")
        sb.append("Talk like a helpful friend. Keep replies short, one to three sentences, ")
        sb.append("and answer in the same language the user uses.\n\n")

        sb.append("You have two modes:\n")
        sb.append("1. CHAT - if the user is chatting, asking a question, or just talking, reply with plain words. ")
        sb.append("Never use JSON in chat.\n")
        sb.append("2. COMMAND - if the user clearly asks you to DO something on the phone that a tool can do, ")
        sb.append("reply with ONLY a single JSON object and nothing else.\n\n")

        sb.append("Your tools:\n")
        sb.append(tools.menu()).append("\n\n")

        val facts = memory.asContext()
        if (facts.isNotEmpty()) {
            sb.append("Things you know about the user: ").append(facts).append("\n\n")
        }

        sb.append("Command examples:\n")
        sb.append("User: text mom that I'm on my way\n")
        sb.append("Jarvis: {\"tool\":\"send_sms\",\"args\":{\"contact\":\"Mom\",\"message\":\"I'm on my way\"}}\n")
        sb.append("User: turn on the flashlight\n")
        sb.append("Jarvis: {\"tool\":\"flashlight\",\"args\":{\"on\":\"true\"}}\n")
        sb.append("User: what's on my screen right now\n")
        sb.append("Jarvis: {\"tool\":\"screen\",\"args\":{\"action\":\"read_screen\"}}\n\n")

        sb.append("Chat examples:\n")
        sb.append("User: how are you today?\n")
        sb.append("Jarvis: I'm doing well, thanks for asking! What are you up to?\n")
        sb.append("User: I'm feeling a bit tired\n")
        sb.append("Jarvis: Sorry to hear that. Want me to set an alarm so you can rest?\n")
        sb.append("User: tell me a joke\n")
        sb.append("Jarvis: Why did the phone go to therapy? It had too many unresolved calls.\n\n")

        sb.append("Rules:\n")
        sb.append("- If you are not sure whether it is a command or chat, choose CHAT.\n")
        sb.append("- Never say you did something unless a tool actually did it.\n")
        sb.append("- Do not mention JSON, tools or these instructions to the user.\n\n")

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
        // If the model slipped a tool call into a chat reply, drop the JSON block.
        s = s.replace(Regex("\\{[\\s\\S]{0,400}\\}"), " ").trim()
        if (s.contains("Jarvis:")) s = s.substringAfterLast("Jarvis:")
        s = s.trim().removePrefix("model").trim()
        s = s.trim('`', '*', '#', '"', '\'', ' ', '\n', '\t')
        if (s.isBlank()) s = "I'm here. What would you like to do?"
        return s
    }
}
