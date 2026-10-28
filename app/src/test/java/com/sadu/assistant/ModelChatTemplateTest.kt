package com.sadu.assistant

import com.sadu.assistant.prompt.GemmaChatTemplate
import com.sadu.assistant.prompt.ModelChatTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A `.gguf` model carries its own chat template, and llama.cpp applies it. When there is none -
 * an unusual file, or an engine that never got that far - the app must not send a prompt with no
 * structure in it, so Gemma's template takes over.
 */
class ModelChatTemplateTest {

    private val turns = listOf(Turn("hi", "hello"))

    @Test
    fun theModelsOwnRenderingIsUsedWhenThereIsOne() {
        val template = ModelChatTemplate { _, _, _ -> "MODEL FORMAT: hi / hello / again" }
        assertEquals("MODEL FORMAT: hi / hello / again", template.build("INSTRUCTIONS", turns, "again"))
    }

    @Test
    fun theInstructionsAndTurnsAreHandedToTheModel() {
        var seen: Triple<String, List<Turn>, String>? = null
        val template = ModelChatTemplate { instructions, given, text ->
            seen = Triple(instructions, given, text)
            "rendered"
        }
        template.build("INSTRUCTIONS", turns, "again")
        assertEquals("INSTRUCTIONS", seen?.first)
        assertEquals(turns, seen?.second)
        assertEquals("again", seen?.third)
    }

    @Test
    fun gemmasTemplateTakesOverWhenTheModelHasNone() {
        val template = ModelChatTemplate { _, _, _ -> "" }
        val prompt = template.build("INSTRUCTIONS", turns, "again")
        assertEquals(GemmaChatTemplate.build("INSTRUCTIONS", turns, "again"), prompt)
        assertTrue(prompt.startsWith("<start_of_turn>user\nINSTRUCTIONS"))
        assertTrue(prompt.endsWith("<start_of_turn>model\n"))
    }

    @Test
    fun aFailingTemplateDoesNotBreakTheReply() {
        val template = ModelChatTemplate { _, _, _ -> throw IllegalStateException("bad template") }
        val prompt = template.build("INSTRUCTIONS", emptyList(), "hello")
        assertEquals(GemmaChatTemplate.build("INSTRUCTIONS", emptyList(), "hello"), prompt)
    }
}
