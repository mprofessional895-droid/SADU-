package com.sadu.assistant.brain

import com.sadu.assistant.Mode
import com.sadu.assistant.ReplyLength
import com.sadu.assistant.Sampling
import com.sadu.assistant.prompt.ChatTemplate
import com.sadu.assistant.prompt.GemmaChatTemplate

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
    /** A ceiling for this turn, from free memory; never longer than the setting. */
    private val cap: () -> Int = { Int.MAX_VALUE },
) : Brain {

    override val isReady: Boolean
        get() = runCatching { engine.isReady }.getOrDefault(false)

    override val name: String get() = engine.name

    override fun generate(request: LlmRequest): String =
        engine.generate(promptFor(request), sampling(request.mode), budget())

    override fun generateStream(request: LlmRequest, onDelta: (String) -> Unit): String =
        engine.generateStream(promptFor(request), sampling(request.mode), budget(), onDelta)

    /** The reply length from Settings, shortened when the phone is short of memory. */
    private fun budget(): Int = minOf(length().tokens, cap()).coerceAtLeast(64)

    /** Stops the answer being written. True when there was one to stop. */
    fun cancel(): Boolean = engine.cancel()

    private fun promptFor(request: LlmRequest): String =
        template.build(request.system, request.turns, request.user)
}
