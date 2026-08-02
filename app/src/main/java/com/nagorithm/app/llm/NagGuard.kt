package com.nagorithm.app.llm

import kotlin.math.abs
import kotlin.math.max

data class GuardResult(val accepted: Boolean, val reason: String)

/**
 * Checks that the model only rephrased, and did not quietly start doing arithmetic.
 *
 * A language model asked to sound motivating will invent an encouraging statistic without being
 * asked, and a fabricated "you are up 15%" is worse than no message at all: it is indistinguishable
 * from the real figures sitting beside it, and one caught invention discredits every honest number
 * the app has ever shown. So every digit in the output has to trace back to one the verdict
 * supplied.
 *
 * Rounding is allowed, because "24%" for 24.2 is a rephrasing rather than a claim. Anything that
 * cannot be matched to a supplied figure sends the whole message back and the template goes out
 * instead.
 */
object NagGuard {

    /** Long enough to say something, short enough to survive a notification shade. */
    private const val MIN_LENGTH = 30
    private const val MAX_LENGTH = 900

    /** Absolute slack, so 24.2 may be said as 24. */
    private const val ROUNDING_SLACK = 0.5

    /** Relative slack, so 1162 may be said as 1160. */
    private const val RELATIVE_SLACK = 0.005

    private val NUMBER = Regex("""\d+(?:\.\d+)?""")
    private val THOUSANDS_SEPARATOR = Regex("""(?<=\d),(?=\d)""")

    fun check(candidate: String, supplied: List<String>): GuardResult {
        val text = candidate.trim()

        if (text.length < MIN_LENGTH) return GuardResult(false, "Too short to be a real answer.")
        if (text.length > MAX_LENGTH) return GuardResult(false, "Ran long past what was asked for.")

        val allowed = supplied.flatMap(::numbersIn)
        val invented = numbersIn(text).filterNot { it.isJustifiedBy(allowed) }

        return if (invented.isEmpty()) {
            GuardResult(true, "Every figure traces back to the verdict.")
        } else {
            GuardResult(
                false,
                "Quoted ${invented.joinToString()} which the verdict never supplied.",
            )
        }
    }

    /**
     * Times like 19:22 come apart into 19 and 22, which is fine — the same happens to the figures
     * the verdict supplied, so the two sides are compared on equal terms.
     */
    fun numbersIn(text: String): List<Double> =
        NUMBER.findAll(text.replace(THOUSANDS_SEPARATOR, ""))
            .mapNotNull { it.value.toDoubleOrNull() }
            .toList()

    private fun Double.isJustifiedBy(allowed: List<Double>): Boolean = allowed.any {
        abs(this - it) <= max(ROUNDING_SLACK, abs(it) * RELATIVE_SLACK)
    }
}
