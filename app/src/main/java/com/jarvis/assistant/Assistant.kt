package com.jarvis.assistant

import com.jarvis.assistant.brain.Brain
import org.json.JSONObject
import java.util.Locale

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
    /** Reply language tag, or "auto" to follow the phone. */
    private val language: () -> String = { "auto" },
    /** Chat mode answers as conversation; Control mode may also act. */
    private val mode: () -> Mode = { Mode.Default },
) {
    private val history = ArrayList<Pair<String, String>>()

    fun resetHistory() {
        history.clear()
    }

    /** Blocking - call from a background thread. */
    fun respond(text: String): String {
        val current = mode()

        // 1. Fast rule layer. In Chat mode only the rules that need no phone access run,
        // so a command can never act on the phone from the wrong mode.
        try {
            val ruled =
                if (current == Mode.Chat) actions.tryHandleChatMode(text)
                else actions.tryHandle(text)
            ruled?.let { reply ->
                history.add(text to reply)
                return reply
            }
        } catch (_: Throwable) {
            // fall through to the brain
        }

        // 2. Brain
        if (!brain.isReady) {
            return if (current == Mode.Chat) {
                "My AI model isn't loaded yet. Tap \"Choose model file\" in Settings " +
                    "and I'll be able to chat."
            } else {
                "My AI model isn't loaded yet. Tap \"Choose model file\" in Settings, " +
                    "or give me a simple command like \"set an alarm for 7 am\"."
            }
        }
        val raw = try {
            brain.generate(promptFor(text, current))
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

    private fun promptFor(text: String, current: Mode): String {
        val facts = memory.asContext()
        val lang = languageName()
        val recent = history.takeLast(3)
        return when (current) {
            // No tool menu and no JSON protocol: the model's whole context is the conversation.
            Mode.Chat -> Prompt.chat(facts, lang, recent, text)
            Mode.Control -> Prompt.control(facts, tools.menu(), lang, recent, text)
        }
    }

    /** The display name of the chosen reply language, or "" to follow the phone. */
    private fun languageName(): String {
        val tag = language()
        if (tag == "auto" || tag.isBlank()) return ""
        return Locale.forLanguageTag(tag).displayLanguage.takeIf { it.isNotBlank() }.orEmpty()
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

    private fun cleanChat(raw: String): String = ReplyText.clean(raw)
}
