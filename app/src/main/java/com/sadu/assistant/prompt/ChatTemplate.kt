package com.sadu.assistant.prompt

import com.sadu.assistant.Turn

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
) : ChatTemplate {

    override fun build(instructions: String, turns: List<Turn>, text: String): String {
        val rendered = runCatching { render(instructions, turns, text) }.getOrDefault("")
        return rendered.ifEmpty { GemmaChatTemplate.build(instructions, turns, text) }
    }

    companion object {

        const val VERSION = "model-template-1"
    }
}
