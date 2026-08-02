package com.nagorithm.app.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NagGuardTest {

    private val supplied = listOf(
        "That 914 m swim came in at 19:22 against a predicted 25:32.",
        "Last seven days: 6 sessions, 4 active days, 126 HRSS.",
        "You were 24.2% better than your own form predicts.",
    )

    private fun check(candidate: String) = NagGuard.check(candidate, supplied)

    @Test
    fun `a faithful rewrite is accepted`() {
        val result = check(
            "Nineteen twenty-two for 914 m, against the 25:32 your curve had you down for. " +
                "Six sessions and 126 HRSS in the last week.",
        )

        assertTrue(result.reason, result.accepted)
    }

    @Test
    fun `an invented statistic is caught`() {
        val result = check(
            "Great work — you are up 15% on the month and your 914 m swim came in at 19:22.",
        )

        assertFalse(result.accepted)
        assertTrue(result.reason.contains("15"))
    }

    @Test
    fun `rounding a supplied figure is a rephrasing, not an invention`() {
        assertTrue(check("You came in 24% quicker than predicted over 914 m.").accepted)
    }

    @Test
    fun `saying numbers as words is fine`() {
        assertTrue(
            check("Six sessions in seven days, and the swim was your quickest yet.").accepted,
        )
    }

    @Test
    fun `a plausible but unsupplied number is still rejected`() {
        // 400 is a perfectly reasonable weekly load. It is also not this user's.
        assertFalse(check("You are carrying 400 HRSS a week now, which is real fitness.").accepted)
    }

    @Test
    fun `an empty or clipped answer is rejected`() {
        assertFalse(check("Nice one.").accepted)
    }

    @Test
    fun `an essay is rejected`() {
        assertFalse(check("Swimming is a wonderful sport. ".repeat(50)).accepted)
    }

    @Test
    fun `a rewrite with no numbers at all is fine`() {
        assertTrue(check("You swam quicker than you had any right to. Go again.").accepted)
    }

    @Test
    fun `thousands separators do not read as two numbers`() {
        val result = NagGuard.check(
            "You have logged 1,240 HRSS since you started, which is not nothing.",
            listOf("You have logged 1240 HRSS since you started."),
        )

        assertTrue(result.reason, result.accepted)
    }
}
