package com.sadu.assistant

import com.sadu.assistant.brain.Brain
import com.sadu.assistant.brain.LlmRequest
import com.sadu.assistant.prompt.ChatStyle
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
    /** The Internet switch, so a search can say why it did nothing instead of guessing. */
    private val internet: () -> Boolean = { true },
    /** How a page is fetched. Swapped in tests so no connection is needed. */
    private val fetch: (String) -> String = WebSearch.live(),
    /** Whether the controls that act as the user are switched on (Settings). */
    private val riskyAllowed: () -> Boolean = { false },
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
        lastWebSources = emptyList()
        stage = null
        val current = mode()

        // "search about X": look it up in the background and answer from what came back.
        // With the Internet switch off there is nothing to look up, and saying so is better
        // than throwing the question at a browser tab.
        if (internet() && webAnswers()) {
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

        // The model asked for a look-up itself - Control mode's web_search tool, or Chat
        // mode's one-line "SEARCH: ..." request. Do the search here and let it answer from
        // the results, rather than handing the user a browser tab.
        val asked = if (current == Mode.Chat) {
            searchRequest(raw)
        } else {
            toolQuery(raw, "web_search")
        }
        if (asked != null && internet() && webAnswers()) {
            gate?.finish()
            webAnswer(text, asked, current, onDelta)?.let { return it }
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

    /**
     * Fetches the results, asks the model to answer from them, and returns that answer.
     *
     * It always returns a sentence, including when the web gave nothing: the app used to hand
     * the question to a browser tab in that case, which looked like Sadu had gone off to search
     * and never come back. A short, truthful line about what happened is more useful - and in
     * Chat mode opening a browser is not something Sadu may do at all.
     */
    private fun webAnswer(
        text: String,
        query: String,
        current: Mode,
        onDelta: (String) -> Unit,
    ): String? {
        stage = "Searching the web"
        val found = try {
            WebSearch.lookup(query, fetch)
        } catch (e: Throwable) {
            stage = null
            val line = "I couldn't reach the web just now" +
                (e.message?.let { " ($it)" } ?: "") + ". Check the Internet switch in Settings."
            remember(text, line)
            return line
        }

        if (found.hits.isEmpty()) {
            stage = null
            val line = if (found.blocked) {
                "I did search for \"$query\", but the search engine answered with a robot check " +
                    "instead of results, so I have nothing to answer from."
            } else {
                "I searched for \"$query\", but the web had nothing useful about it."
            }
            remember(text, line)
            return line
        }

        if (brain.isReady) {
            stage = "Reading the results"
            val enriched = WebSearch.digest(query, found.hits) + "\n\nMy question: " + text
            val raw = try {
                brain.generateStream(requestFor(enriched, current, webResults = true), onDelta)
            } catch (e: Throwable) {
                null
            }
            val answer = raw?.let { cleanChat(it) }.orEmpty()
            if (answer.isNotBlank() && parseToolCall(raw.orEmpty()) == null &&
                searchRequest(answer) == null
            ) {
                stage = null
                answeredWithModel = true
                lastWebSources = found.sources
                remember(text, answer)
                return answer
            }
        }

        // Nothing to summarise with: read out what was found rather than nothing at all.
        stage = null
        lastWebSources = found.sources
        val spoken = WebSearch.readable(query, found.hits)
        remember(text, spoken)
        return spoken
    }

    /**
     * What the app is doing while it works, for the line under the thinking dots
     * ("Searching the web"). Written on a worker thread and read by the UI, so volatile.
     */
    @Volatile
    var stage: String? = null
        private set

    /** Which sites the last web answer was built from, for the note under a reply. */
    var lastWebSources: List<String> = emptyList()
        private set

    /** The query when the model itself asked for a look-up, or null. */
    private fun searchRequest(raw: String): String? =
        Regex("(?im)^\\s*search\\s*:\\s*(.+)$").find(raw)
            ?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

    /** The query when the model called a tool by name, or null. */
    private fun toolQuery(raw: String, tool: String): String? {
        val call = parseToolCall(raw) ?: return null
        if (!call.first.equals(tool, ignoreCase = true)) return null
        return listOf("query", "value", "q").firstNotNullOfOrNull { key ->
            call.second[key]?.takeIf { it.isNotBlank() }
        }
    }

    private fun requestFor(text: String, current: Mode, webResults: Boolean = false): LlmRequest {
        val menu = if (current == Mode.Control) tools.menu(riskyAllowed()) else ""
        val instructions = Prompt.system(
            mode = current,
            facts = memory.asContext(),
            languageName = languageName(),
            menu = menu,
            length = replyLength(),
            style = chatStyle(),
            extra = extraInstructions(),
            webResults = webResults,
            canSearch = current == Mode.Chat && internet() && webAnswers(),
            riskyAllowed = riskyAllowed(),
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
