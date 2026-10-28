package com.jarvis.assistant.brain

import com.jarvis.assistant.Mode
import com.jarvis.assistant.ReplyLength
import com.jarvis.assistant.Sampling
import com.jarvis.assistant.prompt.ChatTemplate
import com.jarvis.assistant.prompt.GemmaChatTemplate

/**
 * Wraps an on-device engine behind the Brain interface.
 *
 * Nothing here knows whether the engine is MediaPipe or llama.cpp - it is handed an [Engine], the
 * [ChatTemplate] that matches it, the sampling the mode asks for and how long the reply may be.
 * A `.task` model gets Gemma's frozen template, a `.gguf` model gets its own; either way the
 * result is one prompt with the model's next words at the end.
 *
 * The sampling comes from the mode: Chat mode asks for the temperature the user chose in Settings,
 * Control mode for the steadier one a JSON tool call needs. Replies stream, so the screen fills
 * word by word the way a chat app should.
 */
class OfflineBrain(
    private val engine: Engine,
    private val template: ChatTemplate = GemmaChatTemplate,
    private val sampling: (Mode) -> Sampling = { mode ->
        if (mode == Mode.Chat) Sampling.chat() else Sampling.Command
    },
    private val length: () -> ReplyLength = { ReplyLength.Default },
) : Brain {

    override val isReady: Boolean
        get() = runCatching { engine.isReady }.getOrDefault(false)

    override val name: String get() = engine.name

    override fun generate(request: LlmRequest): String =
        engine.generate(promptFor(request), sampling(request.mode), length().tokens)

    override fun generateStream(request: LlmRequest, onDelta: (String) -> Unit): String =
        engine.generateStream(promptFor(request), sampling(request.mode), length().tokens, onDelta)

    /** Stops the answer being written. True when there was one to stop. */
    fun cancel(): Boolean = engine.cancel()

    private fun promptFor(request: LlmRequest): String =
        template.build(request.system, request.turns, request.user)
}
