package com.nagorithm.app.verdict

import com.nagorithm.app.NOW
import com.nagorithm.app.activity
import com.nagorithm.app.daysAgo
import com.nagorithm.app.data.SpokenNag
import com.nagorithm.app.data.StoredActivity
import com.nagorithm.app.effort.EffortSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class NagScheduleTest {

    /** Fixed so "waking hours" means the same thing wherever the tests are run. */
    private val zone: ZoneId = ZoneId.of("UTC")

    private val midMorning: Instant = Instant.parse("2026-08-01T10:00:00Z")

    private fun verdict(
        state: VerdictState,
        news: List<StoredActivity> = emptyList(),
    ): Verdict {
        val activities = listOf(activity("h1", daysAgo = 20), activity("h2", daysAgo = 10))
        return Verdict(
            state = state,
            computedAt = midMorning,
            goal = null,
            goalProgress = null,
            news = news,
            headline = null,
            efficiency = null,
            detraining = DetrainingBand.DRIFTING,
            daysSinceLastActivity = 10,
            acwr = null,
            snapshot = EffortSnapshot.from(activities, midMorning),
            reasons = emptyList(),
        )
    }

    private fun spoke(
        hoursAgo: Long,
        state: VerdictState = VerdictState.STEADY,
    ) = SpokenNag(
        spokenAtEpochMillis = midMorning.minus(hoursAgo, ChronoUnit.HOURS).toEpochMilli(),
        state = state,
        headline = "",
        body = "",
        source = NagSource.TEMPLATE,
        reasons = "",
        notified = true,
    )

    private fun decide(
        verdict: Verdict,
        last: SpokenNag?,
        now: Instant = midMorning,
    ) = NagSchedule.decide(verdict, last, now, zone)

    @Test
    fun `nothing has ever been said, so say something`() {
        assertTrue(decide(verdict(VerdictState.STEADY), last = null).speak)
    }

    @Test
    fun `nobody gets nagged at three in the morning`() {
        val decision = decide(
            verdict(VerdictState.IDLE),
            last = spoke(hoursAgo = 200),
            now = Instant.parse("2026-08-01T03:00:00Z"),
        )

        assertFalse(decision.speak)
        assertTrue(decision.reason.contains("waking"))
    }

    @Test
    fun `nor late in the evening`() {
        assertFalse(
            decide(
                verdict(VerdictState.IDLE),
                last = spoke(hoursAgo = 200),
                now = Instant.parse("2026-08-01T22:00:00Z"),
            ).speak,
        )
    }

    @Test
    fun `a fresh session is worth interrupting for`() {
        val session = activity("new", daysAgo = 0, firstSeenDaysAgo = 0, now = midMorning)

        assertTrue(decide(verdict(VerdictState.PROGRESSING, listOf(session)), spoke(30)).speak)
    }

    @Test
    fun `but not twice in one day`() {
        val session = activity("new", daysAgo = 0, firstSeenDaysAgo = 0, now = midMorning)
        val decision = decide(verdict(VerdictState.PROGRESSING, listOf(session)), spoke(4))

        assertFalse(decision.speak)
        assertTrue(decision.reason.contains("Already spoke"))
    }

    @Test
    fun `a session already covered by the last nag is not news again`() {
        // Seen three days ago; the last nag went out two days ago, so it was already reported.
        val session = activity("old", daysAgo = 3, firstSeenDaysAgo = 3, now = midMorning)

        assertFalse(decide(verdict(VerdictState.STEADY, listOf(session)), spoke(48)).speak)
    }

    @Test
    fun `doing nothing gets chased every couple of days`() {
        assertFalse(decide(verdict(VerdictState.IDLE), spoke(30, VerdictState.IDLE)).speak)
        assertTrue(decide(verdict(VerdictState.IDLE), spoke(50, VerdictState.IDLE)).speak)
    }

    @Test
    fun `crossing into idle is worth saying immediately`() {
        val decision = decide(
            verdict(VerdictState.IDLE),
            spoke(hoursAgo = 24, state = VerdictState.RESTING),
        )

        assertTrue(decision.speak)
        assertTrue(decision.reason.contains("into idle"))
    }

    @Test
    fun `an uneventful week still gets a check-in eventually`() {
        assertFalse(decide(verdict(VerdictState.STEADY), spoke(hoursAgo = 24 * 5)).speak)
        assertTrue(decide(verdict(VerdictState.STEADY), spoke(hoursAgo = 24 * 8)).speak)
    }

    @Test
    fun `a broken feed is reported weekly, not every two days`() {
        assertFalse(decide(verdict(VerdictState.FEED_SILENT), spoke(hoursAgo = 24 * 3)).speak)
        assertTrue(decide(verdict(VerdictState.FEED_SILENT), spoke(hoursAgo = 24 * 8)).speak)
    }

    @Test
    fun `a cold start is left alone rather than nagged into existence`() {
        assertFalse(decide(verdict(VerdictState.COLD_START), spoke(hoursAgo = 24 * 3)).speak)
    }

    @Test
    fun `the daily slot does not walk into the evening`() {
        // Spoken at 4pm yesterday. Twenty hours later is noon, still inside waking hours, so a
        // twenty-four hour gate would push each day's nag an hour later than the last.
        val yesterdayAfternoon = Instant.parse("2026-07-31T16:00:00Z")
        val session = activity("new", daysAgo = 0, firstSeenDaysAgo = 0, now = NOW)

        val decision = NagSchedule.decide(
            verdict = verdict(VerdictState.STEADY, listOf(session)),
            last = SpokenNag(
                spokenAtEpochMillis = yesterdayAfternoon.toEpochMilli(),
                state = VerdictState.STEADY,
                headline = "",
                body = "",
                source = NagSource.TEMPLATE,
                reasons = "",
                notified = true,
            ),
            now = Instant.parse("2026-08-01T12:00:00Z"),
            zone = zone,
        )

        assertTrue(decision.speak)
    }
}
