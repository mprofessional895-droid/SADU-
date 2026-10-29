package com.sadu.assistant

import org.json.JSONObject

/**
 * Reading a tool call out of whatever the model actually said.
 *
 * Small local models tend to answer with a bare `{"tool":...}` because the prompt drills it.
 * A cloud model is more opinionated: it may wrap the object in a markdown fence, introduce it
 * with a sentence, or answer in the OpenAI shape (`name` plus `arguments` as a *string*).
 * All of those mean the same thing, and refusing them is what would make the cloud look
 * unable to control the phone.
 *
 * Pure Kotlin and JSON only, so it is tested without a network or a device.
 */
object ToolJson {

    fun first(raw: String): Pair<String, Map<String, String>>? = all(raw).firstOrNull()

    /** Every call-shaped object in the text, in the order they appear. */
    fun all(raw: String): List<Pair<String, Map<String, String>>> {
        val text = unfence(raw)
        val out = ArrayList<Pair<String, Map<String, String>>>()
        var i = 0
        while (i < text.length) {
            if (text[i] == '{') {
                val end = matchingBrace(text, i)
                if (end > i) {
                    read(text.substring(i, end + 1))?.let { out.add(it) }
                    i = end + 1
                    continue
                }
            }
            i++
        }
        return out
    }

    /** ```json ... ``` and ``` ... ``` become plain text; nothing else is touched. */
    private fun unfence(raw: String): String = raw
        .replace(Regex("```[a-zA-Z]*\\s*"), "")
        .replace("```", "")

    /** The index of the `}` that closes the `{` at [start], or -1 when it never closes. */
    private fun matchingBrace(text: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    private fun read(json: String): Pair<String, Map<String, String>>? {
      try {
        val obj = JSONObject(json)
        val name = listOf("tool", "action", "name", "tool_name")
            .firstNotNullOfOrNull { key ->
                obj.optString(key, "").trim().takeIf { it.isNotBlank() && it != "function" }
            }
            ?: obj.optJSONObject("function")?.optString("name", "")?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: return null

        val args = LinkedHashMap<String, String>()
        val a = obj.optJSONObject("args")
            ?: obj.optJSONObject("arguments")
            ?: obj.optJSONObject("parameters")
            ?: obj.optJSONObject("function")?.optJSONObject("arguments")
        if (a != null) {
            val keys = a.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                args[k] = a.opt(k)?.toString() ?: ""
            }
        } else {
            // OpenAI puts the arguments in a JSON *string*, not an object.
            val text = obj.optString("arguments", "")
            if (text.trimStart().startsWith("{")) {
                runCatching {
                    val inner = JSONObject(text)
                    val keys = inner.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        args[k] = inner.opt(k)?.toString() ?: ""
                    }
                }
            }
        }
        if (args.isEmpty()) {
            // Some models flatten the arguments next to the tool name.
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k in META) continue
                val v = obj.opt(k)
                if (v is JSONObject || v is org.json.JSONArray) continue
                args[k] = v?.toString() ?: ""
            }
        }
        return name to args
      } catch (_: Throwable) {
        return null
      }
    }

    private val META = setOf("tool", "action", "name", "tool_name", "args", "arguments", "parameters", "function")
}
