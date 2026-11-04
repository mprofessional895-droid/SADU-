package com.sadu.assistant

/**
 * The one check that stands between a model load and the low-memory killer.
 *
 * A model is loaded into native memory, which Android does not count against the app's heap
 * and cannot reclaim: when RAM runs out, the process is killed outright - the app closes and
 * the model disappears from RAM with no error to read. Thinking makes it worse, because the
 * turn stays alive far longer than a plain answer.
 *
 * Android-free so the rules can be unit tested. [fits] is deliberately generous: the model
 * itself plus room for the context and the rest of the app.
 */
object MemoryGuard {

    /** Free RAM kept for everything else: context, the UI, the rest of the app. */
    const val ReserveMb = 512

    /** Under a ninth of RAM left counts as pressure, the same line Android watches. */
    private const val PressureDivisor = 8

    /** What a model of [modelBytes] needs free before it is loaded. */
    fun needMb(modelBytes: Long): Int = (modelBytes / (1024L * 1024L)).toInt() + ReserveMb

    /** False when the model plus its working room cannot fit in [availableMb]. */
    fun fits(modelBytes: Long, availableMb: Int): Boolean {
        if (availableMb <= 0) return true
        return availableMb >= needMb(modelBytes)
    }

    /** A shorter reply while the phone is short of memory, so the turn ends before it is killed. */
    fun tokenCap(availableMb: Int, totalMb: Int, asked: Int): Int {
        if (totalMb <= 0 || availableMb <= 0) return asked
        val pressure = availableMb < totalMb / PressureDivisor
        if (!pressure) return asked
        return minOf(asked, 384).coerceAtLeast(64)
    }

    /** What Settings says when the load was refused. */
    fun refusal(modelBytes: Long, availableMb: Int): String =
        "Not enough free memory to load this model: it needs about ${needMb(modelBytes)} MB " +
            "and only $availableMb MB is free. Close other apps, pick a smaller model, or " +
            "choose a smaller context window."
}
