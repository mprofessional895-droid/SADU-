package com.sadu.assistant

/**
 * One thing Sadu did while working on a message, in order.
 *
 * Kept so the caller can tell "one answer" from "three steps and a summary", and so the
 * screen can show what happened under the reply instead of only the last line.
 */
data class StepRecord(val tool: String, val result: String)

/**
 * The record of the last model answer: which tools ran, in order. Empty when a rule
 * answered, or when the model just talked.
 */
class StepLog {
    private val steps = ArrayList<StepRecord>()

    fun clear() = steps.clear()

    fun add(tool: String, result: String) {
        steps.add(StepRecord(tool, result))
    }

    fun all(): List<StepRecord> = steps.toList()

    val size: Int get() = steps.size

    /**
     * What Sadu says when the model used up its steps without a closing sentence. It reads
     * back what actually happened, so the user is never left with a silent hang.
     */
    fun summary(): String {
        if (steps.isEmpty()) return "I couldn't finish that one. Try asking again."
        return steps.joinToString(separator = " ") { it.result }
    }
}

/** Small helpers the tool loop needs, kept out of the request types. */
object Steps {

    /** The line that tells the model what it just did, when the results go back to it. */
    fun resultLine(record: StepRecord): String = "${record.tool}: ${record.result}"

    /** True when the tool is not one the menu offered: worth one retry with the menu. */
    fun unknownTool(name: String, menu: String): Boolean {
        val key = name.trim().lowercase()
        if (key.isBlank()) return true
        return !Regex("(?m)^-\\s*" + Regex.escape(key) + "\\(").containsMatchIn(menu)
    }
}
