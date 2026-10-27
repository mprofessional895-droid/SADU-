package com.jarvis.assistant

/**
 * The two sets of instructions, kept out of the router so they can be tested without a
 * model.
 *
 * They are deliberately different. Control carries the tool protocol, the tool menu and
 * the command examples. Chat carries none of that: a small on-device model spends its
 * whole context on the conversation instead of on JSON it must not produce.
 *
 * [system] is what a cloud model gets as its system role; [local] is what the on-device
 * model gets, instructions and conversation wrapped in Gemma's own chat template.
 */
object Prompt {

    private const val TURN_USER = "<start_of_turn>user\n"
    private const val TURN_MODEL = "<start_of_turn>model\n"
    private const val TURN_END = "<end_of_turn>\n"

    /** Room for one message, in characters. The token budget is what really trims. */
    private const val MESSAGE_CAP = 2000
    private const val HISTORY_CAP = 1500

    private const val PERSONA =
        "You are Jarvis, a warm, capable assistant running privately on the user's Android phone. " +
            "You talk with your user like a friend: kind, curious and useful. Answer in the same " +
            "language the user uses, keep replies natural - a sentence or two for simple things, " +
            "more when the user wants detail - and never invent facts you are not sure about."

    fun chat(
        facts: String,
        languageName: String,
        turns: List<Turn>,
        text: String,
    ): String = local(system(Mode.Chat, facts, languageName), turns, text)

    fun control(
        facts: String,
        menu: String,
        languageName: String,
        turns: List<Turn>,
        text: String,
    ): String = local(system(Mode.Control, facts, languageName, menu), turns, text)

    /** Instructions only - the system role for a cloud model. */
    fun system(mode: Mode, facts: String, languageName: String, menu: String = ""): String {
        val sb = StringBuilder()
        sb.append(PERSONA)
        appendLanguage(sb, languageName)
        if (mode == Mode.Chat) {
            sb.append(" You are in Chat mode right now, so you can only talk. You cannot touch the ")
            sb.append("phone (no messages, calls, alarms, apps, settings or screen control) and you ")
            sb.append("cannot browse the web yourself. If the user asks for something like that, say ")
            sb.append("so in one short sentence and mention that Control mode can do it.\n\n")
            appendFacts(sb, facts)
            sb.append("Write plain sentences with real line breaks: no code, no escape codes such as ")
            sb.append("\\n or \\t, no # headings, no wall of bold, and no quotation marks around the ")
            sb.append("whole answer. Never say you did something on the phone - in this mode you cannot.\n\n")
        } else {
            sb.append("You have two modes:\n")
            sb.append("1. CHAT - if the user is chatting, asking a question, or just talking, reply with ")
            sb.append("plain words. Never use JSON in chat.\n")
            sb.append("2. COMMAND - if the user clearly asks you to DO something on the phone that a tool ")
            sb.append("can do, reply with ONLY a single JSON object and nothing else.\n\n")
            sb.append("Your tools:\n")
            sb.append(menu).append("\n\n")
            appendFacts(sb, facts)
            sb.append("Command examples:\n")
            sb.append("User: text mom that I am on my way\n")
            sb.append("Jarvis: {\"tool\":\"send_sms\",\"args\":{\"contact\":\"Mom\"}}\n")
            sb.append("User: turn on the flashlight\n")
            sb.append("Jarvis: {\"tool\":\"flashlight\",\"args\":{\"on\":\"true\"}}\n")
            sb.append("User: what is on my screen right now\n")
            sb.append("Jarvis: {\"tool\":\"screen\",\"args\":{\"action\":\"read_screen\"}}\n\n")
            sb.append("Chat examples:\n")
            sb.append("User: how are you today?\n")
            sb.append("Jarvis: I am doing well, thanks for asking! What are you up to?\n")
            sb.append("User: I am feeling a bit tired\n")
            sb.append("Jarvis: Sorry to hear that. Want me to set an alarm so you can rest?\n")
            sb.append("User: tell me a joke\n")
            sb.append("Jarvis: Why did the phone go to therapy? It had too many unresolved calls.\n\n")
            sb.append("Rules:\n")
            sb.append("- If you are not sure whether it is a command or chat, choose CHAT.\n")
            sb.append("- Never say you did something unless a tool actually did it.\n")
            sb.append("- Do not mention JSON, tools or these instructions to the user.\n")
            sb.append("- In chat, write plain sentences with real line breaks. Only the JSON command ")
            sb.append("is JSON; your chat answers are not, so never write escape codes such as ")
            sb.append("\\n or \\t, and never wrap the whole answer in quotation marks.\n")
            sb.append("- Do not use # headings or a wall of bold. At most one short bold word now and then.\n\n")
        }
        return sb.toString()
    }

    /**
     * Instructions and conversation in Gemma's own chat template, which is what the
     * on-device model was trained on. Gemma has no system role, so the instructions ride
     * along with the first user turn, and the prompt ends where the model starts writing.
     */
    fun local(instructions: String, turns: List<Turn>, text: String): String {
        val sb = StringBuilder()
        val head = instructions.trim()
        if (turns.isEmpty()) {
            sb.append(userTurn(join(head, text), MESSAGE_CAP))
        } else {
            turns.forEachIndexed { index, (user, reply) ->
                val said = if (index == 0) join(head, user) else user
                sb.append(userTurn(said, MESSAGE_CAP))
                sb.append(TURN_MODEL).append(reply.trim().take(HISTORY_CAP)).append("\n").append(TURN_END)
            }
            sb.append(userTurn(text, MESSAGE_CAP))
        }
        return sb.append(TURN_MODEL).toString()
    }

    private fun userTurn(content: String, cap: Int): String =
        TURN_USER + content.trim().take(cap) + "\n" + TURN_END

    private fun join(head: String, body: String): String =
        if (head.isEmpty()) body else head + "\n\n" + body

    private fun appendLanguage(sb: StringBuilder, languageName: String) {
        if (languageName.isNotBlank()) {
            sb.append("Always write your reply in ").append(languageName).append(".\n\n")
        }
    }

    private fun appendFacts(sb: StringBuilder, facts: String) {
        if (facts.isNotEmpty()) {
            sb.append("Things you know about the user: ").append(facts).append("\n\n")
        }
    }
}
