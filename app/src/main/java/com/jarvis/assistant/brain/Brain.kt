package com.jarvis.assistant.brain

/**
 * The seam where a cloud brain can be dropped in later.
 * OfflineBrain runs Gemma on the phone; a future CloudBrain would use the same API.
 */
interface Brain {
    val isReady: Boolean
    fun generate(prompt: String): String
}
