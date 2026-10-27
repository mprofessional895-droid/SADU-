package com.jarvis.assistant.brain

import com.jarvis.assistant.Turn
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The cloud APIs this app knows how to talk to. */
enum class CloudProvider(val label: String, val defaultBase: String, val hint: String) {
    Compatible(
        "OpenAI-compatible",
        "https://api.openai.com/v1",
        "OpenAI, OpenRouter, Groq, DeepSeek - or Ollama / LM Studio on your own network",
    ),
    Anthropic(
        "Anthropic",
        "https://api.anthropic.com",
        "Claude models",
    ),
    Gemini(
        "Google Gemini",
        "https://generativelanguage.googleapis.com/v1beta",
        "Gemini models",
    ),
}

/**
 * Everything the user filled in for the cloud brain. Empty strings mean "not set yet";
 * a blank API key is allowed, which is what a server on the user's own network needs.
 */
data class CloudConfig(
    val provider: CloudProvider = CloudProvider.Compatible,
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 512,
    val timeoutMs: Int = 60_000,
) {
    /** The address to call: what the user typed, or the provider's own default. */
    val base: String get() = baseUrl.trim().ifBlank { provider.defaultBase }

    val isUsable: Boolean get() = model.isNotBlank()

    val keyless: Boolean get() = apiKey.isBlank()

    /** True for this machine or the local network, where no key is normal. */
    val localHost: Boolean
        get() = Regex(
            "localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0|10\\.|192\\.168\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.",
        ).containsMatchIn(base)

    /** Cleartext HTTP is only sensible on the user's own network. */
    val insecureRemote: Boolean get() = base.startsWith("http://") && !localHost
}

/** Builds each provider's HTTP request. Pure, so it is tested without any network. */
object CloudRequest {

    fun url(cfg: CloudConfig, stream: Boolean): String {
        val base = cfg.base.trimEnd('/')
        return when (cfg.provider) {
            CloudProvider.Compatible ->
                if (base.endsWith("/chat/completions")) base else "$base/chat/completions"

            CloudProvider.Anthropic ->
                if (base.endsWith("/messages")) base else "$base/v1/messages"

            CloudProvider.Gemini ->
                if (base.contains(":generateContent") || base.contains(":streamGenerateContent")) base
                else "$base/models/${cfg.model}:${if (stream) "streamGenerateContent?alt=sse" else "generateContent"}"
        }
    }

    fun headers(cfg: CloudConfig, stream: Boolean): Map<String, String> {
        val h = LinkedHashMap<String, String>()
        h["Content-Type"] = "application/json"
        if (stream) h["Accept"] = "text/event-stream"
        when (cfg.provider) {
            CloudProvider.Compatible ->
                if (cfg.apiKey.isNotBlank()) h["Authorization"] = "Bearer ${cfg.apiKey}"

            CloudProvider.Anthropic -> {
                if (cfg.apiKey.isNotBlank()) h["x-api-key"] = cfg.apiKey
                h["anthropic-version"] = "2023-06-01"
            }

            CloudProvider.Gemini ->
                if (cfg.apiKey.isNotBlank()) h["x-goog-api-key"] = cfg.apiKey
        }
        return h
    }

    fun body(cfg: CloudConfig, request: LlmRequest, stream: Boolean): String =
        when (cfg.provider) {
            CloudProvider.Compatible -> JSONObject()
                .put("model", cfg.model)
                .put("messages", roles(request))
                .put("temperature", cfg.temperature.toDouble())
                .put("max_tokens", cfg.maxTokens)
                .put("stream", stream)
                .toString()

            CloudProvider.Anthropic -> JSONObject()
                .put("model", cfg.model)
                .put("system", request.system)
                .put("messages", roles(request, skipSystem = true))
                .put("temperature", cfg.temperature.toDouble())
                .put("max_tokens", cfg.maxTokens)
                .put("stream", stream)
                .toString()

            CloudProvider.Gemini -> JSONObject()
                .put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", request.system)),
                    ),
                )
                .put("contents", geminiContents(request))
                .put(
                    "generationConfig",
                    JSONObject()
                        .put("temperature", cfg.temperature.toDouble())
                        .put("maxOutputTokens", cfg.maxTokens),
                )
                .toString()
        }

    /** system + the conversation so far + the new message. */
    private fun roles(request: LlmRequest, skipSystem: Boolean = false): JSONArray {
        val arr = JSONArray()
        if (!skipSystem) {
            arr.put(JSONObject().put("role", "system").put("content", request.system))
        }
        for (t in request.turns) {
            arr.put(JSONObject().put("role", "user").put("content", t.user))
            if (t.reply.isNotBlank()) {
                arr.put(JSONObject().put("role", "assistant").put("content", t.reply))
            }
        }
        arr.put(JSONObject().put("role", "user").put("content", request.user))
        return arr
    }

    private fun geminiContents(request: LlmRequest): JSONArray {
        val arr = JSONArray()
        for (t in request.turns) {
            arr.put(geminiTurn("user", t.user))
            if (t.reply.isNotBlank()) arr.put(geminiTurn("model", t.reply))
        }
        arr.put(geminiTurn("user", request.user))
        return arr
    }

    private fun geminiTurn(role: String, text: String): JSONObject = JSONObject()
        .put("role", role)
        .put("parts", JSONArray().put(JSONObject().put("text", text)))
}

