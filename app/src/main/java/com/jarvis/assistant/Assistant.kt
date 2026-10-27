package com.jarvis.assistant

import com.jarvis.assistant.brain.Brain
import com.jarvis.assistant.brain.LlmRequest
import org.json.JSONObject
import java.util.Locale

/**
 * The router. For every message it:
 *   1. tries fast, reliable rules (Actions) - covers common commands instantly;
 *   2. otherwise asks the brain, which may answer with a tool call or plain chat;
 *   3. runs the tool and reports the result, or returns the chat reply.
 *
 * It also owns the conversation that is sent back to the model, trimmed to whatever the
 * current context window can hold.
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
    /** The model's context window, from Settings. */
    private val contextTokens: () -> Int = { ContextBudget.Default },
) {
    private val history = ArrayList<Turn>()

    fun resetHistory() {
        history.clear()
    }

    /**
     * A conversation that was opened again: keep talking where it left off instead of
     * starting blank.
     */
    fun loadHistory(turns: List<Turn>) {
        history.clear()
        history.addAll(turns.takeLast(MaxTurns))
    }

    /** The turns currently remembered, for tests and for saving. */
    fun remembered(): List<Turn> = history.toList()

    private fun remember(text: String, reply: String) {
        history.add(Turn(text, reply))
        while (history.size > MaxTurns) history.removeAt(0)
    }

    /**
     * Blocking - call from a background thread.
     *
     * [onDelta] receives the answer as it arrives. In Control mode a reply may be a tool
     * call, so the text is held back until it is clearly prose and never flashes JSON on
     * the screen.
     */
    fun respond(text: String, onDelta: (String) -> Unit = {}): String {
        val current = mode()

        try {
            val ruled =
                if (current == Mode.Chat) actions.tryHandleChatMode(text)
                else actions.tryHandle(text)
            ruled?.let { reply ->
                remember(text, reply)
                return reply
            }
        } catch (_: Throwable) {
        }

        if (!brain.isReady) return notReady(current)

        val gate = if (current == Mode.Control) StreamGate(onDelta) else null
        val raw = try {
            brain.generateStream(requestFor(text, current)) { delta ->
                if (gate == null) onDelta(delta) else gate.feed(delta)
            }
        } catch (e: Throwable) {
            return "Sorry, my brain hit an error: ${e.message}"
        }

        parseToolCall(raw)?.let { (name, args) ->
            val outcome = tools.run(name, args)
            if (outcome.handled) {
                remember(text, outcome.display)
                return outcome.display
            }
        }

        gate?.finish()
        val reply = cleanChat(raw)
        remember(text, reply)
        return reply
    }

    /** Instructions, the turns that fit the window, and the new message. */
    private fun requestFor(text: String, current: Mode): LlmRequest {
        val menu = if (current == Mode.Control) tools.menu() else ""
        val instructions = Prompt.system(current, memory.asContext(), languageName(), menu)
        val fixed = ContextBudget.estimate(instructions) + ContextBudget.estimate(text)
        val window = ContextBudget.clamp(contextTokens())
        return LlmRequest(
            system = instructions,
            turns = ContextBudget.select(history, window, fixed),
            user = text,
            mode = current,
        )
    }

    private fun notReady(current: Mode): String =
        if (current == Mode.Chat) {
            "My AI model isn't loaded yet. Import a model file in Settings, " +
                "or turn on the cloud brain there."
        } else {
            "My AI model isn't ready. Import a model file or turn on the cloud brain in " +
                "Settings, or give me a simple command like \"set an alarm for 7 am\"."
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

/** How many exchanges are kept in memory for context. */
private const val MaxTurns = 200
