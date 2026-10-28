package com.sadu.assistant.prompt

import com.sadu.assistant.ReplyLength

/**
 * Who Sadu is when you are just talking to it.
 *
 * This is the whole of Chat mode's instruction set, and it is deliberately tiny: a small
 * on-device model spends its context on what you said, not on rules about what it may not
 * do. Nothing here mentions the phone or the other mode - Chat mode's permissions are
 * enforced in code, where a model cannot argue with them.
 */
enum class ChatStyle(val key: String, val label: String, val line: String) {

    /** PocketPal-like: no personality imposed, the model just answers well. */
    Minimal(
        "minimal",
        "Minimal",
        "You are Sadu, a helpful assistant with good judgement. Give your best answer to " +
            "whatever is asked.",
    ),

    /** A little warmth, and the same freedom. */
    Friendly(
        "friendly",
        "Friendly",
        "You are Sadu, a warm and capable assistant. You talk with the user like a friend: " +
            "kind, curious and useful. Give your best answer to whatever is asked.",
    );

    companion object {
        val Default = Minimal
        val Choices = listOf(Minimal, Friendly)
        fun of(key: String?): ChatStyle = Choices.firstOrNull { it.key == key } ?: Default
    }
}

/** Chat mode's instructions: a style, a reply length, a language, and whatever the user added. */
object ChatPrompt {

    const val VERSION = "chat-3"

    /** Room for the user's own extra instructions. */
    const val MAX_EXTRA = 600

    fun build(
        facts: String,
        languageName: String,
        length: ReplyLength = ReplyLength.Default,
        style: ChatStyle = ChatStyle.Default,
        extra: String = "",
        webResults: Boolean = false,
        canSearch: Boolean = false,
    ): String {
        val sb = StringBuilder()
        sb.append(style.line)
        sb.append(" Answer in the same language the user uses, and never invent facts you are ")
        sb.append("not sure about.")
        sb.append(' ').append(length.instruction)
        sb.append(" If the user asks for a different length or shape of answer, do what the user asks.")
        sb.append(" Use short paragraphs, and simple bold or dashes when a list helps.\n")
        if (languageName.isNotBlank()) {
            sb.append("Always write your reply in ").append(languageName).append(".\n")
        }
        val wanted = extra.trim().take(MAX_EXTRA)
        if (wanted.isNotEmpty()) sb.append(wanted).append('\n')
        if (facts.isNotEmpty()) {
            sb.append("Things you know about the user: ").append(facts).append('\n')
        }
        if (webResults) {
            sb.append("The message carries web results: answer from them, in your own words, and ")
            sb.append("say the answer comes from a web search.")
        } else if (canSearch) {
            sb.append("You can look things up: when the answer needs a fact you cannot know - ")
            sb.append("news, prices, a business, a place, a person, something recent - reply with ")
            sb.append("exactly one line, SEARCH: followed by a short query, and nothing else. ")
            sb.append("The app searches and asks you again with the results. Do not ask for a ")
            sb.append("search for anything you already know.")
        }
        return sb.toString()
    }
}
