package com.sadu.assistant

import com.sadu.assistant.prompt.ChatPrompt
import com.sadu.assistant.prompt.ChatStyle
import com.sadu.assistant.prompt.ControlPrompt
import com.sadu.assistant.prompt.GemmaChatTemplate

/**
 * The way into the two instruction sets.
 *
 * Chat and Control live in their own files under `prompt/`, so either one can be changed,
 * reviewed and released on its own. This object only routes to them - it holds no wording of
 * its own, so a change to Chat mode cannot quietly change Control mode.
 *
 * [system] is what a cloud model gets as its system role; [local] is what the on-device model
 * gets, instructions and conversation wrapped in Gemma's own chat template.
 */
object Prompt {

    fun chat(
        facts: String,
        languageName: String,
        turns: List<Turn>,
        text: String,
        length: ReplyLength = ReplyLength.Default,
        style: ChatStyle = ChatStyle.Default,
        extra: String = "",
        webResults: Boolean = false,
        canSearch: Boolean = false,
    ): String = local(
        system(
            Mode.Chat, facts, languageName, length = length, style = style, extra = extra,
            webResults = webResults, canSearch = canSearch,
        ),
        turns,
        text,
    )

    fun control(
        facts: String,
        menu: String,
        languageName: String,
        turns: List<Turn>,
        text: String,
        length: ReplyLength = ReplyLength.Default,
    ): String = local(system(Mode.Control, facts, languageName, menu, length), turns, text)

    /** Instructions only - the system role for a cloud model. */
    fun system(
        mode: Mode,
        facts: String,
        languageName: String,
        menu: String = "",
        length: ReplyLength = ReplyLength.Default,
        style: ChatStyle = ChatStyle.Default,
        extra: String = "",
        webResults: Boolean = false,
        canSearch: Boolean = false,
    ): String = if (mode == Mode.Chat) {
        ChatPrompt.build(facts, languageName, length, style, extra, webResults, canSearch)
    } else {
        ControlPrompt.build(facts, menu, languageName, length)
    }

    /** Instructions and conversation, in the model's own format. */
    fun local(instructions: String, turns: List<Turn>, text: String): String =
        GemmaChatTemplate.build(instructions, turns, text)
}
