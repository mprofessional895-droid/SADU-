package com.sadu.assistant

/**
 * Turns raw model output into something that reads nicely on screen.
 *
 * Small on-device models mimic the JSON escaping they see in the tool-call examples,
 * so replies come back with literal "\n" instead of real line breaks. They also leak
 * prompt control tokens, code fences and stray "Sadu:" labels.
 *
 * Pure Kotlin (no Android types) so it can be unit-tested on the JVM.
 */
object ReplyText {

    /** Same clean-up, but honest about an empty reply: it stays empty instead of a shrug. */
    fun cleanKeepingBlank(raw: String): String =
        clean(raw, placeholder = "")

    fun clean(raw: String): String = clean(raw, placeholder = "I'm here. What would you like to do?")

    private fun clean(raw: String, placeholder: String): String {
        var s = raw

        // Prompt control tokens
        s = s.replace("<end_of_turn>", "")
            .replace("<start_of_turn>", "")
            .replace("<eos>", "")
            .replace("<bos>", "")

        // A tool call that never became one - drop the object, keep the words
        s = s.replace(Regex("\\{\\s*\"(?:tool|action)\"[\\s\\S]{0,400}?\\}"), " ")

        // JSON-style escapes -> real characters
        s = s.replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\\t", "    ")
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\\\", "\\")

        s = s.replace("\r\n", "\n").replace('\r', '\n')

        // Code fences add nothing once the code is shown as plain text
        s = s.lines().filterNot { it.trim().startsWith("```") }.joinToString("\n")

        // Markdown table separator rows (|---|---|)
        s = s.lines()
            .filterNot { it.contains('|') && it.replace(Regex("[\\s|:-]"), "").isEmpty() }
            .joinToString("\n")

        // The model sometimes echoes the speaker label
        if (s.contains("Sadu:")) s = s.substringAfterLast("Sadu:")

        s = s.removePrefix("model").trim()
        s = s.trim('`', ' ', '\n', '\t')

        // At most one blank line between paragraphs
        s = s.replace(Regex("\n[ \t]*\n[ \t]*\n+"), "\n\n")
        s = s.lines().joinToString("\n") { it.trimEnd() }.trim()

        return s.ifBlank { placeholder }
    }
}
