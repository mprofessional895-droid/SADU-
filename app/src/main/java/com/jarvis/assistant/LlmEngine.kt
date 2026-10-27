package com.jarvis.assistant

import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.File

/** Runs the Gemma model on the phone. No internet needed. */
class LlmEngine(private val ctx: Context) {
    private var llm: LlmInference? = null
    private var wantsTokens = ContextBudget.Default
    private var loadedTokens = 0

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
            .setPreferredBackend(LlmInference.Backend.CPU)
            .build()
        llm = LlmInference.createFromOptions(ctx, options)
        tokens
    } catch (_: Throwable) {
        llm = null
        null
    }

    /**
     * One fresh chat session per reply, with Google's recommended Gemma sampling
     * (top-k 64, top-p 0.95) and a calm temperature so a small model stays on topic.
     */
    fun generate(prompt: String): String {
        val engine = llm ?: throw IllegalStateException("Model is not loaded yet")
        val opts = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(64)
            .setTopP(0.95f)
            .setTemperature(0.6f)
            .build()
        val session = LlmInferenceSession.createFromOptions(engine, opts)
        try {
            session.addQueryChunk(prompt)
            return session.generateResponse()
        } finally {
            session.close()
        }
    }

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

    fun close() {
        try {
            llm?.close()
        } catch (_: Throwable) {
        }
        llm = null
    }
}
