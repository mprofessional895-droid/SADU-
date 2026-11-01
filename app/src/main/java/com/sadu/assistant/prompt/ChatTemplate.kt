package com.sadu.assistant.prompt

import com.sadu.assistant.Turn
import com.sadu.assistant.brain.ModelFamily

/**
 * How instructions and a conversation are written out for the on-device model.
 *
 * MediaPipe gets a `.task` bundle and no template engine, so Sadu writes Gemma's format itself
 * ([GemmaChatTemplate]). A `.gguf` model carries its own template in its metadata, and llama.cpp
 * can apply it ([ModelChatTemplate]) - which is how the same app gets a correct prompt for a
 * model that is not Gemma. A model without one falls back to Gemma's, so nothing breaks.
 */
interface ChatTemplate {

    fun build(instructions: String, turns: List<Turn>, text: String): String
}

/**
 * The model's own template, applied by llama.cpp. [render] is the engine's call into it and
 * returns "" when the model carries no usable template, which is when the fallback takes over.
 */
class ModelChatTemplate(
    private val render: (String, List<Turn>, String) -> String,
    /** The family the loaded model belongs to, for the fallback when its template is unusable. */
    private val family: () -> ModelFamily = { ModelFamily.Other },
) : ChatTemplate {

    override fun build(instructions: String, turns: List<Turn>, text: String): String {
        val said = text.trim()
        val rendered = runCatching { render(instructions, turns, text) }.getOrDefault("")

        // llama.cpp applied the model's own template: it is the only correct format for this
        // model, so it is taken as is. The one thing worth checking is that the message survived
        // it - a template that quietly drops the user's words is worse than no template.
        if (rendered.isNotBlank() && keeps(rendered, said)) return rendered

        val fallback = FamilyChatTemplate.build(family(), instructions, turns, text)
        // Last resort: the family format could not keep the message either, so the message is put
        // at the end verbatim rather than being lost.
        if (keeps(fallback, said)) return fallback
        return fallback + (if (fallback.endsWith("\n")) "" else "\n") + said
    }

    companion object {

        const val VERSION = "model-template-2"

        /** How much of the message has to be findable in the prompt to count as kept. */
        private const val PROOF = 24

        fun keeps(prompt: String, said: String): Boolean {
            if (said.isEmpty()) return prompt.isNotBlank()
            if (prompt.isBlank()) return false
            val proof = said.takeLast(PROOF.coerceAtMost(said.length))
            return prompt.contains(proof)
        }
    }
}
