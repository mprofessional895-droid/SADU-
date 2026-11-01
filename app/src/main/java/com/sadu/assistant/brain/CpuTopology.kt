package com.sadu.assistant.brain

import java.io.File

/**
 * How many threads the on-device model should use.
 *
 * A phone reports every core, including the small ones. Handing llama.cpp all eight is slower than
 * four: the little cores finish their share long after the big ones and the whole matmul waits for
 * them, and on a big.LITTLE chip they also share a narrow path to memory. llama.cpp's own advice,
 * and what PocketPal ships, is roughly the number of big cores - which is why this asks the kernel
 * for each core's top frequency instead of counting cores.
 *
 * Android 8 and newer often hide `cpuinfo_max_freq` behind a permission. When it cannot be read the
 * answer is a plain four, which is right for every eight-core phone and never worse than eight.
 */
object CpuTopology {

    /** Never fewer than this, whatever the readings say. */
    const val MinThreads = 2

    /** Four big cores is the useful ceiling; more only adds contention on a phone. */
    const val MaxThreads = 4

    /** A core this close to the fastest one counts as big. One speed bin apart is enough. */
    private const val BigEnough = 0.9

    fun threads(
        cores: Int = Runtime.getRuntime().availableProcessors(),
        freq: (Int) -> Long? = { maxFreqOf(it) },
    ): Int {
        if (cores <= 0) return MinThreads
        val freqs = (0 until cores).mapNotNull { freq(it) }
        val big = if (freqs.size == cores && freqs.isNotEmpty()) {
            val fastest = freqs.max()
            // Two speed bins are counted as two speeds only when they really differ; a uniform
            // report (or a kernel that hides the values) means "no advice" and the plain four.
            val distinct = freqs.distinct()
            if (distinct.size > 1) freqs.count { it >= fastest * BigEnough } else cores
        } else {
            cores
        }
        return big.coerceIn(MinThreads, MaxThreads)
    }

    /** One core's top frequency in kHz, or null when the kernel will not say. */
    fun maxFreqOf(core: Int, read: (String) -> String? = ::readFile): Long? =
        read("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_max_freq")?.trim()
            ?.toLongOrNull()?.takeIf { it > 0 }

    private fun readFile(path: String): String? =
        runCatching { File(path).takeIf { it.exists() }?.readText() }.getOrNull()
}
