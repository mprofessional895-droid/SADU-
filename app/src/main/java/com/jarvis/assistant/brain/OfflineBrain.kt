package com.jarvis.assistant.brain

import com.jarvis.assistant.LlmEngine
import com.jarvis.assistant.Mode
import com.jarvis.assistant.Prompt
import com.jarvis.assistant.Sampling

/**
 * Wraps the on-device Gemma engine behind the Brain interface.
 *
 * The sampling comes from the mode: Chat mode asks for the temperature the user chose in
 * Settings, Control mode for the steadier one a JSON tool call needs. Replies stream, so the
 * screen fills word by word the way a chat app should.
 */
class OfflineBrain(
    private val engine: LlmEngine,
    private val sampling: (Mode) -> Sampling = { mode ->
        if (mode == Mode.Chat) Sampling.chat() else Sampling.Command
    },
) : Brain {

    override val isReady: Boolean
        get() = runCatching { engine.isLoaded }.getOrDefault(false)

    override val name: String get() = "On-device Gemma"

    override fun generate(request: LlmRequest): String =
        engine.generate(promptFor(request), sampling(request.mode))

    override fun generateStream(request: LlmRequest, onDelta: (String) -> Unit): String =
        engine.generateStream(promptFor(request), sampling(request.mode), onDelta)

    /** Stops the answer being written. True when there was one to stop. */
    fun cancel(): Boolean = engine.cancel()

    private fun promptFor(request: LlmRequest): String =
        Prompt.local(request.system, request.turns, request.user)
}
