package com.sadu.assistant.brain

import com.sadu.assistant.ToolSpec

/**
 * The Gemma 3 function-calling dialect, in one file and used by nothing else yet.
 *
 * A model trained for function calling writes its calls in special tokens, not JSON:
 *
 * ```
 * <start_function_call>call:open_app{app:<escape>youtube<escape>}<end_function_call>
 * ```
 *
 * `<escape>` is that model's string delimiter, and the tokens are special tokens - so a
 * prompt that asks for "only a JSON object", and a parser that only reads JSON, both push it
 * off its own format and it prints the tokens as text instead. This object holds the whole
 * dialect: the tool block it expects, the marks that end a call, a reader for what it writes,
 * and the guard against the repetition loop a small model falls into.
 *
 * Kept separate on purpose: nothing in the app calls it yet, so the current path cannot be
 * affected by it. Wiring it in later is a single branch on [handles].
 */
object FunctionGemma {

    /** Marks that end a turn. Without them a 270M model repeats `<escape>` to the token cap. */
    val STOPS = listOf("<end_function_call>", "<end_function_response>", "<end_of_turn>")

    private const val ESCAPE = "<escape>"
    private const val START = "<start_function_call>"
    private const val END = "<end_function_call>"

    /** True for the models this dialect belongs to, so a normal `.gguf` is never affected. */
    fun handles(modelName: String): Boolean =
        modelName.lowercase().replace("-", "").replace("_", "").contains("functiongemma")

    /**
     * The developer block the model was trained to read. Not JSON: the official template writes
     * `<start_function_declaration>declaration:<name>{description:<escape>…<escape>,
     * parameters:{properties:{…},required:[…],type:<escape>OBJECT<escape>}}<end_function_declaration>`.
     */
    fun tools(specs: List<ToolSpec>): String {
        val out = StringBuilder(
            "You are a model that can do function calling with the following functions",
        )
        for (spec in specs) {
            val names = spec.args.split(',').map { it.trim().substringBefore(' ') }.filter { it.isNotEmpty() }
            out.append("\n<start_function_declaration>declaration:").append(spec.name)
            out.append("{description:").append(ESCAPE).append(spec.description).append(ESCAPE)
            if (names.isNotEmpty()) {
                out.append(",parameters:{properties:{")
                names.forEachIndexed { i, arg ->
                    if (i > 0) out.append(',')
                    out.append(arg).append(":{description:").append(ESCAPE).append(arg)
                        .append(ESCAPE).append(",type:").append(ESCAPE).append("STRING")
                        .append(ESCAPE).append('}')
                }
                out.append("},required:[")
                names.forEachIndexed { i, arg ->
                    if (i > 0) out.append(',')
                    out.append(ESCAPE).append(arg).append(ESCAPE)
                }
                out.append("],type:").append(ESCAPE).append("OBJECT").append(ESCAPE).append('}')
            }
            out.append("}<end_function_declaration>")
        }
        return out.toString()
    }

    /** One call the model asked for. `args` keeps the order the model wrote them in. */
    data class Call(val name: String, val args: Map<String, String>)

    /**
     * Reads the first call in a reply. Tolerates the shapes seen in the wild: the documented
     * `<start_function_call>call:name{a:<escape>b<escape>}<end_function_call>`, a missing
     * underscore (`<startfunctioncall>`), and the parenthesis variant `call("a","b")`.
     */
    fun read(reply: String): Call? {
        val text = reply.replace("<startfunctioncall>", START)
            .replace("<start_function_call >", START)
            .replace("<endfunctioncall>", END)
        val named = Regex("<start_function_call>\\s*call:([A-Za-z0-9_.]+)\\s*\\{(.*?)\\}\\s*</?end_function_call>?",
            RegexOption.DOT_MATCHES_ALL).find(text)
        if (named != null) {
            return Call(named.groupValues[1], args(named.groupValues[2]))
        }
        val bare = Regex("<start_function_call>\\s*call:([A-Za-z0-9_.]+)\\s*\\{").find(text)
        if (bare != null) {
            val rest = text.substring(bare.range.last + 1)
            return Call(bare.groupValues[1], args(rest.substringBefore("}")))
        }
        val parens = Regex("call\\(\\s*\"([^\"]*)\"\\s*(?:,\\s*\"([^\"]*)\")?").find(text)
            ?: return null
        val name = parens.groupValues[1].trim()
        if (name.isEmpty()) return null
        val value = parens.groupValues[2].trim()
        return Call(name, if (value.isEmpty()) emptyMap() else mapOf("value" to value))
    }

    /** `arg:<escape>value<escape>, other:<escape>value<escape>` -> ordered pairs. */
    private fun args(body: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val rx = Regex("([A-Za-z0-9_]+)\\s*:\\s*(?:<escape>(.*?)<escape>|\"([^\"]*)\"|([^,}]*))",
            RegexOption.DOT_MATCHES_ALL)
        for (m in rx.findAll(body)) {
            val value = listOf(m.groupValues[2], m.groupValues[3], m.groupValues[4])
                .firstOrNull { it.isNotEmpty() } ?: ""
            // A call that was cut mid-value leaves the opening <escape> behind.
            out[m.groupValues[1]] = value.trim().removePrefix(ESCAPE).removeSuffix(ESCAPE).trim()
        }
        return out
    }

    /**
     * A Hinglish sentence leaves its postposition in the argument - the model wrote
     * `contact: "shubham ko"` in every run. The trailing particle is not part of a name.
     */
    fun tidyArg(value: String): String = value.trim()
        .replace(Regex("\\s+(?:ko|se|me|par|pe|k liye)$", RegexOption.IGNORE_CASE), "")
        .trim()

    /** What the model gets back after a tool ran, in the shape it was trained on. */
    fun response(name: String, result: String): String =
        "<start_function_response>response:$name{result:<escape>$result<escape>}<end_function_response>"

    /** Text with every special token removed, so nothing raw is ever shown or spoken. */
    fun clean(reply: String): String = reply
        .replace(Regex("</?(?:start|end)_(?:function_call|function_response|of_turn)>"), "")
        .replace(Regex("<startfunctioncall>|<endfunctioncall>|<escape>|<eos>"), "")
        .replace(Regex("\\s{2,}"), " ")
        .trim()

    /**
     * Cuts a repetition loop. A 270M model that misses its stop mark writes `<escape>` forever;
     * ten in a row is already broken, so everything from there is dropped.
     */
    fun withoutLoop(reply: String, limit: Int = 10): String {
        val runs = Regex("(?:<escape>\\s*){$limit,}").find(reply) ?: return reply
        return reply.substring(0, runs.range.first)
    }
}
