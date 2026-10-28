package com.sadu.assistant.brain

/**
 * Why an answer came from the phone even though the cloud model was switched on, in
 * words the user can act on. Null means there is nothing to explain: the cloud was never
 * wanted, or it did the answering.
 *
 * The cloud model answers whenever it is switched on and set up - it does not also need
 * the "allow internet access" switch, which is about the phone tools. A failed call is
 * reported separately, with the reason the server gave.
 */
fun cloudSkipNote(
    cloudEnabled: Boolean,
    problem: String?,
    usedCloud: Boolean,
): String? {
    if (!cloudEnabled || usedCloud) return null
    if (problem != null) return "The cloud model is on but not ready: $problem"
    return "The cloud model is switched on, but this answer came from the phone."
}
