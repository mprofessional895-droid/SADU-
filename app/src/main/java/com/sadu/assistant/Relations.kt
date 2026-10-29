package com.sadu.assistant

/**
 * Family words people actually say ("papa", "mummy", "bhai") and the names they mean.
 *
 * A contact saved as "Papa" is found by "call papa"; a contact saved as "Dad" is found by
 * "papa ko call kar". Matching stays conservative: only the words in this table are
 * translated, and the translated name still has to match a real contact.
 */
object Relations {

    private val table: Map<String, List<String>> = mapOf(
        "papa" to listOf("papa", "pappa", "dad", "daddy", "father", "abbu", "abba", "appa", "pita"),
        "mummy" to listOf("mummy", "mum", "mom", "mommy", "mother", "maa", "ma", "ammi", "amma"),
        "bhai" to listOf("bhai", "brother", "bhaiya", "bhai sahab", "bhaisahab"),
        "behen" to listOf("behen", "behan", "sister", "didi", "bahan"),
        "wife" to listOf("wife", "biwi", "patni", "jaan"),
        "husband" to listOf("husband", "pati", "shauhar"),
        "dost" to listOf("friend", "dost", "yaar"),
    )

    /** Every spelling of the same relation, or just the one the user typed. */
    fun variants(word: String): List<String> {
        val key = word.trim().lowercase()
        table[key]?.let { return it }
        for (spellings in table.values) if (key in spellings) return spellings
        return listOf(key)
    }
}

/** How many tool calls Sadu may make while working out one message. */
object ToolSteps {
    /** Each step is a full model round trip, so this is also the wait before Sadu summarises. */
    const val Max = 4
}
