package com.sadu.assistant.brain

import com.sadu.assistant.ToolSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Gemma function-calling dialect, checked without a model: the exact text the model was
 * trained to write, the broken shapes it writes when it is confused, and the guard that stops
 * a 270M model repeating itself to the token limit.
 */
class FunctionGemmaTest {

    private val specs = listOf(
        ToolSpec("open_app", "app", "open an installed app by name"),
        ToolSpec("whatsapp", "contact, message", "write a WhatsApp message and open the chat"),
    )

    @Test
    fun theDocumentedCallIsRead() {
        val call = FunctionGemma.read(
            "<start_function_call>call:open_app{app:<escape>youtube<escape>}<end_function_call>",
        )
        assertEquals("open_app", call?.name)
        assertEquals(mapOf("app" to "youtube"), call?.args)
    }

    @Test
    fun twoArgumentsKeepTheirNames() {
        val call = FunctionGemma.read(
            "<start_function_call>call:whatsapp{contact:<escape>mummy<escape>, " +
                "message:<escape>main late aa raha hoon<escape>}<end_function_call>",
        )
        assertEquals("whatsapp", call?.name)
        assertEquals("mummy", call?.args?.get("contact"))
        assertEquals("main late aa raha hoon", call?.args?.get("message"))
    }

    @Test
    fun aMissingUnderscoreIsStillRead() {
        val call = FunctionGemma.read(
            "<startfunctioncall>call(\"whatsapp\", \"group\")<endfunctioncall>",
        )
        assertEquals("whatsapp", call?.name)
        assertEquals("group", call?.args?.get("value"))
    }

    @Test
    fun theParenthesisVariantIsRead() {
        val call = FunctionGemma.read("call(\"open_app\", \"camera\")")
        assertEquals("open_app", call?.name)
        assertEquals("camera", call?.args?.get("value"))
    }

    @Test
    fun anUncutCallIsStillRead() {
        val call = FunctionGemma.read("<start_function_call>call:open_app{app:<escape>youtube")
        assertEquals("open_app", call?.name)
        assertEquals("youtube", call?.args?.get("app"))
    }

    @Test
    fun plainTalkIsNotACall() {
        assertNull(FunctionGemma.read("Sure, opening YouTube for you."))
        assertNull(FunctionGemma.read(""))
    }

    @Test
    fun theToolBlockCarriesEveryTool() {
        val block = FunctionGemma.tools(specs)
        assertTrue(block.contains("<start_function_declaration>declaration:open_app{"))
        assertTrue(block.contains("<start_function_declaration>declaration:whatsapp{"))
        assertTrue("arguments are declared", block.contains("contact:{"))
        assertTrue("the trained header", block.contains("function calling"))
        assertTrue("each declaration is closed", block.endsWith("<end_function_declaration>"))
        assertFalse("not the JSON shape", block.contains("\"name\""))
    }

    @Test
    fun theStopMarksAreTheOnesTheModelWrites() {
        assertTrue(FunctionGemma.STOPS.contains("<end_function_call>"))
        assertTrue(FunctionGemma.STOPS.contains("<end_of_turn>"))
    }

    @Test
    fun theLoopIsCutBeforeItReachesTheScreen() {
        val loop = "<start_function_call>call:open_app{app:<escape>youtube<escape>}<end_function_call>" +
            "<escape>".repeat(200)
        val cut = FunctionGemma.withoutLoop(loop)
        assertTrue(cut.length < loop.length)
        assertTrue(cut.endsWith("<end_function_call>"))
    }

    @Test
    fun noSpecialTokenSurvivesCleaning() {
        val clean = FunctionGemma.clean(
            "<start_function_call>call:open_app{app:<escape>youtube<escape>}<end_function_call>",
        )
        assertFalse(clean.contains("<"))
        assertTrue(clean.contains("open_app"))
    }

    @Test
    fun theResponseIsInTheTrainedShape() {
        val out = FunctionGemma.response("open_app", "Opened YouTube.")
        assertTrue(out.startsWith("<start_function_response>response:open_app{"))
        assertTrue(out.endsWith("<end_function_response>"))
        assertTrue(out.contains("Opened YouTube."))
        assertEquals("open_app", FunctionGemma.read("""<start_function_call>call:open_app{app:<escape>youtube<escape>}<end_function_call>""")?.name)
    }

    @Test
    fun aHinglishPostpositionIsNotPartOfTheName() {
        assertEquals("shubham", FunctionGemma.tidyArg("shubham ko"))
        assertEquals("mummy", FunctionGemma.tidyArg(" mummy se "))
        assertEquals("Rahul Sharma", FunctionGemma.tidyArg("Rahul Sharma"))
        assertEquals("papa", FunctionGemma.tidyArg("papa ko"))
    }

    @Test
    fun onlyTheGemmaFunctionModelIsClaimed() {
        assertTrue(FunctionGemma.handles("functiongemma-270m-it-Q8_0.gguf"))
        assertTrue(FunctionGemma.handles("Function-Gemma-270M.gguf"))
        assertFalse(FunctionGemma.handles("gemma-3-270m-it-Q8_0.gguf"))
        assertFalse(FunctionGemma.handles("qwen2.5-1.5b-instruct-q4.gguf"))
    }
}
