package com.sadu.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One line in a conversation. */
data class ChatMsg(
    val text: String,
    val fromUser: Boolean,
    val id: Long = System.nanoTime(),
    val at: Long = System.currentTimeMillis(),
    /** What the model thought before answering, when it shared it. */
    val thinking: String? = null,
    /** When each phase of this turn happened, if it was measured. */
    val stats: TurnStats? = null,
)

/**
 * The timings Sadu can honestly report for one answer: how long the model read the prompt,
 * how long it thought, how long until its first word, and how fast it wrote.
 * Any value that was not measured stays null rather than showing a made-up number.
 */
data class TurnStats(
    val thinkingMs: Long? = null,
    val promptMs: Long? = null,
    val ttftMs: Long? = null,
    val genTokS: Double? = null,
)

/** One saved conversation. */
data class ChatSession(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val messages: List<ChatMsg>,

    /** The mode this conversation was started in - shown as a tag in the list. */
    val mode: Mode = Mode.Default,
) {
    /** How many things the user actually asked. */
    val askedCount: Int get() = messages.count { it.fromUser }

    /** First user question, for the preview line. */
    val preview: String
        get() = messages.firstOrNull { it.fromUser }?.text?.squash().orEmpty()

    val lastAt: Long get() = messages.lastOrNull()?.at ?: createdAt
}

private fun String.squash(): String = replace(Regex("\\s+"), " ").trim()

/**
 * Conversations are kept in filesDir/chats.json on the phone.
 * Nothing here is uploaded anywhere.
 */
class ChatHistory(private val ctx: Context) {

    private val file: File get() = File(ctx.filesDir, "chats.json")

    private fun readStats(m: JSONObject): TurnStats? {
        val tm = m.optLong("tm", -1L)
        val pm = m.optLong("pm", -1L)
        val tt = m.optLong("tt", -1L)
        val gs = m.optDouble("gs", -1.0)
        if (tm < 0 && pm < 0 && tt < 0 && gs <= 0) return null
        return TurnStats(
            thinkingMs = tm.takeIf { it >= 0 },
            promptMs = pm.takeIf { it >= 0 },
            ttftMs = tt.takeIf { it >= 0 },
            genTokS = gs.takeIf { it > 0 },
        )
    }

    fun load(): List<ChatSession> = try {
        if (!file.exists()) emptyList() else {
            val root = JSONArray(file.readText())
            (0 until root.length()).mapNotNull { i -> readSession(root.optJSONObject(i)) }
        }
    } catch (_: Throwable) {
        emptyList()
    }

    fun save(sessions: List<ChatSession>) {
        try {
            val root = JSONArray()
            sessions.forEach { s ->
                val msgs = JSONArray()
                s.messages.forEach { m ->
                    msgs.put(
                        JSONObject()
                            .put("t", m.text)
                            .put("u", m.fromUser)
                            .put("i", m.id)
                            .put("at", m.at)
                            .put("th", m.thinking.orEmpty())
                            .put("tm", m.stats?.thinkingMs ?: -1L)
                            .put("pm", m.stats?.promptMs ?: -1L)
                            .put("tt", m.stats?.ttftMs ?: -1L)
                            .put("gs", m.stats?.genTokS ?: -1.0),
                    )
                }
                root.put(
                    JSONObject()
                        .put("id", s.id)
                        .put("title", s.title)
                        .put("created", s.createdAt)
                        .put("mode", s.mode.tag)
                        .put("msgs", msgs),
                )
            }
            file.writeText(root.toString())
        } catch (_: Throwable) {
            // A failed save must never break the chat.
        }
    }

    private fun readSession(o: JSONObject?): ChatSession? {
        o ?: return null
        val id = o.optLong("id").takeIf { it != 0L } ?: return null
        val created = o.optLong("created", System.currentTimeMillis())
        val arr = o.optJSONArray("msgs") ?: JSONArray()
        val msgs = (0 until arr.length()).mapNotNull { i ->
            val m = arr.optJSONObject(i) ?: return@mapNotNull null
            val text = m.optString("t")
            if (text.isEmpty()) null
            else ChatMsg(
                text = text,
                fromUser = m.optBoolean("u", false),
                id = m.optLong("i", System.nanoTime()),
                // Older files had no timestamp; the conversation start is the best guess.
                at = m.optLong("at", created),
                thinking = m.optString("th").takeIf { it.isNotBlank() },
                stats = readStats(m),
            )
        }
        return ChatSession(
            id = id,
            title = o.optString("title").ifBlank { "Conversation" },
            createdAt = created,
            messages = msgs,
            // Older files have no mode; Chat is the safe reading.
            mode = Mode.from(o.optString("mode")),
        )
    }

    fun sizeBytes(): Long = try {
        if (file.exists()) file.length() else 0L
    } catch (_: Throwable) {
        0L
    }
}

/**
 * Pairs the messages of a saved conversation into exchanges, so a reopened chat is fed
 * back to the model instead of starting blank. The opening greeting has no question, so
 * it is left out.
 */
fun turnsOf(messages: List<ChatMsg>): List<Turn> {
    val out = ArrayList<Turn>()
    var pending: String? = null
    for (m in messages) {
        if (m.fromUser) {
            pending?.let { out.add(Turn(it, "")) }
            pending = m.text
        } else if (pending != null) {
            out.add(Turn(pending, m.text))
            pending = null
        }
    }
    pending?.let { out.add(Turn(it, "")) }
    return out
}
