package com.sadu.assistant

import android.content.Context

/** Tiny on-phone memory: a list of short facts about the user. Never leaves the device. */
class Memory(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("sadu_memory", Context.MODE_PRIVATE)

    fun facts(): List<String> =
        (prefs.getString("facts", "") ?: "").split("\n").filter { it.isNotBlank() }

    private fun save(list: List<String>) {
        prefs.edit().putString("facts", list.takeLast(40).joinToString("\n")).apply()
    }

    fun add(fact: String) {
        val clean = fact.replace("\n", " ").trim()
        if (clean.isEmpty()) return
        val existing = facts()
        if (existing.any { it.equals(clean, ignoreCase = true) }) return
        save(existing + clean)
    }

    fun setName(name: String) {
        save(facts().filterNot { it.startsWith("The user's name is") } + "The user's name is ${name.trim()}")
    }

    fun remove(fact: String) {
        save(facts() - fact)
    }

    fun update(old: String, new: String) {
        val cleaned = new.replace("\n", " ").trim()
        if (cleaned.isEmpty()) return
        save(facts().map { if (it == old) cleaned else it })
    }

    fun clear() {
        prefs.edit().remove("facts").apply()
    }

    /** Short text that goes into the AI prompt: only what the message is about. */
    fun asContext(query: String = ""): String = MemoryFocus.pick(facts(), query)
}

/**
 * Which remembered facts belong in a prompt.
 *
 * Kept apart from [Memory] so it can be tested without a phone, and apart from the prompt so the
 * rule for "what is relevant" is written once. Old behaviour sent the last ten facts every time;
 * that spends tokens on the wrong things and hands a small model a subject nobody asked about.
 */
object MemoryFocus {

    private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")

    /** Facts that shape how Sadu answers, whatever the question is. */
    private val STANDING = listOf("name is", "prefers", "likes", "always", "never", "wants", "calls me")

    private const val MaxFacts = 5
    private const val FallbackFacts = 3
    private const val MaxChars = 400

    fun standing(fact: String): Boolean {
        val line = fact.lowercase()
        return STANDING.any { line.contains(it) }
    }

    fun pick(all: List<String>, query: String): String {
        if (all.isEmpty()) return ""
        val picked = if (query.isBlank()) all.takeLast(FallbackFacts) else focus(all, query)
        return picked.joinToString("; ").take(MaxChars)
    }

    fun focus(all: List<String>, query: String): List<String> {
        val words = query.lowercase().split(NOT_WORD).filter { it.length > 2 }.toSet()
        if (words.isEmpty()) return all.takeLast(FallbackFacts)
        val hits = all.map { fact -> words.count { word -> fact.lowercase().contains(word) } }
        // No word of the message appears in any fact: the message is not about memory at all, so
        // a few recent facts are enough. Anything more is noise dressed up as context.
        if (hits.sum() == 0) return all.takeLast(FallbackFacts)
        val scored = all.mapIndexed { index, fact ->
            Triple(fact, hits[index] * 10 + if (standing(fact)) 5 else 0, index)
        }
        val relevant = scored.filter { it.second > 0 }
            .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenByDescending { it.third })
            .map { it.first }
        if (relevant.isEmpty()) return all.takeLast(FallbackFacts)
        val kept = ArrayList<String>(relevant.take(MaxFacts))
        for (fact in all) {
            if (kept.size >= MaxFacts) break
            if (standing(fact) && kept.none { it == fact }) kept += fact
        }
        return kept
    }
}
