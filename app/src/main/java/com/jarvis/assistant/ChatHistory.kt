package com.jarvis.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One saved conversation shown in the sidebar. */
data class ChatSession(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val messages: List<ChatMsg> = emptyList(),
)

/** Keeps every conversation in app storage so the sidebar can list them. */
class ChatHistory(ctx: Context) {

    private val file = File(ctx.filesDir, "chats.json")

    fun load(): List<ChatSession> = try {
        if (!file.exists()) {
            emptyList()
        } else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val ma = o.optJSONArray("messages") ?: JSONArray()
                val msgs = (0 until ma.length()).mapNotNull { j ->
                    val m = ma.optJSONObject(j) ?: return@mapNotNull null
                    ChatMsg(m.optString("t"), m.optBoolean("u"), m.optLong("i", j.toLong()))
                }
                ChatSession(o.optLong("id"), o.optString("title"), o.optLong("at"), msgs)
            }
        }
    } catch (_: Throwable) {
        emptyList()
    }

    fun save(sessions: List<ChatSession>) {
        try {
            val arr = JSONArray()
            for (s in sessions) {
                val o = JSONObject()
                o.put("id", s.id)
                o.put("title", s.title)
                o.put("at", s.createdAt)
                val ma = JSONArray()
                for (m in s.messages) {
                    val mo = JSONObject()
                    mo.put("t", m.text)
                    mo.put("u", m.fromUser)
                    mo.put("i", m.id)
                    ma.put(mo)
                }
                o.put("messages", ma)
                arr.put(o)
            }
            file.writeText(arr.toString())
        } catch (_: Throwable) {
            // A failed save must never break the chat
        }
    }
}
