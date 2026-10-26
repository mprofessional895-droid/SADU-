package com.jarvis.assistant.brain

import com.jarvis.assistant.LlmEngine

/** Wraps the on-device Gemma engine behind the Brain interface. */
class OfflineBrain(private val engine: LlmEngine) : Brain {
    override val isReady: Boolean
        get() = runCatching { engine.isLoaded }.getOrDefault(false)

    override fun generate(prompt: String): String = engine.generate(prompt)
}
