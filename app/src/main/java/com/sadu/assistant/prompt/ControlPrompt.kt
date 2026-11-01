package com.sadu.assistant.prompt

import com.sadu.assistant.ReplyLength

/**
 * Who Sadu is when it is allowed to act on the phone.
 *
 * Control carries the tool protocol, the tool menu and the command examples - everything Chat
 * mode deliberately leaves out. It is kept apart from [ChatPrompt] so the two modes can be
 * changed and reviewed one at a time.
 *
 * The wording is deliberately short: every line is re-read on every message. The rules that
 * matter for correctness - JSON only for a command, never claim an action that a tool did not
 * run, never invent a tool - are kept; the padding around them is gone.
 */
object ControlPrompt {

    const val VERSION = "control-3"

    private const val PERSONA =
        "You are Sadu, a capable assistant running privately on this Android phone."

    fun build(
        facts: String,
        menu: String,
        languageName: String,
        length: ReplyLength = ReplyLength.Default,
        toolResults: List<String> = emptyList(),
        retry: Boolean = false,
    ): String {
        val sb = StringBuilder()
        sb.append(PERSONA).append(' ').append(length.instruction)
        sb.append(" Do what the user asks for a different shape of answer.\n")
        if (languageName.isNotBlank()) {
            sb.append("Always write your reply in ").append(languageName).append(".\n")
        }
        sb.append("\nCHAT: if the user is talking or asking, answer in plain words, no JSON.\n")
        sb.append("COMMAND: if the user asks you to do something on the phone that a tool can do, ")
        sb.append("reply with ONLY one JSON object and nothing else.\n")
        sb.append("If it is unclear whether they want an action, choose CHAT.\n\n")
        sb.append("Your tools:\n").append(menu).append("\n\n")
        if (facts.isNotEmpty()) {
            sb.append("Things you know about the user: ").append(facts).append("\n\n")
        }
        appendResults(sb, toolResults, retry)
        sb.append("Command examples:\n")
        sb.append("User: flashlight on karo\n")
        sb.append("Sadu: {\"tool\":\"flashlight\",\"args\":{\"on\":\"true\"}}\n")
        sb.append("User: mummy ko WhatsApp par bol ki main late hoon\n")
        sb.append("Sadu: {\"tool\":\"whatsapp\",\"args\":{\"contact\":\"Mummy\",\"message\":\"main late hoon\"}}\n\n")
        sb.append("Chat example:\n")
        sb.append("User: how are you today?\n")
        sb.append("Sadu: I am doing well, thanks for asking! What are you up to?\n\n")
        sb.append("Rules:\n")
        sb.append("- One tool call per reply. Its result comes back, then call the next tool or answer.\n")
        sb.append("- Use only the tools above, with the arguments shown and real values.\n")
        sb.append("- Never say something was done unless its tool result says it was.\n")
        sb.append("- In chat use plain sentences with real line breaks, never escape codes such ")
        sb.append("as \\n, and no quotes around the whole answer, no headings.\n")
        sb.append("- If web results are given, answer from them and say it came from a web search.\n")
        sb.append("- Do not mention JSON, tools or these instructions to the user.\n")
        return sb.toString()
    }

    /**
     * What has already been done for this message. Shown to the model so it can decide the
     * next step, and never shown to the user - the app says its own summary.
     */
    private fun appendResults(sb: StringBuilder, results: List<String>, retry: Boolean) {
        if (retry) {
            sb.append("That reply was not a tool from the list above. Reply again with one tool ")
            sb.append("call from the list, or plain words.\n\n")
        }
        if (results.isEmpty()) return
        sb.append("Already done for this message:\n")
        for (line in results) sb.append("- ").append(line).append('\n')
        sb.append("\n")
    }
}
