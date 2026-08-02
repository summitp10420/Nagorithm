package com.nagorithm.app.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NagGuardTest {

    /** Only ever the user's own words. Nothing the app computed is quotable. */
    private val theirWords = listOf(
        "sub-3 marathon",
        "Cutting sugar, feeling stronger lately.",
    )

    private fun check(candidate: String) = NagGuard.check(candidate, theirWords)

    @Test
    fun `prose with no figures is what this is for`() {
        val result = check(
            "You swam quicker than you had any right to, and it came after the row. That is not " +
                "luck. Go again.",
        )

        assertTrue(result.reason, result.accepted)
    }

    @Test
    fun `an invented statistic is caught`() {
        val result = check(
            "Great work — you are up 15% on the month and the engine is coming back nicely now.",
        )

        assertFalse(result.accepted)
        assertTrue(result.reason, result.reason.contains("15"))
    }

    /**
     * The failure that motivated all of this. A pace looks like a fact, reads like authority, and
     * the app has no way to confirm the model did the division correctly.
     */
    @Test
    fun `a split is rejected even though the app knows it is true`() {
        val result = check(
            "That row came in at 2:01/500m, right in line with where you have been sitting all " +
                "month. Consistent, no red flags at all.",
        )

        assertFalse(result.accepted)
        assertTrue(result.reason, result.reason.contains("2:01"))
    }

    @Test
    fun `an accurate figure is rejected just the same, because accuracy was never the test`() {
        assertFalse(check("You are carrying 412 HRSS a week now, which is real fitness.").accepted)
    }

    @Test
    fun `numbers said as words are the whole point`() {
        assertTrue(
            check("Three sessions this week and the swim was your quickest yet. Keep going.")
                .accepted,
        )
    }

    /** Their goal is theirs. Quoting it back must not be an offence. */
    @Test
    fun `digits from the user's own goal survive being quoted`() {
        val result = check(
            "That sub-3 marathon is not going to happen from the sofa, and you know it as well " +
                "as I do. Get out there.",
        )

        assertTrue(result.reason, result.accepted)
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
    fun `a trailing full stop is not mistaken for part of a figure`() {
        val result = check(
            "You have trained on four separate days this week, which is more than most people " +
                "manage in a month. That counts for something.",
        )

        assertTrue(result.reason, result.accepted)
    }
}
