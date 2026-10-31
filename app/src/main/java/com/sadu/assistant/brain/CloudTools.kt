package com.sadu.assistant.brain

import com.sadu.assistant.ToolSpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * The toolbox, said in each provider's own function-calling shape.
 *
 * The app never decides what the user meant: it declares the tools and the model picks one.
 * A declared tool is also what keeps a strict provider happy - a model that answers with a
 * function call while none were declared is rejected outright with
 * "Tool choice is none, but model called a tool".
 */
object CloudTools {

    /** The argument names in a spec like "contact, message" or "on (true|false)". */
    fun arguments(spec: ToolSpec): List<String> =
        spec.args.split(',')
            .map { it.trim().substringBefore(' ').trim() }
            .filter { it.isNotEmpty() }

    private fun properties(spec: ToolSpec, key: String = "type"): JSONObject {
        val props = JSONObject()
        for (name in arguments(spec)) {
            props.put(name, JSONObject().put(key, "string"))
        }
        return props
    }

    private fun schema(spec: ToolSpec, typeKey: String = "type"): JSONObject = JSONObject()
        .put(typeKey, "object")
        .put("properties", properties(spec, typeKey))

    /** OpenAI-compatible and Anthropic both take a list of function descriptions. */
    fun openAi(specs: List<ToolSpec>): JSONArray {
        val arr = JSONArray()
        for (spec in specs) {
            arr.put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", spec.name)
                            .put("description", spec.description)
                            .put("parameters", schema(spec)),
                    ),
            )
        }
        return arr
    }

    fun anthropic(specs: List<ToolSpec>): JSONArray {
        val arr = JSONArray()
        for (spec in specs) {
            arr.put(
                JSONObject()
                    .put("name", spec.name)
                    .put("description", spec.description)
                    .put("input_schema", schema(spec)),
            )
        }
        return arr
    }

    /** Gemini writes its schema types in capitals and nests them under functionDeclarations. */
    fun gemini(specs: List<ToolSpec>): JSONArray {
        val declarations = JSONArray()
        for (spec in specs) {
            val props = JSONObject()
            for (name in arguments(spec)) {
                props.put(name, JSONObject().put("type", "STRING"))
            }
            declarations.put(
                JSONObject()
                    .put("name", spec.name)
                    .put("description", spec.description)
                    .put(
                        "parameters",
                        JSONObject().put("type", "OBJECT").put("properties", props),
                    ),
            )
        }
        return JSONArray().put(JSONObject().put("functionDeclarations", declarations))
    }
}
