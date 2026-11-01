package com.sadu.assistant.brain

import java.util.Locale

/**
 * Which model family is loaded, so the prompt can be written in that family's own format.
 *
 * A `.gguf` usually carries its chat template in the metadata and llama.cpp applies it. When the
 * metadata is missing or unusable, guessing Gemma's markers for a Llama or Qwen model produces a
 * prompt the model reads as plain text - it answers badly, not loudly. So the family decides the
 * fallback format instead of one format being forced on everything.
 */
enum class ModelFamily(val label: String, val markers: List<String>) {

    Gemma("Gemma", listOf("<start_of_turn>", "<end_of_turn>")),
    FunctionGemma("FunctionGemma", listOf("<start_function_call>", "<end_function_call>")),
    Llama("Llama", listOf("<|start_header_id|>", "<|end_header_id|>")),
    Qwen("Qwen", listOf("<|im_start|>", "<|im_end|>")),
    Mistral("Mistral", listOf("[INST]", "[/INST]")),
    Phi("Phi", listOf("<|user|>", "<|assistant|>")),
    Other("Generic", emptyList());

    companion object {

        /**
         * Reads the family out of a model description, a file name, or both - whatever the app
         * has. FunctionGemma is checked before Gemma because the name contains it.
         */
        fun of(vararg hints: String?): ModelFamily {
            val text = hints.filterNotNull().joinToString(" ").lowercase(Locale.US)
            if (text.isBlank()) return Other
            return when {
                text.contains("functiongemma") || text.contains("function-gemma") -> FunctionGemma
                text.contains("gemma") -> Gemma
                text.contains("qwen") -> Qwen
                text.contains("mistral") || text.contains("mixtral") || text.contains("devstral") -> Mistral
                text.contains("llama") || text.contains("tinyllama") -> Llama
                text.contains("phi-") || text.contains("phi2") || text.contains("phi3") ||
                    text.contains("phi4") -> Phi
                else -> Other
            }
        }

        /** True when the text already carries this model's own control markers. */
        fun carriesMarkers(family: ModelFamily, text: String): Boolean =
            family.markers.isNotEmpty() && family.markers.all { text.contains(it) }
    }
}
