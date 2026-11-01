package com.sadu.assistant.brain

import android.content.Context
import android.net.Uri
import com.sadu.assistant.ContextBudget
import com.sadu.assistant.Sampling
import com.sadu.assistant.Turn
import java.io.File

/**
 * The second on-device engine: llama.cpp, running a `.gguf` file.
 *
 * Why it is here: MediaPipe exposes no repetition penalty, so a small model loops and pads and
 * there is nothing to do about it beyond turning the temperature down. llama.cpp has the penalty,
 * and PocketPal - which people compare us to - is llama.cpp. Same weights, same engine.
 *
 * Everything that needs a phone is behind [native] calls in `cpp/llama_jni.cpp`; this file is the
 * bookkeeping around them: one model, one context, one reply at a time.
 *
 * [isAvailable] is false on any build that did not ship the native library (no NDK at build time),
 * and then Settings says so and MediaPipe keeps the job.
 */
class LlamaEngine(private val ctx: Context) : Engine {

    private var handle: Long = 0L
    private var loadedTokens = 0

    /** What the loaded file says it is, for the status line in Settings. */
    var info: Gguf.Info? = null
        private set

    @Volatile
    private var writing = false

    override val name: String get() = "On-device llama.cpp"

    override val isReady: Boolean get() = handle != 0L

    override val contextTokens: Int get() = loadedTokens

    override val isWriting: Boolean get() = writing

    private val modelFile: File get() = File(ctx.filesDir, ModelName)

    override fun isModelPresent(): Boolean = modelFile.exists() && modelFile.length() > MinModelBytes

    override fun modelBytes(): Long =
        runCatching { if (modelFile.exists()) modelFile.length() else 0L }.getOrDefault(0L)

    @Synchronized
    override fun load(contextTokens: Int): Int {
        requireNative()
        val tokens = ContextBudget.clamp(contextTokens)
        if (handle != 0L && loadedTokens == tokens) return loadedTokens
        close()
        if (!isModelPresent()) throw IllegalStateException("No .gguf model has been imported yet.")

        val opened = nativeLoad(modelFile.absolutePath, tokens, threads(), Batch)
        if (opened == 0L) {
            throw IllegalStateException(
                nativeLastError().ifEmpty { "llama.cpp could not load that model file." },
            )
        }
        handle = opened
        loadedTokens = tokens
        info = (Gguf.inspect(modelFile) as? Gguf.Check.Ok)?.info
        return loadedTokens
    }

    override fun generate(prompt: String, sampling: Sampling, maxNewTokens: Int): String =
        run(prompt, sampling, maxNewTokens, null)

    override fun generateStream(
        prompt: String,
        sampling: Sampling,
        maxNewTokens: Int,
        onDelta: (String) -> Unit,
    ): String = run(prompt, sampling, maxNewTokens, onDelta)

    /**
     * A turn the model may only answer inside [grammar], ending at the first of [stops]. The engine
     * is cancelled the moment a stop mark lands, so a runaway tail costs nothing.
     */
    fun generateShaped(
        prompt: String,
        grammar: String?,
        stops: List<String>,
        sampling: Sampling,
        maxNewTokens: Int,
        onDelta: ((String) -> Unit)? = null,
    ): String = run(prompt, sampling, maxNewTokens, onDelta, grammar, stops)

    private fun run(
        prompt: String,
        sampling: Sampling,
        maxNewTokens: Int,
        onDelta: ((String) -> Unit)?,
        grammar: String? = null,
        stops: List<String> = emptyList(),
    ): String {
        val current = handle
        if (current == 0L) throw IllegalStateException("No .gguf model is loaded.")

        writing = true
        val marks = if (stops.isEmpty()) null else StopMarks(stops)
        var stopped = false
        try {
            val sink = object : TokenSink {
                override fun onToken(piece: String) {
                    if (piece.isEmpty()) return
                    val text = if (marks == null) piece else marks.take(piece)
                    if (text.isNotEmpty()) onDelta?.invoke(text)
                    if (marks?.isStopped == true && !stopped) {
                        stopped = true
                        nativeCancel(current)
                    }
                }
            }
            val reply = nativeGenerate(
                current,
                prompt,
                sampling.temperature,
                sampling.topK,
                sampling.topP,
                sampling.minP,
                sampling.repeatPenalty,
                Sampling.RepeatLastN,
                maxNewTokens.coerceAtLeast(1),
                grammar,
                sink,
            )
            if (reply.isEmpty() && !writingCancelled && !stopped) {
                throw IllegalStateException(
                    nativeLastError().ifEmpty { "The model did not write anything back." },
                )
            }
            return if (marks == null) reply else marks.truncate(reply)
        } finally {
            writing = false
            writingCancelled = false
        }
    }

    private var writingCancelled = false

