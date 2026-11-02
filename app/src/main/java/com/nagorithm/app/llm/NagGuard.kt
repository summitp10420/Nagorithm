package com.nagorithm.app.llm

data class GuardResult(val accepted: Boolean, val reason: String)

/**
 * Checks that the model wrote prose and not statistics.
 *
 * An earlier version of this tried to verify arithmetic: every figure in the answer had to trace
 * back to one the verdict supplied, with tolerances for rounding. It worked, and it was the wrong
 * shape of problem. Verifying a model's numbers means anticipating every number it might derive —
 * a pace from a distance and a time, a percentage from two loads, a split from a total — and each
 * new one is another rule. The check can never be finished, only extended.
 *
 * Forbidding figures altogether ends the argument. There is nothing to verify because there is
 * nothing being claimed numerically, and the check collapses to a search for digits. The exact
 * numbers still exist and are still shown, in the audit trail, computed by the app that can be held
 * responsible for them.
 *
 * Digits appearing in the user's own words are the exception. A goal of "sub-3 marathon" has to
 * survive being quoted back at them.
 */
object NagGuard {

    /**
     * Long enough to say something, short enough that the shade truncates nothing that mattered.
     * The ceiling is generous because a character in full flow is allowed a paragraph; it exists to
     * catch a model that has started narrating rather than to enforce a house style.
     */
    private const val MIN_LENGTH = 30
    private const val MAX_LENGTH = 1_400

    /** A run of digits and the punctuation that binds them, so "2:01/500m" is caught whole. */
    private val FIGURE = Regex("""\d[\d.,:/]*""")

    private const val TRIMMED = ".,:/"

    fun check(candidate: String, quotable: List<String>): GuardResult {
        val text = candidate.trim()

        if (text.length < MIN_LENGTH) return GuardResult(false, "Too short to be a real answer.")
        if (text.length > MAX_LENGTH) return GuardResult(false, "Ran long past what was asked for.")

        // Whole figures only. A substring test would let a goal of "sub-20 5k" excuse a 2, a 0 and
        // a 5 as well, which together cover most of what a model might invent.
        val theirFigures = quotable.flatMap(::figuresIn).toSet()
        val figures = figuresIn(text)
            .filterNot { it in theirFigures }
            .distinct()

        return if (figures.isEmpty()) {
            GuardResult(true, "Written in words, with no figures of its own.")
        } else {
            GuardResult(
                false,
                "Quoted ${figures.joinToString()} when the message should carry no figures at all.",
            )
        }
    }

    private fun figuresIn(text: String): List<String> =
        FIGURE.findAll(text).map { it.value.trimEnd(*TRIMMED.toCharArray()) }.toList()
}
