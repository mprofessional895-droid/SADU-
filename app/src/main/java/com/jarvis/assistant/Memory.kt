package com.jarvis.assistant

import android.content.Context

/** Tiny on-phone memory: a list of short facts about the user. Never leaves the device. */
class Memory(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("jarvis_memory", Context.MODE_PRIVATE)

    fun facts(): List<String> =
        (prefs.getString("facts", "") ?: "").split("\n").filter { it.isNotBlank() }

    private fun save(list: List<String>) {
        prefs.edit().putString("facts", list.takeLast(40).joinToString("\n")).apply()
    }

    fun add(fact: String) {
        save(facts() + fact.replace("\n", " ").trim())
    }

    fun setName(name: String) {
        save(facts().filterNot { it.startsWith("The user's name is") } + "The user's name is ${name.trim()}")
    }

    fun clear() {
        prefs.edit().remove("facts").apply()
    }

    /** Short text that goes into the AI prompt. */
    fun asContext(): String = facts().takeLast(10).joinToString("; ").take(500)
}
