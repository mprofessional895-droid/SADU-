package com.sadu.assistant

import com.sadu.assistant.brain.ModelFamily
import com.sadu.assistant.prompt.FamilyChatTemplate
import com.sadu.assistant.prompt.ModelChatTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelFamilyTest {

    @Test
    fun theFamilyIsReadFromTheNameOrTheDescription() {
        assertEquals(ModelFamily.Gemma, ModelFamily.of("gemma-3-1b-it-Q4_K_M.gguf"))
        assertEquals(ModelFamily.FunctionGemma, ModelFamily.of("functiongemma-270m-it.gguf"))
        assertEquals(ModelFamily.Qwen, ModelFamily.of("Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"))
        assertEquals(ModelFamily.Llama, ModelFamily.of("Llama-3.2-1B-Instruct.gguf"))
        assertEquals(ModelFamily.Mistral, ModelFamily.of(null, "Mistral 7B Instruct v0.2"))
        assertEquals(ModelFamily.Phi, ModelFamily.of("phi-3-mini-4k-instruct.gguf"))
        assertEquals(ModelFamily.Other, ModelFamily.of("mystery-model.gguf"))
        assertEquals(ModelFamily.Other, ModelFamily.of(null, null))
    }

    @Test
    fun eachFamilyGetsItsOwnMarkersAndNeverGemmasByAccident() {
        val turns = listOf(Turn("hi", "hello"))
        val qwen = FamilyChatTemplate.build(ModelFamily.Qwen, "SYSTEM", turns, "again")
        assertTrue(qwen.contains("<|im_start|>system\nSYSTEM<|im_end|>"))
        assertTrue(qwen.endsWith("<|im_start|>assistant\n"))
        assertFalse(qwen.contains("<start_of_turn>"))

        val llama = FamilyChatTemplate.build(ModelFamily.Llama, "SYSTEM", turns, "again")
        assertTrue(llama.startsWith("<|begin_of_text|>"))
        assertTrue(llama.contains("<|start_header_id|>user<|end_header_id|>"))

        val mistral = FamilyChatTemplate.build(ModelFamily.Mistral, "SYSTEM", turns, "again")
        assertTrue(mistral.contains("[INST]"))
        assertTrue(mistral.endsWith("[/INST]"))
    }

    @Test
    fun anUnknownFamilyStillGetsAStructuredPrompt() {
        val prompt = FamilyChatTemplate.build(ModelFamily.Other, "SYSTEM", emptyList(), "again")
        assertTrue(prompt.startsWith("<start_of_turn>user"))
        assertTrue(prompt.contains("again"))
    }

    @Test
    fun theMessageSurvivesEveryFamily() {
        val said = "shubham ko message karde ki hello"
        for (family in ModelFamily.values()) {
            val prompt = FamilyChatTemplate.build(family, "SYSTEM", listOf(Turn("a", "b")), said)
            assertTrue("$family dropped the message", prompt.contains(said))
        }
    }

    @Test
    fun aTemplateThatDropsTheMessageFallsBackAndStillKeepsIt() {
        val template = ModelChatTemplate(
            { _, _, _ -> "<start_of_turn>user\nsomething else<end_of_turn>" },
            { ModelFamily.Qwen },
        )
        val prompt = template.build("SYSTEM", emptyList(), "kya haal hai")
        assertTrue(prompt.contains("kya haal hai"))
        assertTrue(prompt.contains("<|im_start|>"))
    }
}
