package com.jarvis.assistant.brain

import com.jarvis.assistant.LlmEngine
import com.jarvis.assistant.Mode
import com.jarvis.assistant.Prompt

/** Wraps the on-device Gemma engine behind the Brain interface. */
class OfflineBrain(private val engine: LlmEngine) : Brain {

    override val isReady: Boolean
        get() = runCatching { engine.isLoaded }.getOrDefault(false)

    override val name: String get() = "On-device Gemma"

    override fun generate(request: LlmRequest): String {
        val temperature =
            if (request.mode == Mode.Chat) LlmEngine.ChatTemperature else LlmEngine.CommandTemperature
        return engine.generate(
            Prompt.local(request.system, request.turns, request.user),
            temperature,
        )
    }
}
