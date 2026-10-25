package com.jarvis.assistant

import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

/** Runs the Gemma model on the phone. No internet needed. */
class LlmEngine(private val ctx: Context) {
    private var llm: LlmInference? = null

    private val modelFile: File get() = File(ctx.filesDir, "model.task")

    fun isModelPresent(): Boolean = modelFile.exists() && modelFile.length() > 50_000_000L

    @Synchronized
    fun load() {
        if (llm != null) return
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(1024)
            .setPreferredBackend(LlmInference.Backend.CPU)
            .build()
        llm = LlmInference.createFromOptions(ctx, options)
    }

    fun generate(prompt: String): String {
        val engine = llm ?: throw IllegalStateException("Model is not loaded yet")
        return engine.generateResponse(prompt)
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
