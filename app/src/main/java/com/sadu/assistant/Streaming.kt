package com.sadu.assistant

import kotlin.math.ceil

/**
 * Decides how much of an answer to show on each tick.
 *
 * Cloud models stream token by token and the on-device model answers in one piece; both
 * end up here, so a reply always arrives at the same calm speed instead of appearing all
 * at once or in jagged bursts.
 */
/**
 * When each part of one answer happened, measured on the phone's clock.
 *
 * Thinking time is not the same as waiting time: a model can think for four seconds and then
 * write quickly, and the reader should be able to see which of the two is slow.
 */
class TurnClock(private val clock: () -> Long = { System.currentTimeMillis() }) {

    private val start = clock()
    private var firstTokenAt: Long? = null
    private var thoughtEndedAt: Long? = null
    private var endedAt: Long? = null

    fun firstToken() {
        if (firstTokenAt == null) firstTokenAt = clock()
    }

    fun thoughtDone() {
        if (thoughtEndedAt == null) thoughtEndedAt = clock()
    }

    fun finish() {
        endedAt = clock()
    }

    fun ttftMs(): Long? = firstTokenAt?.let { it - start }

    fun thinkingMs(): Long? = thoughtEndedAt?.let { it - start }

    fun totalMs(): Long = (endedAt ?: clock()) - start
}

object Pacer {

    /** Milliseconds between ticks. Slow enough to read, fast enough not to feel stuck. */
    const val Tick = 16

    /** Roughly how many ticks an answer should take once all of it is available. */
    private const val Ticks = 70

    fun next(shown: Int, available: Int): Int {
        if (available <= shown) return shown
        val step = maxOf(1, ceil(available / Ticks.toDouble()).toInt())
        return minOf(available, shown + step)
    }

    fun done(shown: Int, available: Int, finished: Boolean): Boolean = finished && shown >= available
}

/**
 * Keeps a streaming reply from leaking tool JSON onto the screen.
 *
 * In Control mode the model may answer with `{"tool":...}`. Nothing is shown until the
 * first real character proves this is prose; a JSON reply stays hidden and is handled as
 * a command instead. Once prose is confirmed every later delta goes straight through.
 */
class StreamGate(private val sink: (String) -> Unit) {

    private val held = StringBuilder()
    private var prose = false
    private var json = false

    /** True once the reply has been recognised as a tool call. */
    val looksLikeToolCall: Boolean get() = json

    fun feed(delta: String) {
        if (delta.isEmpty() || json) return
        if (prose) {
            sink(delta)
            return
        }
        held.append(delta)
        val head = held.toString().trimStart()
        if (head.isEmpty()) return
        if (head.startsWith("{")) {
            json = true
            held.setLength(0)
            return
        }
        prose = true
        sink(held.toString())
        held.setLength(0)
    }

    /** Flushes anything still held (a reply that turned out to be blank). */
    fun finish() {
        if (!json && !prose && held.isNotEmpty()) {
            prose = true
            sink(held.toString())
        }
        held.setLength(0)
    }
}

/**
 * Thread-safe accumulation of streamed text: the network thread appends, the UI thread
 * reads snapshots.
 */
class StreamBuffer {
    private val lock = Any()
    private val text = StringBuilder()

    fun append(delta: String) {
        synchronized(lock) { text.append(delta) }
    }

    fun snapshot(): String = synchronized(lock) { text.toString() }

    val length: Int get() = synchronized(lock) { text.length }
}

/** One-way flag shared between the thread that generates and the loop that reveals. */
class StreamState {
    @Volatile
    var finished = false
        private set

    fun markFinished() {
        finished = true
    }
}
