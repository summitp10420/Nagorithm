package com.nagorithm.app.verdict

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.nagorithm.app.NOW
import com.nagorithm.app.activity
import com.nagorithm.app.daysAgo
import com.nagorithm.app.data.Goal
import com.nagorithm.app.data.GoalBaseline
import com.nagorithm.app.data.GoalDimension
import com.nagorithm.app.data.StoredActivity
import com.nagorithm.app.effort.EffortSnapshot
import com.nagorithm.app.health.SportCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerdictEngineTest {

    private fun evaluate(
        activities: List<StoredActivity>,
        goal: Goal? = null,
        lastCheckInDaysAgo: Long? = 3,
        feedAlive: Boolean? = true,
    ) = VerdictEngine.evaluate(
        activities = activities,
        snapshot = EffortSnapshot.from(activities, NOW),
        goal = goal,
        lastCheckInEpochMillis = lastCheckInDaysAgo?.let { NOW.daysAgo(it).toEpochMilli() },
        feedAlive = feedAlive,
        now = NOW,
    )

    /**
     * A month of riding at a steady 30 km/h across several durations, so a speed curve can be
     * fitted and tests only have to add the one session they are actually about.
     */
    private fun history(): List<StoredActivity> = listOf(
        activity("r1", daysAgo = 26, distanceMeters = 20_000.0, durationMinutes = 40),
        activity("r2", daysAgo = 20, distanceMeters = 40_000.0, durationMinutes = 80),
        activity("r3", daysAgo = 14, distanceMeters = 60_000.0, durationMinutes = 120),
        activity("r4", daysAgo = 9, distanceMeters = 30_000.0, durationMinutes = 60),
    )

    @Test
    fun `silence from Health Connect is not blamed on the user`() {
        val verdict = evaluate(history(), feedAlive = false)

        assertEquals(VerdictState.FEED_SILENT, verdict.state)
        assertTrue(verdict.reasons.any { it.contains("feed itself is suspect") })
    }

    @Test
    fun `an idle week with a working feed is the user's problem`() {
        val verdict = evaluate(history(), feedAlive = true)

        assertEquals(VerdictState.IDLE, verdict.state)
        assertEquals(DetrainingBand.DRIFTING, verdict.detraining)
    }

    @Test
    fun `a fortnight off is called losing fitness`() {
        val verdict = evaluate(
            listOf(activity("r1", daysAgo = 26), activity("r2", daysAgo = 15)),
        )

        assertEquals(VerdictState.IDLE, verdict.state)
        assertEquals(DetrainingBand.LOSING, verdict.detraining)
    }

    @Test
    fun `barely any history means no verdict is offered`() {
        assertEquals(VerdictState.COLD_START, evaluate(listOf(activity("a", daysAgo = 2))).state)
    }

    @Test
    fun `ratios are suppressed until there is a block to divide by`() {
        val verdict = evaluate(listOf(activity("a", daysAgo = 2), activity("b", daysAgo = 1)))

        assertNull(verdict.acwr)
        assertTrue(verdict.reasons.any { it.contains("ratios suppressed") })
    }

    @Test
    fun `beating the fitted curve is progress`() {
        val verdict = evaluate(
            history() + activity("new", daysAgo = 1, distanceMeters = 40_000.0, durationMinutes = 72),
        )

        assertEquals(VerdictState.PROGRESSING, verdict.state)
        assertTrue(verdict.headline!!.secondsFasterThanPredicted > 0)
    }

    @Test
    fun `a session at an unrepeated distance is still judged`() {
        // 47 km has never been ridden before, which the old bucketing would have discarded.
        val verdict = evaluate(
            history() + activity("odd", daysAgo = 1, distanceMeters = 47_000.0, durationMinutes = 84),
        )

        assertEquals(VerdictState.PROGRESSING, verdict.state)
        assertEquals(47_000.0, verdict.headline!!.activity.distanceMeters!!, 1.0)
    }

    @Test
    fun `matching the curve is steady, not a celebration`() {
        val verdict = evaluate(
            history() + activity("new", daysAgo = 1, distanceMeters = 40_000.0, durationMinutes = 80),
        )

        assertEquals(VerdictState.STEADY, verdict.state)
    }

    @Test
    fun `a session slower than the curve is not called progress`() {
        val verdict = evaluate(
            history() + activity("new", daysAgo = 1, distanceMeters = 40_000.0, durationMinutes = 92),
        )

        assertEquals(VerdictState.STEADY, verdict.state)
        assertTrue(verdict.headline!!.secondsFasterThanPredicted < 0)
    }

    @Test
    fun `three hard sessions on a thin base is a warning, not applause`() {
        // Enough history that the ratio is allowed to speak at all: a chronic window that is full
        // rather than merely elapsed, and enough sessions in it that one of them moving does not
        // decide the answer.
        val base = listOf(
            activity("h0", daysAgo = 40, hrss = 40.0, distanceMeters = 20_000.0, durationMinutes = 40),
            activity("h1", daysAgo = 27, hrss = 40.0, distanceMeters = 20_000.0, durationMinutes = 40),
            activity("h2", daysAgo = 25, hrss = 40.0, distanceMeters = 40_000.0, durationMinutes = 80),
            activity("h3", daysAgo = 23, hrss = 40.0, distanceMeters = 60_000.0, durationMinutes = 120),
            activity("h4", daysAgo = 21, hrss = 40.0, distanceMeters = 30_000.0, durationMinutes = 60),
            activity("h5", daysAgo = 19, hrss = 40.0, distanceMeters = 30_000.0, durationMinutes = 60),
            activity("h6", daysAgo = 17, hrss = 40.0, distanceMeters = 30_000.0, durationMinutes = 60),
            activity("h7", daysAgo = 15, hrss = 40.0, distanceMeters = 30_000.0, durationMinutes = 60),
        )
        val burst = listOf(
            activity("b1", daysAgo = 2, hrss = 200.0, distanceMeters = 35_000.0, durationMinutes = 60),
            activity("b2", daysAgo = 1, hrss = 200.0, distanceMeters = 35_000.0, durationMinutes = 60),
            activity("b3", daysAgo = 0, hrss = 200.0, distanceMeters = 35_000.0, durationMinutes = 60),
        )

        val verdict = evaluate(base + burst)

        assertEquals(VerdictState.RAMPING_HARD, verdict.state)
        assertTrue(verdict.acwr!! > 1.5)
    }

    @Test
    fun `a big ratio on a tiny absolute load is not a warning`() {
        // Same shape as the ramping test, an order of magnitude down. Two hours of moderate work
        // in a week is not something to be told off for, whatever the ratio says.
        val base = listOf(
            activity("h0", daysAgo = 40, hrss = 8.0, distanceMeters = 20_000.0, durationMinutes = 40),
            activity("h1", daysAgo = 27, hrss = 8.0, distanceMeters = 20_000.0, durationMinutes = 40),
            activity("h2", daysAgo = 25, hrss = 8.0, distanceMeters = 40_000.0, durationMinutes = 80),
            activity("h3", daysAgo = 23, hrss = 8.0, distanceMeters = 60_000.0, durationMinutes = 120),
            activity("h4", daysAgo = 21, hrss = 8.0, distanceMeters = 30_000.0, durationMinutes = 60),
            activity("h5", daysAgo = 19, hrss = 8.0, distanceMeters = 30_000.0, durationMinutes = 60),
            activity("h6", daysAgo = 17, hrss = 8.0, distanceMeters = 30_000.0, durationMinutes = 60),
            activity("h7", daysAgo = 15, hrss = 8.0, distanceMeters = 30_000.0, durationMinutes = 60),
        )
        val burst = listOf(
            activity("b1", daysAgo = 2, hrss = 40.0, distanceMeters = 35_000.0, durationMinutes = 60),
            activity("b2", daysAgo = 1, hrss = 40.0, distanceMeters = 35_000.0, durationMinutes = 60),
        )

        val verdict = evaluate(base + burst)

        assertTrue(verdict.acwr!! > 1.5)
        assertTrue(verdict.state != VerdictState.RAMPING_HARD)
    }

    /**
     * The bike-ride regression. Three weeks of tracking against a genuinely hard week produced a
     * ratio of two and a half and a warning, because the twenty-eight day denominator was averaging
     * over days that were never recorded. Silence is the only honest output here.
     */
    @Test
    fun `a ramp is not called until the chronic window is actually full`() {
        val recent = listOf(
            activity("r1", daysAgo = 20, hrss = 40.0, distanceMeters = 20_000.0, durationMinutes = 40),
            activity("r2", daysAgo = 18, hrss = 40.0, distanceMeters = 20_000.0, durationMinutes = 40),
            activity("r3", daysAgo = 16, hrss = 40.0, distanceMeters = 20_000.0, durationMinutes = 40),
            activity("r4", daysAgo = 14, hrss = 40.0, distanceMeters = 20_000.0, durationMinutes = 40),
            activity("b1", daysAgo = 2, hrss = 200.0, distanceMeters = 35_000.0, durationMinutes = 60),
            activity("b2", daysAgo = 1, hrss = 200.0, distanceMeters = 35_000.0, durationMinutes = 60),
            activity("b3", daysAgo = 0, hrss = 200.0, distanceMeters = 35_000.0, durationMinutes = 60),
        )

        val verdict = evaluate(recent)

        assertEquals(null, verdict.acwr)
        assertTrue(verdict.state != VerdictState.RAMPING_HARD)
    }

    @Test
    fun `a session that synced late still counts as news`() {
        val late = activity("late", daysAgo = 6, firstSeenDaysAgo = 0)
        val verdict = evaluate(history() + late, lastCheckInDaysAgo = 3)

        assertTrue(verdict.hasNews)
        assertEquals("late", verdict.news.single().recordId)
    }

    @Test
    fun `old news does not hide a lapse from someone who never opens the app`() {
        // Acknowledged once a month ago and read only notifications since, so every session in
        // the history is still "news" — but the newest is nine days old.
        val verdict = evaluate(history(), lastCheckInDaysAgo = 30)

        assertTrue(verdict.hasNews)
        assertEquals(VerdictState.IDLE, verdict.state)
        assertTrue(verdict.reasons.any { it.contains("older than the recovery window") })
    }

    @Test
    fun `a late sync inside the recovery window is still talked about, not called idle`() {
        val late = activity("late", daysAgo = 6, firstSeenDaysAgo = 0)
        val verdict = evaluate(history() + late, lastCheckInDaysAgo = 3)

        assertEquals(DetrainingBand.FRESH, verdict.detraining)
        assertTrue(verdict.state != VerdictState.IDLE)
    }

    @Test
    fun `detraining tolerance shrinks as there is more to lose`() {
        assertEquals(DetrainingBand.FRESH, VerdictEngine.detrainingBand(4, loadPerWeek = 50.0))
        assertEquals(DetrainingBand.DRIFTING, VerdictEngine.detrainingBand(4, loadPerWeek = 500.0))
    }

    @Test
    fun `a pace goal is a predicted time over the distance it started on`() {
        val swims = listOf(
            swim("swim1", daysAgo = 25, meters = 1_000.0, minutes = 22),
            swim("swim2", daysAgo = 20, meters = 1_500.0, minutes = 33),
            swim("swim3", daysAgo = 2, meters = 1_000.0, minutes = 19),
            // A ride in the same window must not become the swim goal's yardstick.
            activity("ride", daysAgo = 3),
        )

        val goal = Goal(
            text = "Swim a faster 1000",
            dimension = GoalDimension.PACE,
            sport = SportCategory.SWIMMING,
            createdAtEpochMillis = NOW.daysAgo(24).toEpochMilli(),
            start = GoalBaseline(
                value = 22 * 60.0,
                unit = "s",
                bucketMeters = 1_000.0,
                capturedAtEpochMillis = NOW.daysAgo(24).toEpochMilli(),
            ),
        )

        val progress = evaluate(swims, goal = goal).goalProgress!!

        assertEquals(1_000.0, progress.bucketMeters!!, 1.0)
        assertTrue(progress.currentValue < 22 * 60.0)
        // Faster is better for pace, so the improvement must come out positive.
        assertTrue(progress.improvementFraction > 0)
        assertTrue(progress.hasMoved)
    }

    @Test
    fun `a distance goal tracks the longest effort, not the quickest`() {
        val goal = Goal(
            text = "Ride a century",
            dimension = GoalDimension.DISTANCE,
            sport = SportCategory.CYCLING,
            createdAtEpochMillis = NOW.daysAgo(30).toEpochMilli(),
            start = GoalBaseline(
                value = 40_000.0,
                unit = "m",
                capturedAtEpochMillis = NOW.daysAgo(30).toEpochMilli(),
            ),
        )

        val progress = evaluate(
            history() + activity("long", daysAgo = 1, distanceMeters = 90_000.0, durationMinutes = 200),
            goal = goal,
        ).goalProgress!!

        assertEquals(90_000.0, progress.currentValue, 1.0)
        assertEquals(1.25, progress.improvementFraction, 0.01)
    }

    @Test
    fun `a goal with nothing recorded yet reports no progress rather than inventing it`() {
        val goal = Goal(
            text = "Get going",
            dimension = GoalDimension.VOLUME,
            createdAtEpochMillis = NOW.toEpochMilli(),
        )

        val verdict = evaluate(history(), goal = goal)

        assertNull(verdict.goalProgress)
        assertTrue(verdict.reasons.any { it.contains("Get going") })
    }

    @Test
    fun `every verdict explains itself`() {
        val verdict = evaluate(
            history() + activity("new", daysAgo = 1, distanceMeters = 40_000.0, durationMinutes = 80),
        )

        assertFalse(verdict.reasons.isEmpty())
        assertTrue(verdict.reasons.first().startsWith("Verdict:"))
    }

    private fun swim(id: String, daysAgo: Long, meters: Double, minutes: Long): StoredActivity =
        activity(
            id = id,
            daysAgo = daysAgo,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
            distanceMeters = meters,
            durationMinutes = minutes,
            isIndoor = true,
        )
}
