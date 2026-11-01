package com.sadu.assistant.brain

import com.sadu.assistant.Mode
import com.sadu.assistant.ToolSpec
import com.sadu.assistant.Turn
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The cloud APIs this app knows how to talk to. */
enum class CloudProvider(
    val label: String,
    val defaultBase: String,
    val hint: String,
    val exampleModel: String,
) {
    Compatible(
        "OpenAI-compatible",
        "https://api.openai.com/v1",
        "OpenAI, OpenRouter, Groq, DeepSeek - or Ollama / LM Studio on your own network",
        "gpt-4o-mini",
    ),
    Anthropic(
        "Anthropic",
        "https://api.anthropic.com",
        "Claude models",
        "claude-3-5-sonnet-latest",
    ),
    Gemini(
        "Google Gemini",
        "https://generativelanguage.googleapis.com/v1beta",
        "Gemini models",
        "gemini-2.0-flash",
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
    /**
     * The address to call: what the user typed, or the provider's own default. A missing
     * scheme becomes https://, because that is what every hosted provider uses, and a
     * trailing slash is dropped so paths never double up.
     */
    val base: String
        get() {
            val typed = baseUrl.trim().ifBlank { provider.defaultBase }
            val withScheme =
                if (typed.startsWith("http://") || typed.startsWith("https://")) typed
                else "https://$typed"
            return withScheme.trimEnd('/')
        }

    val isUsable: Boolean get() = problem() == null

    /**
     * True when a reply should come from this cloud model. Switching it on is the consent
     * to send chats to the provider, so the separate "allow internet access" switch - which
     * is about the phone tools - has no say here.
     */
    fun shouldAnswer(enabled: Boolean): Boolean = enabled && isUsable

    /**
     * A human reason this setup cannot be used yet, or null when it is fine. The same
     * wording is shown under the fields and thrown if a call is made anyway.
     */
    fun problem(): String? {
        if (model.isBlank()) return "Add the model name, for example ${provider.exampleModel}"
        val address = base
        val host = address.removePrefix("https://").removePrefix("http://")
            .substringBefore('/').substringBefore(':')
        if (host.isBlank()) return "That address has no server name in it"
        if (host.contains(' ') || host.contains('"')) return "That address is not a web address"
        if (keyless && provider != CloudProvider.Compatible) {
            return "${provider.label} needs an API key"
        }
        return null
    }

    /** Worth saying, but not a reason to refuse: the key would travel in the clear. */
    fun warning(): String? =
        if (insecureRemote) "http:// is unencrypted. Use https:// unless this is your own server."
        else null

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

    fun body(
        cfg: CloudConfig,
        request: LlmRequest,
        stream: Boolean,
        tools: List<ToolSpec> = emptyList(),
    ): String = build(cfg, request, stream, if (request.mode == Mode.Control) tools else emptyList())
        .toString()

    /**
     * The request, built once so the tools can be added where each provider expects them.
     * [tools] is empty in Chat mode: that mode is conversation only, and a model with no
     * tools declared cannot be rejected for calling one.
     */
    private fun build(
        cfg: CloudConfig,
        request: LlmRequest,
        stream: Boolean,
        tools: List<ToolSpec>,
    ): JSONObject = when (cfg.provider) {
        CloudProvider.Compatible -> JSONObject()
            .put("model", cfg.model)
            .put("messages", roles(request))
            .put("temperature", cfg.temperature.toDouble())
            .put("max_tokens", cfg.maxTokens)
            .put("stream", stream)
            .also { json ->
                if (tools.isNotEmpty()) {
                    json.put("tools", CloudTools.openAi(tools))
                    json.put("tool_choice", "auto")
                }
            }

        CloudProvider.Anthropic -> JSONObject()
            .put("model", cfg.model)
            .put("system", request.system)
            .put("messages", roles(request, skipSystem = true))
            .put("temperature", cfg.temperature.toDouble())
            .put("max_tokens", cfg.maxTokens)
            .put("stream", stream)
            .also { json ->
                if (tools.isNotEmpty()) {
                    json.put("tools", CloudTools.anthropic(tools))
                    json.put("tool_choice", JSONObject().put("type", "auto"))
                }
            }

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
            .also { json ->
                if (tools.isNotEmpty()) json.put("tools", CloudTools.gemini(tools))
            }
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
        val said = when (provider) {
            CloudProvider.Compatible ->
                str(
                    json.optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("message")?.opt("content"),
                )

            CloudProvider.Anthropic -> joinTexts(json.optJSONArray("content"), "text")

            CloudProvider.Gemini -> geminiText(json)
        }
        // A model that called a function usually says nothing alongside it.
        if (said.isNotBlank()) said else asToolJson(calls(provider, json))
    } catch (_: Throwable) {
        ""

    }

    /** One function call, however the provider delivered it. */
    data class Call(
        val index: Int = -1,
        val name: String = "",
        val arguments: String = "",
    ) {
        /** Streaming pieces carry a name once and the arguments in slices. */
        fun merge(next: Call) = Call(
            index = index,
            name = if (name.isBlank()) next.name else name,
            arguments = arguments + next.arguments,
        )
    }

    /** The calls in a whole (non-streaming) reply. */
    fun calls(provider: CloudProvider, json: JSONObject): List<Call> = try {
        when (provider) {
            CloudProvider.Compatible -> {
                val list = json.optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optJSONArray("tool_calls")
                (0 until (list?.length() ?: 0)).mapNotNull { i ->
                    val fn = list?.optJSONObject(i)?.optJSONObject("function") ?: return@mapNotNull null
                    val name = fn.optString("name")
                    if (name.isBlank()) null
                    else Call(i, name, fn.optString("arguments"))
                }
            }

            CloudProvider.Anthropic ->
                anthropicBlocks(json.optJSONArray("content"))
                    .filter { it.type == "tool_use" }
                    .mapIndexed { i, block -> Call(i, block.name, block.args) }

            CloudProvider.Gemini ->
                geminiCalls(json).mapIndexed { i, call -> Call(i, call.name, call.arguments) }
        }
    } catch (_: Throwable) {
        emptyList()
    }

    /** A function-call fragment inside one streamed chunk, or null. */
    fun toolDelta(provider: CloudProvider, data: String): Call? =
        runCatching { toolDeltaOrThrow(provider, data) }.getOrNull()

    private fun toolDeltaOrThrow(provider: CloudProvider, data: String): Call? {
        val json = JSONObject(data)
        return when (provider) {
            CloudProvider.Compatible -> {
                val calls = json.optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("delta")?.optJSONArray("tool_calls")
                val first = calls?.optJSONObject(0) ?: return null
                Call(
                    index = first.optInt("index", -1),
                    name = first.optJSONObject("function")?.optString("name").orEmpty(),
                    arguments = first.optJSONObject("function")?.optString("arguments").orEmpty(),
                )
            }

            CloudProvider.Anthropic -> when (json.optString("type")) {
                // The name and id come once, the arguments follow as partial JSON.
                "content_block_start" -> {
                    val block = json.optJSONObject("content_block")
                    if (block?.optString("type") != "tool_use") null
                    else Call(json.optInt("index", -1), block.optString("name"), "")
                }

                "content_block_delta" -> {
                    val delta = json.optJSONObject("delta")
                    if (delta?.optString("type") != "input_json_delta") null
                    else Call(
                        json.optInt("index", -1),
                        "",
                        delta.optString("partial_json").orEmpty(),
                    )
                }

                else -> null
            }

            CloudProvider.Gemini -> geminiCalls(json).firstOrNull()
        }
    }

    /**
     * The tool menu tells a model to answer with `{"tool":...}`; a provider's own function
     * call has to become that same shape, so the rest of the app never learns the difference.
     */
    fun asToolJson(calls: List<Call>): String {
        val call = calls.firstOrNull { it.name.isNotBlank() } ?: return ""
        val args = call.arguments.trim()
        val safe = if (args.startsWith("{") && args.endsWith("}")) args else "{}"
        return "{\"tool\":\"${call.name}\",\"args\":$safe}"
    }

    private data class Block(val type: String, val name: String, val args: String)

    private fun anthropicBlocks(content: JSONArray?): List<Block> {
        val out = ArrayList<Block>()
        for (i in 0 until (content?.length() ?: 0)) {
            val b = content?.optJSONObject(i) ?: continue
            out.add(
                Block(
                    b.optString("type"),
                    b.optString("name"),
                    b.optJSONObject("input")?.toString().orEmpty(),
                ),
            )
        }
        return out
    }

    private fun geminiCalls(json: JSONObject): List<Call> {
        val out = ArrayList<Call>()
        val list = json.optJSONArray("candidates") ?: return out
        for (i in 0 until list.length()) {
            val parts = list.optJSONObject(i)?.optJSONObject("content")?.optJSONArray("parts")
                ?: continue
            for (p in 0 until parts.length()) {
                val fn = parts.optJSONObject(p)?.optJSONObject("functionCall") ?: continue
                val name = fn.optString("name")
                if (name.isBlank()) continue
                out.add(Call(-1, name, fn.optJSONObject("args")?.toString().orEmpty()))
            }
        }
        return out
    }

    /**
     * True when this chunk - or a whole reply - says the model stopped because it reached
     * the output limit. That is the difference between "the answer ended" and "the answer
     * was cut off", and each provider words it differently.
     */
    fun hitTokenLimit(provider: CloudProvider, data: String): Boolean = try {
        val json = JSONObject(data)
        when (provider) {
            CloudProvider.Compatible -> json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optString("finish_reason") == "length"

            CloudProvider.Anthropic ->
                json.optString("stop_reason") == "max_tokens" ||
                    json.optJSONObject("delta")?.optString("stop_reason") == "max_tokens"

            CloudProvider.Gemini -> {
                var hit = false
                val list = json.optJSONArray("candidates")
                for (i in 0 until (list?.length() ?: 0)) {
                    if (list?.optJSONObject(i)?.optString("finishReason") == "MAX_TOKENS") hit = true
                }
                hit
            }
        }
    } catch (_: Throwable) {
        false
    }

    /** A field that may be JSON null, which optString would turn into the word "null". */
    private fun str(v: Any?): String = if (v == null || v == JSONObject.NULL) "" else v.toString()

    /** The text in one streamed chunk, or null when the chunk carries none. */
    fun delta(provider: CloudProvider, data: String): String? = try {
        val json = JSONObject(data)
        val out = when (provider) {
            CloudProvider.Compatible ->
                str(
                    json.optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("delta")?.opt("content"),
                )

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

class CloudBrain(
    /** The toolbox the model may call. Declared to the provider, so the model itself decides. */
    /** Tools to declare for one request: Control mode only, and only the ones it needs. */
    private val toolSpecs: (LlmRequest) -> List<ToolSpec> = { emptyList() },
    private val config: () -> CloudConfig,
) : Brain {

    /** Control mode is the mode that carries the phone; Chat mode declares no tools. */
    private fun toolsFor(@Suppress("UNUSED_PARAMETER") request: LlmRequest): List<ToolSpec> =
        toolSpecs(request)

    override val isReady: Boolean get() = config().isUsable

    /** Set when the provider says it stopped because the reply hit max_tokens. */
    @Volatile
    override var lastReplyTruncated: Boolean = false
        private set

    private fun markStart() {
        lastReplyTruncated = false
    }

    override val name: String
        get() = "Cloud (${config().model.ifBlank { "not set" }})"

    override fun generate(request: LlmRequest): String {
        val cfg = config()
        cfg.problem()?.let { throw IOException(it) }
        lastReplyTruncated = false
        val body = CloudRequest.body(cfg, request, stream = false, tools = toolsFor(request))
        val text = post(cfg, CloudRequest.url(cfg, stream = false), body, null)
        if (text.isBlank()) throw IOException("The cloud model sent an empty reply")
        return text
    }

    override fun generateStream(request: LlmRequest, onDelta: (String) -> Unit): String {
        val cfg = config()
        cfg.problem()?.let { throw IOException(it) }
        lastReplyTruncated = false
        val body = CloudRequest.body(cfg, request, stream = true, tools = toolsFor(request))
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
                lastReplyTruncated = CloudReply.hitTokenLimit(cfg.provider, whole)
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
        // A provider's native function call arrives in pieces (OpenAI streams the name first
        // and the arguments a few characters at a time), so it is merged by index and only
        // turned into text once the stream ends.
        val calls = LinkedHashMap<Int, CloudReply.Call>()
        var nextIndex = 0
        conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                val data = Sse.payload(line) ?: continue
                if (CloudReply.hitTokenLimit(cfg.provider, data)) lastReplyTruncated = true
                CloudReply.toolDelta(cfg.provider, data)?.let { piece ->
                    val key = if (piece.index >= 0) piece.index else nextIndex++
                    calls[key] = calls[key]?.merge(piece) ?: piece
                }
                val delta = CloudReply.delta(cfg.provider, data) ?: continue
                out.append(delta)
                onDelta(delta)
            }
        }
        if (out.isBlank() && calls.isNotEmpty()) {
            out.append(CloudReply.asToolJson(calls.values.toList()))
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