    override fun cancel(): Boolean {
        val current = handle
        if (current == 0L || !writing) return false
        writingCancelled = true
        nativeCancel(current)
        return true
    }

    @Synchronized
    override fun close() {
        val current = handle
        handle = 0L
        loadedTokens = 0
        if (current != 0L) runCatching { nativeFree(current) }
    }

    /**
     * The model's own chat template, applied by llama.cpp. "" when the model carries none, which
     * is what makes [ModelChatTemplate] fall back to Gemma's.
     */
    fun applyTemplate(instructions: String, turns: List<Turn>, text: String): String {
        val current = handle
        if (current == 0L) return ""

        val roles = ArrayList<String>(turns.size * 2 + 2)
        val contents = ArrayList<String>(turns.size * 2 + 2)
        if (instructions.isNotBlank()) {
            roles += ROLE_SYSTEM
            contents += instructions
        }
        turns.forEach { (said, replied) ->
            if (said.isNotBlank()) {
                roles += ROLE_USER
                contents += said
            }
            if (replied.isNotBlank()) {
                roles += ROLE_ASSISTANT
                contents += replied
            }
        }
        roles += ROLE_USER
        contents += text
        return runCatching { nativeApplyTemplate(current, roles.toTypedArray(), contents.toTypedArray()) }
            .getOrDefault("")
    }

    /** Copies a chosen `.gguf` into the app's own storage, checking it belongs there first. */
    override fun importModel(uri: Uri, onProgress: (Long) -> Unit) {
        val input = ctx.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not open the file you picked")

        val tmp = File(ctx.filesDir, "$ModelName.tmp")
        input.use { src ->
            val head = ByteArray(Gguf.PREFIX_BYTES)
            var headLength = 0
            while (headLength < head.size) {
                val n = src.read(head, headLength, head.size - headLength)
                if (n <= 0) break
                headLength += n
            }
            when (val check = Gguf.inspect(head.copyOf(headLength))) {
                is Gguf.Check.Bad -> throw IllegalStateException(check.reason)
                is Gguf.Check.Ok -> Unit
            }

            tmp.outputStream().use { out ->
                out.write(head, 0, headLength)
                var total = headLength.toLong()
                onProgress(total)
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    val n = src.read(buffer)
                    if (n <= 0) break
                    out.write(buffer, 0, n)
                    total += n
                    onProgress(total)
                }
            }
        }

        if (tmp.length() < MinModelBytes) {
            tmp.delete()
            throw IllegalStateException("That file is too small to be a model.")
        }
        close()
        modelFile.delete()
        if (!tmp.renameTo(modelFile)) throw IllegalStateException("Could not save the model file")
        info = (Gguf.inspect(modelFile) as? Gguf.Check.Ok)?.info
    }

    private fun requireNative() {
        if (!isAvailable) throw IllegalStateException(Unavailable)
    }

    /** How many threads the model runs on: the big cores, not every core the phone reports. */
    private fun threads(): Int = CpuTopology.threads()

    /** llama.cpp's own numbers for the last reply: prompt speed and generation speed apart. */
    fun lastPerf(): String = runCatching { nativeLastPerf(handle) }.getOrDefault("")

    /** The token sink the native side calls back into. */
    private interface TokenSink {
        fun onToken(piece: String)
    }

    private external fun nativeLoad(path: String, contextTokens: Int, threads: Int, batch: Int): Long

    private external fun nativeFree(handle: Long)

    private external fun nativeCancel(handle: Long)

    private external fun nativeLastError(): String

    private external fun nativeLastPerf(handle: Long): String

    private external fun nativeApplyTemplate(handle: Long, roles: Array<String>, contents: Array<String>): String

    private external fun nativeGenerate(
        handle: Long,
        prompt: String,
        temperature: Float,
        topK: Int,
        topP: Float,
        minP: Float,
        repeatPenalty: Float,
        repeatLastN: Int,
        maxNewTokens: Int,
        grammar: String?,
        sink: TokenSink?,
    ): String

    companion object {

        const val Library = "sadu_llama"

        const val ModelName = "model.gguf"

        /** Nothing under this is a model; the smallest useful quantised 1B is a hundred times it. */
        const val MinModelBytes = 8L * 1024 * 1024

        const val Batch = 512

        private const val ROLE_SYSTEM = "system"
        private const val ROLE_USER = "user"
        private const val ROLE_ASSISTANT = "assistant"

        const val Unavailable =
            "This build has no llama.cpp library in it, so it cannot run a .gguf file. " +
                "Use the MediaPipe engine, or build the app with the Android NDK."

        /** True when this APK really carries the native engine. */
        val isAvailable: Boolean by lazy {
            runCatching { System.loadLibrary(Library) }.isSuccess
        }
    }
}
