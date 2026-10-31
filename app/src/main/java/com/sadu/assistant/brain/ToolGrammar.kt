package com.sadu.assistant.brain

import com.sadu.assistant.ToolSpec

/**
 * A GBNF grammar that keeps a tool call well formed while leaving the choice to the model.
 *
 * Grammar is not a rule about what the user meant - it only says which bytes are legal inside a
 * call. The model still picks the tool and the values; it simply cannot wander into prose, loop on
 * `<escape>`, or invent a tool name that does not exist. That is what makes a small model cheap to
 * run: the answer is a handful of tokens, and it always ends at `<end_function_call>`.
 */
object ToolGrammar {

    /**
     * The grammar for these tools, or null when there is nothing to shape. Keys are shared across
     * tools on purpose: a grammar per tool would need the tool choice to be made before the model
     * has made it.
     */
    fun forTools(specs: List<ToolSpec>): String? {
        val names = specs.map { it.name }.distinct().filter { it.isNotBlank() }
        if (names.isEmpty()) return null
        val keys = specs.flatMap { spec ->
            spec.args.split(',').map { it.trim().substringBefore(' ') }
        }.filter { it.isNotBlank() }.distinct()
        if (keys.isEmpty()) return null

        return buildString {
            append("root ::= \"<start_function_call>call:\" name \"{\" args? \"}\" \"<end_function_call>\"\n")
            append("name ::= ").append(names.joinToString(" | ") { "\"$it\"" }).append('\n')
            append("args ::= arg (\",\" arg)*\n")
            append("arg ::= key \":\" \"<escape>\" value \"<escape>\"\n")
            append("key ::= ").append(keys.joinToString(" | ") { "\"$it\"" }).append('\n')
            append("value ::= [^<]{0,120}\n")
        }
    }

    /** The marks a shaped turn must end on, so the caller can stop the engine the moment one lands. */
    val STOPS = listOf("<end_function_call>")
}
