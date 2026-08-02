package com.nagorithm.app.verdict

/**
 * What the user actually sees, paired with the decision behind it.
 *
 * The two travel together so "Why this nag?" can never drift from the nag it explains — the
 * reasoning is the same object that produced the words, not a reconstruction of it.
 */
data class CheckIn(
    val verdict: Verdict,
    val nag: Nag,
    /**
     * The trail recorded when this wording was delivered, which includes why it was said at that
     * moment and how it came to be worded that way. Null when the card is showing a verdict that
     * was never spoken aloud, in which case the verdict's own reasons are the whole story.
     */
    val spokenReasons: List<String>? = null,
) {
    val reasons: List<String> get() = spokenReasons ?: verdict.reasons
}
