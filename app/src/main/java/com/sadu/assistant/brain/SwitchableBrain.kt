package com.sadu.assistant.brain

/**
 * The on-device brain as seen from above, with the engine underneath it replaceable.
 *
 * [BrainRouter] is built once and holds a local brain forever; this is that local brain. When the
 * user changes engines in Settings, the delegate is swapped and nothing else in the app has to be
 * rebuilt - the same design that let Chat and Control mode move apart.
 */
class SwitchableBrain(initial: Brain) : Brain {

    @Volatile
    private var delegate: Brain = initial

    private val lock = Any()

    /** The brain answering right now. */
    val active: Brain get() = delegate

    fun switchTo(brain: Brain) {
        synchronized(lock) { delegate = brain }
    }

    override val isReady: Boolean get() = delegate.isReady

    override val name: String get() = delegate.name

    override val lastReplyTruncated: Boolean get() = delegate.lastReplyTruncated

    override fun generate(request: LlmRequest): String = delegate.generate(request)

    override fun generateStream(request: LlmRequest, onDelta: (String) -> Unit): String =
        delegate.generateStream(request, onDelta)
}
