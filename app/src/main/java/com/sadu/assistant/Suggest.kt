package com.sadu.assistant

/**
 * Guessing which saved contact a half-remembered name meant.
 *
 * Pure Kotlin so the behaviour can be tested without Android. The rule of thumb: a short
 * edit distance on a short name is a suggestion, a shared start is a suggestion, and
 * everything else is noise - better to ask nothing than to offer a stranger.
 */
object Suggest {

    /** How many names to offer. More than a handful is a list, not a question. */
    const val MAX = 3

    fun closest(query: String, names: List<String>, max: Int = MAX): List<String> {
        val q = clean(query)
        if (q.isEmpty()) return emptyList()

        val scored = names
            .distinctBy { clean(it) }
            .mapNotNull { name ->
                val n = clean(name)
                when {
                    n == q -> null                      // exact matches are not suggestions
                    n.isEmpty() -> null
                    n.startsWith(q) || q.startsWith(n) -> name to 0
                    n.contains(q) || q.contains(n) -> name to 1
                    else -> {
                        val d = distance(q, n)
                        val allowed = if (q.length <= 4) 1 else 2
                        if (d <= allowed) name to (2 + d) else null
                    }
                }
            }
            .sortedWith(compareBy({ it.second }, { it.first.length }))

        return scored.take(max).map { it.first }
    }

    /** "kya aap in me se kisi ki baat kar rahe the" - only when there is something to say. */
    fun question(query: String, names: List<String>): String? {
        val options = closest(query, names)
        return when {
            options.isEmpty() -> null
            options.size == 1 ->
                "I couldn't find \"$query\". Did you mean ${options.first()}? " +
                    "Say yes and I'll do it."
            else ->
                "I couldn't find \"$query\". Did you mean " +
                    options.joinToString(", ") { it } + "? Tell me which one, or the number."
        }
    }

    private fun clean(s: String): String = s.trim().lowercase()
        .replace(Regex("\\s+"), " ")

    /** Levenshtein distance, two rows of memory. */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val swap = prev
            prev = cur
            cur = swap
        }
        return prev[b.length]
    }
}
