package com.sadu.assistant

/**
 * llama.cpp's own counters, split by phase.
 *
 * Reading the prompt and writing the answer are different jobs and different speeds: a long
 * prompt is slow to read even when generation is quick. One "tokens per second" number hides
 * which half is the problem, so the two are kept apart here.
 */
data class PerfLine(
    val promptTokS: Double,
    val promptTokens: Int,
    val promptMs: Long,
    val genTokS: Double,
    val genTokens: Int,
    val genMs: Long,
    val context: Int,
    val threads: Int,
) {
    /** One log line, with the numbers already parsed so they can be compared between builds. */
    fun line(engine: String, mode: Mode, steps: Int, toolMs: Long): String = buildString {
        append("engine=").append(engine)
        append(" mode=").append(mode.name)
        append(" prefill_ms=").append(promptMs)
        append(" prompt_tok=").append(promptTokens)
        append(" prompt_tok_s=").append(rounded(promptTokS))
        append(" gen_ms=").append(genMs)
        append(" gen_tok=").append(genTokens)
        append(" gen_tok_s=").append(rounded(genTokS))
        append(" ctx=").append(context)
        append(" threads=").append(threads)
        append(" steps=").append(steps)
        append(" tool_ms=").append(toolMs)
    }

    private fun rounded(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)

    companion object {

        /** "" or anything unparseable is null: a missing measurement is not a zero. */
        fun parse(text: String?): PerfLine? {
            if (text.isNullOrBlank()) return null
            val m = PATTERN.find(text) ?: return null
            val v = m.groupValues
            return runCatching {
                PerfLine(
                    promptTokS = v[1].toDouble(),
                    promptTokens = v[2].toInt(),
                    promptMs = v[3].toLong(),
                    genTokS = v[4].toDouble(),
                    genTokens = v[5].toInt(),
                    genMs = v[6].toLong(),
                    context = v[7].toInt(),
                    threads = v[8].toInt(),
                )
            }.getOrNull()
        }

        private val PATTERN = Regex(
            "prompt=([0-9.]+)tok/s \\((\\d+) tok in (\\d+)ms\\) " +
                "gen=([0-9.]+)tok/s \\((\\d+) tok in (\\d+)ms\\) " +
                "ctx=(\\d+) threads=(\\d+)"
        )
    }
}
