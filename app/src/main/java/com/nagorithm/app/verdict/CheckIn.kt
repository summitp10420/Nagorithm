package com.nagorithm.app.verdict

/**
 * What the user actually sees, paired with the decision behind it.
 *
 * The two travel together so "Why this nag?" can never drift from the nag it explains — the
 * reasoning is the same object that produced the words, not a reconstruction of it.
 */
data class CheckIn(val verdict: Verdict, val nag: Nag)
