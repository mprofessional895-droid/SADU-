package com.jarvis.assistant

import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.File

/**
 * Runs the Gemma model on the phone. No internet needed.
 *
 * Two things worth knowing about this layer, both of them ceilings rather than choices:
 * MediaPipe asks for the sampling it is given and nothing else - there is no repetition
 * penalty to damp a small model's loops - and `maxTokens` covers the prompt as well as the
 * reply, so the context window and the answer share one budget.
 */
open class LlmEngine(private val ctx: Context) {

    private var llm: LlmInference? = null
    private var wantsTokens = ContextBudget.Default
    private var loadedTokens = 0

    /** The session that is writing right now, so it can be stopped. */
    @Volatile
    private var active: LlmInferenceSession? = null

    @Volatile
    private var cancelled = false

    /** The context window the loaded model actually accepted. */
    val contextTokens: Int get() = loadedTokens

    private val modelFile: File get() = File(ctx.filesDir, "model.task")

    fun isModelPresent(): Boolean = modelFile.exists() && modelFile.length() > 50_000_000L

    /** Size of the imported model on disk, for the status screen. */
    fun modelBytes(): Long = runCatching {
        if (modelFile.exists()) modelFile.length() else 0L
    }.getOrDefault(0L)

    val isLoaded: Boolean get() = llm != null

    @Synchronized
    fun load(contextTokens: Int = ContextBudget.Default): Int {
        wantsTokens = ContextBudget.clamp(contextTokens)
        if (llm != null && loadedTokens == wantsTokens) return loadedTokens
        if (llm != null) close()
        val accepted = tryLoad(wantsTokens) ?: tryLoad(ContextBudget.Min)
            ?: throw IllegalStateException("The model could not be loaded")
        loadedTokens = accepted
        return accepted
    }

    /** Builds the engine at one window size, or null when the model refuses it. */
    private fun tryLoad(tokens: Int): Int? = try {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(tokens)
            // The engine caps top-k at 40 unless it is told otherwise, which would quietly
            // override the sampler this app asks for.
            .setMaxTopK(Sampling.MaxTopK)
            .setPreferredBackend(LlmInference.Backend.CPU)
            .build()
        llm = LlmInference.createFromOptions(ctx, options)
        tokens
    } catch (_: Throwable) {
        llm = null
        null
    }

    /** One fresh session per reply, with the sampling the caller asked for. */
    private fun open(engine: LlmInference, sampling: Sampling): LlmInferenceSession {
        val opts = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(sampling.topK)
            .setTopP(sampling.topP)
            .setTemperature(sampling.temperature)
            .build()
        return LlmInferenceSession.createFromOptions(engine, opts)
    }

    open fun generate(prompt: String, sampling: Sampling = Sampling.chat()): String {
        val engine = llm ?: throw IllegalStateException("Model is not loaded yet")
        val session = open(engine, sampling)
        try {
            session.addQueryChunk(prompt)
            return session.generateResponse()
        } finally {
            session.close()
        }
    }

    /**
     * The same answer, but [onDelta] is handed each new piece as the model writes it, which
     * is what makes an on-device reply land on screen word by word instead of all at once.
     */
    open fun generateStream(
        prompt: String,
        sampling: Sampling = Sampling.chat(),
        onDelta: (String) -> Unit = {},
    ): String {
        val engine = llm ?: throw IllegalStateException("Model is not loaded yet")
        cancelled = false
        val session = open(engine, sampling)
        active = session
        var latest = ""
        try {
            session.addQueryChunk(prompt)
            val future = session.generateResponseAsync { partial, _ ->
                // MediaPipe hands over the text so far, not the newest piece, so the new
                // part is whatever has been added since the last call either way.
                val soFar = partial.orEmpty()
                if (soFar.isEmpty()) return@generateResponseAsync
                if (soFar.length > latest.length && soFar.startsWith(latest)) {
                    onDelta(soFar.substring(latest.length))
                    latest = soFar
                } else if (soFar != latest && !latest.endsWith(soFar)) {
                    onDelta(soFar)
                    latest += soFar
                }
            }
            return try {
                future.get()
            } catch (e: Throwable) {
                // A stopped answer keeps whatever the model had already written.
                if (cancelled && latest.isNotEmpty()) latest else throw e
            }
        } finally {
            active = null
            runCatching { session.close() }
        }
    }

    /** Stops the answer that is being written, if any. True when there was one. */
    open fun cancel(): Boolean {
        val session = active ?: return false
        cancelled = true
        runCatching { session.cancelGenerateResponseAsync() }
        return true
    }

    val isWriting: Boolean get() = active != null

    /** Copies the chosen model file into the app's private storage (one time). */
    fun importModel(uri: Uri, onProgress: (Long) -> Unit) {
        val tmp = File(ctx.filesDir, "model.tmp")
        val input = ctx.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not open the file you picked")
        input.use { src ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(1 shl 20)
                var total = 0L
                while (true) {
                    val n = src.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    total += n
                    onProgress(total)
                }
            }
        }
        synchronized(this) {
            llm?.close()
            llm = null
        }
        modelFile.delete()
        if (!tmp.renameTo(modelFile)) throw IllegalStateException("Could not save the model file")
    }

    open fun close() {
        try {
            llm?.close()
        } catch (_: Throwable) {
        }
        llm = null
    }

    companion object {
        /** Kept for the wording in Settings: what Gemma's own card recommends. */
        const val ChatTemperature = Sampling.DefaultTemperature
    }
}
