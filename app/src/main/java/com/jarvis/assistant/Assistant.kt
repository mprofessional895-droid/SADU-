package com.jarvis.assistant

import com.jarvis.assistant.brain.Brain
import com.jarvis.assistant.brain.LlmRequest
import com.jarvis.assistant.prompt.ChatStyle
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
    /** How long answers should be, from Settings. */
    private val replyLength: () -> ReplyLength = { ReplyLength.Default },
    /** How Chat mode's answers should read, from Settings. */
    private val chatStyle: () -> ChatStyle = { ChatStyle.Default },
    /** The user's own extra instructions for Chat mode, from Settings. */
    private val extraInstructions: () -> String = { "" },
    /** Whether a "search for ..." may be looked up here and answered from the results. */
    private val webAnswers: () -> Boolean = { false },
    /** How a page is fetched. Swapped in tests so no connection is needed. */
    private val fetch: (String) -> String = WebSearch.live(),
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
    /** True when the last answer came from a model, false when a built-in rule answered. */
    var answeredWithModel = false
        private set

    /** True when the last model answer stopped at the output limit, not at the end. */
    var lastReplyTruncated = false
        private set

    /** True when a Chat-mode reply claimed a phone action it cannot have done. */
    var lastActionClaim = false
        private set

    fun respond(text: String, onDelta: (String) -> Unit = {}): String {
        answeredWithModel = false
        lastReplyTruncated = false
        lastActionClaim = false
        val current = mode()

        // "search about X": look it up in the background and answer from what came back.
        if (webAnswers()) {
            actions.searchQuery(text)?.let { query ->
                webAnswer(text, query, current, onDelta)?.let { return it }
            }
        }

        // Chat mode cannot act on the phone, so it says so itself rather than letting the
        // model promise something that will never happen.
        if (current == Mode.Chat && ChatPolicy.phoneIntent(text)) {
            val line = ChatPolicy.NOT_HERE
            remember(text, line)
            return line
        }

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
        lastActionClaim = current == Mode.Chat && ChatPolicy.claimsPhoneAction(reply)
        answeredWithModel = true
        lastReplyTruncated = brain.lastReplyTruncated
        remember(text, reply)
        return reply
    }

    /** Instructions, the turns that fit the window, and the new message. */
    /**
     * Fetches the results, asks the model to answer from them, and returns that answer.
     * Null means "I could not do this", and the normal path (which opens the browser)
     * takes over.
     */
    private fun webAnswer(
        text: String,
        query: String,
        current: Mode,
        onDelta: (String) -> Unit,
    ): String? {
        val hits = try {
            WebSearch.search(query, fetch)
        } catch (_: Throwable) {
            return null
        }
        if (hits.isEmpty()) return null

        if (brain.isReady) {
            val enriched = WebSearch.digest(query, hits) + "\n\nMy question: " + text
            val raw = try {
                brain.generateStream(requestFor(enriched, current, webResults = true), onDelta)
            } catch (e: Throwable) {
                null
            }
            val answer = raw?.let { cleanChat(it) }.orEmpty()
            if (answer.isNotBlank() && parseToolCall(raw.orEmpty()) == null) {
                answeredWithModel = true
                remember(text, answer)
                return answer
            }
        }

        // Nothing to summarise with: read out what was found rather than nothing at all.
        val spoken = WebSearch.readable(query, hits)
        remember(text, spoken)
        return spoken
    }

    private fun requestFor(text: String, current: Mode, webResults: Boolean = false): LlmRequest {
        val menu = if (current == Mode.Control) tools.menu() else ""
        val instructions = Prompt.system(
            mode = current,
            facts = memory.asContext(),
            languageName = languageName(),
            menu = menu,
            length = replyLength(),
            style = chatStyle(),
            extra = extraInstructions(),
            webResults = webResults,
        )
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
