package com.jarvis.assistant.brain

/**
 * Why an answer came from the phone even though the cloud model was switched on, in
 * words the user can act on. Null means there is nothing to explain: either the cloud
 * was never wanted, or it did the answering.
 *
 * The cloud is only used when it is switched on, the internet is allowed and the
 * settings are complete - three separate things that used to fail silently, which left
 * people watching "Answered by On-device Gemma" with no idea which one was missing.
 */
fun cloudSkipNote(
    cloudEnabled: Boolean,
    internetAllowed: Boolean,
    problem: String?,
    usedCloud: Boolean,
): String? {
    if (!cloudEnabled || usedCloud) return null
    if (!internetAllowed) {
        return "The cloud model is on, but internet access is blocked, so the phone " +
            "answered. Allow internet in Settings, then ask again."
    }
    if (problem != null) return "The cloud model is on but not ready: $problem"
    return "The cloud model is switched on but was not used for this answer."
}
