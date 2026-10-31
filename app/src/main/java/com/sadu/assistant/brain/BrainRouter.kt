package com.sadu.assistant.brain

import com.sadu.assistant.Mode
import java.io.IOException

/**
 * Picks the brain for each request.
 *
 * The cloud model is used whenever it is switched on and set up. If it fails - no
 * internet, a wrong key, a timeout - the on-device model answers instead, and the app
 * says in plain words what happened rather than showing a dead end.
 */
class BrainRouter(
    private val local: Brain,
    private val cloud: Brain,
    private val cloudWanted: () -> Boolean = { false },
) : Brain {

    /** The brain that answered the last request. */
    @Volatile
    var lastUsed: String = local.name
        private set

    /** Why the cloud was skipped or gave up, or null when it was not tried. */
    @Volatile
    var lastFallback: String? = null
        private set

    /** Whether the brain that answered last stopped at its output limit. */
    @Volatile
    override var lastReplyTruncated: Boolean = false
        private set

    override val isReady: Boolean get() = local.isReady || cloudReady()

    override val name: String get() = if (cloudReady()) cloud.name else local.name

    override fun generate(request: LlmRequest): String = generateStream(request) { }

    override fun generateStream(request: LlmRequest, onDelta: (String) -> Unit): String {
        if (cloudReady()) {
            try {
                val reply = cloud.generateStream(request, onDelta)
                lastUsed = cloud.name
                lastFallback = null
                lastReplyTruncated = cloud.lastReplyTruncated
                return reply
            } catch (e: Throwable) {
                lastFallback = reason(e)
                // Control mode never drops to the on-device model: a half-working answer
                // about a phone action is worse than a straight failure the user can read.
                if (request.mode == Mode.Control) {
                    throw IOException("Cloud model failed: $lastFallback")
                }
            }
        }
        if (!local.isReady) {
            val err = lastFallback?.let { "Cloud model failed: $it" }
                ?: "Cloud model failed and no on-device model is loaded."
            throw IOException(err)
        }
        val reply = try {
            local.generateStream(request, onDelta)
        } catch (e: IllegalStateException) {
            throw IOException(
                lastFallback?.let { "Cloud model failed: $it (and no on-device model is loaded)" }
                    ?: e.message
            )
        }
        lastUsed = local.name
        lastReplyTruncated = local.lastReplyTruncated
        return reply
    }

    private fun cloudReady(): Boolean = cloudWanted() && cloud.isReady

    private fun reason(e: Throwable): String =
        (e as? IOException)?.message ?: e.message ?: e.toString()
}
