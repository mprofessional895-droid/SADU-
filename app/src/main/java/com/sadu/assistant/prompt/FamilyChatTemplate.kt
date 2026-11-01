package com.sadu.assistant.prompt

import com.sadu.assistant.Turn
import com.sadu.assistant.brain.ModelFamily

/**
 * The fallback format for a model that carries no usable chat template of its own.
 *
 * Every family has its own control tokens; writing Gemma's markers for a Qwen or Llama model is
 * the same as sending it plain text with stray words in it. Only used when llama.cpp returned
 * nothing for the model's own template, so a correct model is never reformatted.
 */
class FamilyChatTemplate(private val family: () -> ModelFamily) : ChatTemplate {

    override fun build(instructions: String, turns: List<Turn>, text: String): String =
        build(family(), instructions, turns, text)

    companion object {

        const val VERSION = "family-template-1"

        fun build(family: ModelFamily, instructions: String, turns: List<Turn>, text: String): String =
            when (family) {
                ModelFamily.Llama -> llama(instructions, turns, text)
                ModelFamily.Qwen -> chatMl(instructions, turns, text)
                ModelFamily.Mistral -> mistral(instructions, turns, text)
                ModelFamily.Phi -> phi(instructions, turns, text)
                else -> GemmaChatTemplate.build(instructions, turns, text)
            }

        private fun llama(head: String, turns: List<Turn>, text: String): String {
            val sb = StringBuilder("<|begin_of_text|>")
            if (head.isNotBlank()) {
                sb.append("<|start_header_id|>system<|end_header_id|>\n\n")
                sb.append(head.trim()).append("<|eot_id|>")
            }
            turns.forEachIndexed { index, (said, replied) ->
                val said0 = if (index == 0) join(head, said) else said
                sb.append("<|start_header_id|>user<|end_header_id|>\n\n")
                sb.append(cap(said0)).append("<|eot_id|>")
                if (replied.isNotBlank()) {
                    sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
                    sb.append(replied.trim().take(GemmaChatTemplate.HISTORY_CAP)).append("<|eot_id|>")
                }
            }
            sb.append("<|start_header_id|>user<|end_header_id|>\n\n")
            sb.append(cap(text)).append("<|eot_id|>")
            sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
            return sb.toString()
        }

        private fun chatMl(head: String, turns: List<Turn>, text: String): String {
            val sb = StringBuilder()
            if (head.isNotBlank()) {
                sb.append("<|im_start|>system\n").append(head.trim()).append("<|im_end|>\n")
            }
            turns.forEachIndexed { index, (said, replied) ->
                val said0 = if (index == 0) join(head, said) else said
                sb.append("<|im_start|>user\n").append(cap(said0)).append("<|im_end|>\n")
                if (replied.isNotBlank()) {
                    sb.append("<|im_start|>assistant\n")
                    sb.append(replied.trim().take(GemmaChatTemplate.HISTORY_CAP)).append("<|im_end|>\n")
                }
            }
            sb.append("<|im_start|>user\n").append(cap(text)).append("<|im_end|>\n")
            sb.append("<|im_start|>assistant\n")
            return sb.toString()
        }

        private fun mistral(head: String, turns: List<Turn>, text: String): String {
            val sb = StringBuilder("<s>")
            if (turns.isEmpty()) {
                sb.append("[INST] ").append(join(head, text)).append(" [/INST]")
                return sb.toString()
            }
            turns.forEachIndexed { index, (said, replied) ->
                val said0 = if (index == 0) join(head, said) else said
                sb.append("[INST] ").append(cap(said0)).append(" [/INST] ")
                if (replied.isNotBlank()) sb.append(replied.trim().take(GemmaChatTemplate.HISTORY_CAP)).append("</s>")
            }
            sb.append("[INST] ").append(cap(text)).append(" [/INST]")
            return sb.toString()
        }

        private fun phi(head: String, turns: List<Turn>, text: String): String {
            val sb = StringBuilder()
            if (head.isNotBlank()) sb.append("<|system|>\n").append(head.trim()).append("<|end|>\n")
            turns.forEachIndexed { index, (said, replied) ->
                val said0 = if (index == 0) join(head, said) else said
                sb.append("<|user|>\n").append(cap(said0)).append("<|end|>\n")
                if (replied.isNotBlank()) {
                    sb.append("<|assistant|>\n")
                    sb.append(replied.trim().take(GemmaChatTemplate.HISTORY_CAP)).append("<|end|>\n")
                }
            }
            sb.append("<|user|>\n").append(cap(text)).append("<|end|>\n<|assistant|>\n")
            return sb.toString()
        }

        /** The instructions travel with the first user turn, as every one of these formats expects. */
        private fun join(head: String, said: String): String =
            if (head.isBlank()) said.trim() else head.trim() + "\n\n" + said.trim()

        /** The user's own words are never cut down to make room for the instructions. */
        private fun cap(said: String): String = said.trim().take(GemmaChatTemplate.MESSAGE_CAP)
    }
}