/** Reads text out of each provider's JSON. Pure. */
object CloudReply {

    /** The full text of a non-streaming reply, or "" when the shape is unfamiliar. */
    fun text(provider: CloudProvider, body: String): String = try {
        val json = JSONObject(body)
        when (provider) {
            CloudProvider.Compatible ->
                json.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()

            CloudProvider.Anthropic -> joinTexts(json.optJSONArray("content"), "text")

            CloudProvider.Gemini -> geminiText(json)
        }
    } catch (_: Throwable) {
        ""
    }

    /** The text in one streamed chunk, or null when the chunk carries none. */
    fun delta(provider: CloudProvider, data: String): String? = try {
        val json = JSONObject(data)
        val out = when (provider) {
            CloudProvider.Compatible ->
                json.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("delta")
                    ?.optString("content")
                    .orEmpty()

            CloudProvider.Anthropic ->
                if (json.optString("type") == "content_block_delta") {
                    json.optJSONObject("delta")?.optString("text").orEmpty()
                } else {
                    ""
                }

            CloudProvider.Gemini -> geminiText(json)
        }
        out.ifEmpty { null }
    } catch (_: Throwable) {
        null
    }

    private fun joinTexts(blocks: JSONArray?, field: String): String {
        if (blocks == null) return ""
        val sb = StringBuilder()
        for (i in 0 until blocks.length()) {
            sb.append(blocks.optJSONObject(i)?.optString(field).orEmpty())
        }
        return sb.toString()
    }

    private fun geminiText(json: JSONObject): String {
        val out = StringBuilder()
        val list = json.optJSONArray("candidates") ?: return ""
        for (i in 0 until list.length()) {
            val parts = list.optJSONObject(i)?.optJSONObject("content")?.optJSONArray("parts")
                ?: continue
            for (p in 0 until parts.length()) {
                out.append(parts.optJSONObject(p)?.optString("text").orEmpty())
            }
        }
        return out.toString()
    }

    /** A short human reason from an error body. */
    fun detail(body: String): String {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return "no details"
        try {
            val json = JSONObject(trimmed)
            val message = json.optJSONObject("error")?.optString("message").orEmpty()
            if (message.isNotBlank()) return message.take(200)
            val alt = json.optString("message")
            if (alt.isNotBlank()) return alt.take(200)
        } catch (_: Throwable) {
        }
        return trimmed.take(200)
    }
}

class CloudBrain(private val config: () -> CloudConfig) : Brain {

    override val isReady: Boolean get() = config().isUsable

    override val name: String
        get() = "Cloud (${config().model.ifBlank { "not set" }})"

    override fun generate(request: LlmRequest): String {
        val cfg = config()
        if (!cfg.isUsable) throw IOException("No cloud model name is set")
        val body = CloudRequest.body(cfg, request, stream = false)
        val text = post(cfg, CloudRequest.url(cfg, stream = false), body, null)
        if (text.isBlank()) throw IOException("The cloud model sent an empty reply")
        return text
    }

    override fun generateStream(request: LlmRequest, onDelta: (String) -> Unit): String {
        val cfg = config()
        if (!cfg.isUsable) throw IOException("No cloud model name is set")
        val body = CloudRequest.body(cfg, request, stream = true)
        val text = post(cfg, CloudRequest.url(cfg, stream = true), body, onDelta)
        // A server that ignores "stream" answers with the whole body at once.
        return if (text.isBlank()) generate(request) else text
    }

    /** POST and read the answer. [onDelta] is null for a whole-body reply. */
    private fun post(
        cfg: CloudConfig,
        url: String,
        body: String,
        onDelta: ((String) -> Unit)?,
    ): String {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = cfg.timeoutMs
                doOutput = true
                useCaches = false
                CloudRequest.headers(cfg, onDelta != null).forEach { (k, v) ->
                    setRequestProperty(k, v)
                }
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val text = runCatching {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull().orEmpty()
                throw IOException("Cloud error $code: ${CloudReply.detail(text)}${advice(code)}")
            }

            if (onDelta == null) {
                val whole = conn.inputStream.bufferedReader().use { it.readText() }
                return CloudReply.text(cfg.provider, whole)
            }
            return readStream(cfg, conn, onDelta)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun readStream(
        cfg: CloudConfig,
        conn: HttpURLConnection,
        onDelta: (String) -> Unit,
    ): String {
        val out = StringBuilder()
        conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                val data = Sse.payload(line) ?: continue
                val delta = CloudReply.delta(cfg.provider, data) ?: continue
                out.append(delta)
                onDelta(delta)
            }
        }
        return out.toString()
    }

    private fun advice(code: Int): String = when (code) {
        401, 403 -> " - check the API key"
        404 -> " - check the base URL and the model name"
        429 -> " - rate limited, try again shortly"
        in 500..599 -> " - the provider is having trouble"
        else -> ""
    }
}

/** Server-sent events, one line at a time. */
object Sse {
    fun payload(line: String): String? {
        val t = line.trim()
        if (!t.startsWith("data:")) return null
        return t.removePrefix("data:").trim().ifEmpty { null }
    }
}

/** Reads a stored provider name back, defaulting to the OpenAI-compatible one. */
fun cloudProviderFrom(name: String?): CloudProvider =
    CloudProvider.values().firstOrNull { it.name == name } ?: CloudProvider.Compatible
