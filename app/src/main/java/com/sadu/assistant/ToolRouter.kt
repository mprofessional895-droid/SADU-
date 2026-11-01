package com.sadu.assistant

import java.util.Locale

/**
 * Which handful of tools a message is worth showing the model.
 *
 * Twenty tool definitions are about 1.2 KB of every request, and a small model given twenty
 * choices picks the wrong one more often than a model given four. This is a cheap text match -
 * no second model call, no latency - that narrows the menu, and it fails open: a message it does
 * not recognise keeps every tool, so an unknown request can never be starved of the right one.
 *
 * It decides *what to offer*, never *what to do*: the model still chooses the tool and its
 * arguments, and [Tools.run] still refuses anything the mode does not allow.
 */
enum class ToolCategory {
    System, Messaging, Calling, Time, Media, Apps, Web, Memory, Screen, Install
}

object ToolRouter {

    private val LABELS = mapOf(
        "flashlight" to ToolCategory.System,
        "battery" to ToolCategory.System,
        "volume" to ToolCategory.System,
        "wifi_settings" to ToolCategory.System,
        "bluetooth_settings" to ToolCategory.System,
        "whatsapp" to ToolCategory.Messaging,
        "send_sms" to ToolCategory.Messaging,
        "call" to ToolCategory.Calling,
        "time" to ToolCategory.Time,
        "date" to ToolCategory.Time,
        "set_alarm" to ToolCategory.Time,
        "play_music" to ToolCategory.Media,
        "media" to ToolCategory.Media,
        "open_app" to ToolCategory.Apps,
        "web_search" to ToolCategory.Web,
        "open_url" to ToolCategory.Web,
        "remember" to ToolCategory.Memory,
        "recall" to ToolCategory.Memory,
        "forget" to ToolCategory.Memory,
        "screen" to ToolCategory.Screen,
        "install_app" to ToolCategory.Install,
    )

    private val HINTS = mapOf(
        ToolCategory.Messaging to listOf(
            "whatsapp", "message", "msg", "sms", "text", "chat", "bhej", "bhejo", "send",
            "likh", "likho", "reply", "forward", "group", "inbox",
        ),
        ToolCategory.Calling to listOf("call", "dial", "ring", "phone", "milao", "phone karo"),
        ToolCategory.System to listOf(
            "flashlight", "torch", "battery", "charge", "volume", "awaz", "sound",
            "wifi", "wi-fi", "bluetooth", "settings", "setting", "silent", "mute",
        ),
        ToolCategory.Time to listOf("alarm", "timer", "remind", "reminder", "yaad dila", "time", "samay", "baje", "date", "tarikh"),
        ToolCategory.Media to listOf("music", "song", "gana", "gaana", "play", "pause", "next track", "previous", "spotify", "youtube music"),
        ToolCategory.Apps to listOf("app", "open", "khol", "kholo", "launch", "start", "close", "band"),
        ToolCategory.Web to listOf("search", "google", "website", "site", "url", "link", "browse", "dhundh", "khoj"),
        ToolCategory.Memory to listOf("remember", "yaad rakh", "yaad hai", "forget", "bhool", "recall", "what do you know about me"),
        ToolCategory.Screen to listOf("screen", "screenshot", "tap", "click", "scroll", "back", "home", "recents", "menu", "button", "type"),
        ToolCategory.Install to listOf("install", "download", "play store", "update app", "uninstall"),
    )

    /** A narrow menu still needs the few tools a chain of steps usually reaches for. */
    private val COMPANIONS = mapOf(
        ToolCategory.Messaging to setOf(ToolCategory.Apps, ToolCategory.Memory),
        ToolCategory.Screen to setOf(ToolCategory.Apps),
        ToolCategory.Media to setOf(ToolCategory.Apps),
        ToolCategory.Install to setOf(ToolCategory.Web),
        ToolCategory.Calling to setOf(ToolCategory.Memory),
    )

    /** Below this many tools the menu is too thin to be worth narrowing at all. */
    private const val TOO_THIN = 2

    fun categories(text: String): Set<ToolCategory> {
        val clean = text.lowercase(Locale.US)
        if (clean.isBlank()) return emptySet()
        val found = LinkedHashSet<ToolCategory>()
        for ((category, words) in HINTS) {
            if (words.any { clean.contains(it) }) found += category
        }
        for (category in found.toList()) COMPANIONS[category]?.let { found += it }
        return found
    }

    fun categoryOf(tool: String): ToolCategory? = LABELS[tool.trim().lowercase(Locale.US)]

    /**
     * The tools to offer for [text]. Every tool is returned when nothing matches, when only a
     * couple would be left, or when the message looks like it needs several things at once.
     */
    fun select(text: String, specs: List<ToolSpec>): List<ToolSpec> {
        val wanted = categories(text)
        if (wanted.isEmpty()) return specs
        val picked = specs.filter { categoryOf(it.name) in wanted }
        if (picked.size < TOO_THIN || picked.size >= specs.size) return specs
        // Memory tools are three short lines and are asked for mid-conversation more often than
        // any other tool, so they ride along with every narrowed menu. The order stays the menu's
        // own order, so a model that remembers "second entry" still sees the same thing.
        val keep = picked.map { it.name }.toSet() +
            specs.filter { categoryOf(it.name) == ToolCategory.Memory }.map { it.name }
        return specs.filter { it.name in keep }
    }
}
