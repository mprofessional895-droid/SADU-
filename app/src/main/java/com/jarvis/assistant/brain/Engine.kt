package com.jarvis.assistant.brain

import android.net.Uri
import com.jarvis.assistant.Sampling

/**
 * What the app needs from an on-device model runner, and nothing more.
 *
 * There are two: MediaPipe running a `.task` bundle, and llama.cpp running a `.gguf` file. Both
 * wrap into [OfflineBrain], so everything above this line - Chat mode, Control mode, the
 * conversation, the settings - is written once and does not know which one answered.
 */
interface Engine {

    /** Shown next to a reply, e.g. "On-device Gemma (MediaPipe)". */
    val name: String

    val isReady: Boolean

    /** The context window the loaded model actually accepted. 0 when nothing is loaded. */
    val contextTokens: Int

    val isWriting: Boolean get() = false

    /** True when the model file this engine needs is already on the phone. */
    fun isModelPresent(): Boolean

    /** How big that file is, for the status line in Settings. */
    fun modelBytes(): Long

    /** Copies the chosen model file into the app's own storage. Happens once. */
    fun importModel(uri: Uri, onProgress: (Long) -> Unit)

    /** Loads (or reloads) the model. Returns the context window in tokens. */
    fun load(contextTokens: Int): Int

    /**
     * [maxNewTokens] is how long the reply may be. llama.cpp stops at it; MediaPipe has no output
     * cap of its own and already shares one budget between prompt and reply, so it ignores it.
     */
    fun generate(prompt: String, sampling: Sampling, maxNewTokens: Int = 0): String

    fun generateStream(
        prompt: String,
        sampling: Sampling,
        maxNewTokens: Int = 0,
        onDelta: (String) -> Unit,
    ): String

    /** Stops the reply being written. True when there was one to stop. */
    fun cancel(): Boolean

    fun close()
}

/**
 * Which on-device engine to run.
 *
 * Both are kept on the phone at once: the settings decide, and switching back is instant because
 * nothing was removed to make room.
 */
enum class LocalEngine(val key: String, val label: String, val blurb: String) {

    MediaPipe(
        key = "mediapipe",
        label = "MediaPipe (.task)",
        blurb = "The engine Jarvis has always used. Needs a .task model file.",
    ),

    Llama(
        key = "llama",
        label = "llama.cpp (.gguf)",
        blurb = "Runs .gguf files, the same engine PocketPal uses. Adds a repetition penalty, so a " +
            "small model repeats itself less.",
    );

    companion object {

        val Default = MediaPipe

        fun of(key: String?): LocalEngine = entries.firstOrNull { it.key == key } ?: Default
    }
}
