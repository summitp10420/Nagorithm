package com.nagorithm.app.effort

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.nagorithm.app.NOW
import com.nagorithm.app.activity
import com.nagorithm.app.data.StoredActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EfficiencyFactorTest {

    private fun ride(
        id: String,
        daysAgo: Long,
        meters: Double = 30_000.0,
        minutes: Long = 60,
        heartRate: Long? = 150,
        samples: Int = 100,
    ): StoredActivity = activity(
        id = id,
        daysAgo = daysAgo,
        distanceMeters = meters,
        durationMinutes = minutes,
    ).copy(averageHeartRateBpm = heartRate, hrSampleCount = samples)

    @Test
    fun `speed per beat is distance over time over heart rate`() {
        val factor = ride("a", daysAgo = 1).efficiencyFactor()!!

        assertEquals((30_000.0 / 3_600.0) / 150.0, factor, 1e-9)
    }

    @Test
    fun `the same pace at a lower heart rate scores better`() {
        val hard = ride("hard", daysAgo = 1, heartRate = 160).efficiencyFactor()!!
        val easy = ride("easy", daysAgo = 1, heartRate = 140).efficiencyFactor()!!

        assertTrue(easy > hard)
    }

    @Test
    fun `it does not care how long the session was`() {
        val short = ride("short", daysAgo = 1, meters = 15_000.0, minutes = 30).efficiencyFactor()!!
        val long = ride("long", daysAgo = 1, meters = 60_000.0, minutes = 120).efficiencyFactor()!!

        assertEquals(short, long, 1e-9)
    }

    @Test
    fun `swimming heart rate is not trusted here either`() {
        val swim = activity(
            id = "swim",
            daysAgo = 1,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
            distanceMeters = 1_500.0,
            durationMinutes = 30,
            isIndoor = true,
        ).copy(averageHeartRateBpm = 130, hrSampleCount = 50)

        assertNull(swim.efficiencyFactor())
    }

    @Test
    fun `a handful of stray samples is not an average heart rate`() {
        assertNull(ride("a", daysAgo = 1, samples = 3).efficiencyFactor())
    }

    @Test
    fun `a warm-up length session is excluded`() {
        assertNull(ride("a", daysAgo = 1, meters = 5_000.0, minutes = 15).efficiencyFactor())
    }

    @Test
    fun `going further per beat than last month reads as improvement`() {
        val trend = efficiencyTrends(
            listOf(
                ride("old1", daysAgo = 50, meters = 30_000.0),
                ride("old2", daysAgo = 40, meters = 30_000.0),
                ride("new1", daysAgo = 10, meters = 32_000.0),
                ride("new2", daysAgo = 5, meters = 32_000.0),
            ),
            now = NOW,
        ).single()

        assertTrue(trend.improvementFraction > 0)
        assertTrue(trend.isMeaningful)
        assertEquals(2, trend.recentCount)
        assertEquals(2, trend.priorCount)
    }

    @Test
    fun `one window with nothing in it produces no trend`() {
        val trends = efficiencyTrends(
            listOf(ride("new1", daysAgo = 10), ride("new2", daysAgo = 5)),
            now = NOW,
        )

        assertTrue(trends.isEmpty())
    }

    @Test
    fun `a small wobble is not called improvement`() {
        val trend = efficiencyTrends(
            listOf(
                ride("old1", daysAgo = 50, meters = 30_000.0),
                ride("old2", daysAgo = 40, meters = 30_000.0),
                ride("new1", daysAgo = 10, meters = 30_200.0),
                ride("new2", daysAgo = 5, meters = 30_200.0),
            ),
            now = NOW,
        ).single()

        assertTrue(trend.improvementFraction > 0)
        assertTrue(!trend.isMeaningful)
    }
}
