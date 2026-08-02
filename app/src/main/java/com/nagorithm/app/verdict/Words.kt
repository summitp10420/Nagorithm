package com.nagorithm.app.verdict

import kotlin.math.abs

/**
 * Quantities, in words.
 *
 * Nothing the user reads contains a digit. Partly that is taste — a line about an acute-to-chronic
 * ratio is neither funny nor useful, and someone who wants the chart already has Garmin open — and
 * partly it is the only cheap answer to a problem with no other solution. A figure written by a
 * language model has to be checked against something, and checking every number it might produce
 * means reimplementing the model's arithmetic. Removing the figures removes the obligation.
 *
 * The arithmetic is not lost or softened. It sits in the "Why this nag?" audit exactly as computed,
 * where anyone who wants to argue with it can. What changes is that it is no longer the message.
 */
internal object Words {

    /** When something happened, as a person would place it. */
    fun days(days: Long?): String = when {
        days == null -> "at some point"
        days <= 0L -> "today"
        days == 1L -> "yesterday"
        days <= 3L -> "a couple of days ago"
        days <= 6L -> "earlier this week"
        days <= 9L -> "about a week ago"
        days <= 16L -> "a fortnight ago"
        days <= 24L -> "the best part of a month ago"
        days <= 45L -> "over a month ago"
        else -> "months ago"
    }

    /** How long something lasted. "A fortnight" and "a fortnight ago" are not interchangeable. */
    fun span(days: Long?): String = when {
        days == null -> "a while"
        days <= 1L -> "a day"
        days <= 3L -> "a couple of days"
        days <= 6L -> "most of a week"
        days <= 9L -> "a week"
        days <= 16L -> "a fortnight"
        days <= 24L -> "three weeks or so"
        days <= 45L -> "a month"
        else -> "a few months"
    }

    fun count(n: Int): String = when (n) {
        0 -> "nothing"
        1 -> "one"
        2 -> "two"
        3 -> "three"
        4 -> "four"
        5 -> "five"
        6 -> "six"
        7 -> "seven"
        in 8..11 -> "a good few"
        else -> "a dozen or more"
    }

    fun sessions(n: Int): String = when (n) {
        0 -> "nothing"
        1 -> "one session"
        else -> "${count(n)} sessions"
    }

    /**
     * How much of a change is worth claiming out loud.
     *
     * Under a couple of percent is inside the noise of a wrist sensor and a bad night's sleep, so
     * "barely" is the honest word for it. Calling that an improvement is how an app stops being
     * believed about the improvements that are real.
     */
    fun magnitude(fraction: Double): String {
        val size = abs(fraction)
        return when {
            size.isNaN() -> "somehow"
            size < 0.02 -> "barely"
            size < 0.05 -> "slightly"
            size < 0.10 -> "noticeably"
            size < 0.20 -> "clearly"
            else -> "dramatically"
        }
    }

    /** Weekly load as a register rather than a score, since the score means nothing to anyone. */
    fun weekly(loadPerWeek: Double): String = when {
        loadPerWeek < 60.0 -> "very light"
        loadPerWeek < 150.0 -> "light"
        loadPerWeek < 300.0 -> "moderate"
        loadPerWeek < 500.0 -> "serious"
        else -> "heavy"
    }

    fun capitalise(text: String): String = text.replaceFirstChar(Char::uppercaseChar)
}
