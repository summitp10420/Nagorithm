package com.nagorithm.app.effort

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.nagorithm.app.activity
import com.nagorithm.app.data.StoredActivity
import com.nagorithm.app.health.SportCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CriticalSpeedTest {

    private val runKey = SportKey(SportCategory.RUNNING, isIndoor = false)

    private fun run(id: String, daysAgo: Long, meters: Double, minutes: Long): StoredActivity =
        activity(
            id = id,
            daysAgo = daysAgo,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            distanceMeters = meters,
            durationMinutes = minutes,
        )

    /** Points generated from distance = 3 m/s × time + 200 m, so the fit has a known answer. */
    private fun straightLine(): List<StoredActivity> = listOf(
        run("a", daysAgo = 20, meters = 2_000.0, minutes = 10),
        run("b", daysAgo = 14, meters = 3_800.0, minutes = 20),
        run("c", daysAgo = 8, meters = 5_600.0, minutes = 30),
    )

    @Test
    fun `recovers the speed and reserve it was given`() {
        val fit = fitCriticalSpeed(runKey, straightLine())!!

        assertEquals(FitQuality.FITTED, fit.quality)
        assertEquals(3.0, fit.criticalSpeed, 0.01)
        assertEquals(200.0, fit.reserveMeters, 1.0)
    }

    @Test
    fun `a prediction is the inverse of the fit`() {
        val fit = fitCriticalSpeed(runKey, straightLine())!!

        assertEquals(1_200.0, fit.predictSeconds(3_800.0)!!, 1.0)
        // A distance never actually run is still answerable, which is the point of fitting.
        assertEquals(1_600.0, fit.predictSeconds(5_000.0)!!, 1.0)
    }

    @Test
    fun `an easy session does not drag the curve down`() {
        val lazy = run("slow", daysAgo = 3, meters = 2_500.0, minutes = 25)
        val fit = fitCriticalSpeed(runKey, straightLine() + lazy)!!

        assertEquals(3.0, fit.criticalSpeed, 0.01)
    }

    @Test
    fun `one session is not a curve`() {
        assertNull(fitCriticalSpeed(runKey, listOf(run("a", 5, 5_000.0, 30))))
    }

    @Test
    fun `sessions of the same length cannot separate speed from reserve`() {
        val fit = fitCriticalSpeed(
            runKey,
            listOf(
                run("a", daysAgo = 10, meters = 3_000.0, minutes = 20),
                run("b", daysAgo = 5, meters = 3_200.0, minutes = 21),
            ),
        )!!

        assertEquals(FitQuality.APPROXIMATE, fit.quality)
        assertEquals(0.0, fit.reserveMeters, 0.001)
        assertTrue(fit.criticalSpeed > 0.0)
    }

    @Test
    fun `a distance inside the reserve has no meaningful prediction`() {
        val fit = fitCriticalSpeed(runKey, straightLine())!!

        assertNull(fit.predictSeconds(100.0))
    }

    @Test
    fun `warm-ups are kept out of the fit`() {
        val fit = fitCriticalSpeed(
            runKey,
            straightLine() + run("jog", daysAgo = 1, meters = 900.0, minutes = 5),
        )!!

        assertEquals(3.0, fit.criticalSpeed, 0.01)
    }

    @Test
    fun `road and treadmill are fitted apart`() {
        val fits = criticalSpeedFits(
            straightLine() + activity(
                id = "mill",
                daysAgo = 4,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
                distanceMeters = 5_000.0,
                durationMinutes = 30,
                isIndoor = true,
            ),
        )

        assertNotNull(fits[runKey])
        // The treadmill has only one session of its own, so it gets no curve rather than
        // borrowing the road one.
        assertNull(fits[SportKey(SportCategory.RUNNING, isIndoor = true)])
    }

    @Test
    fun `a session is never included in the curve it is judged against`() {
        val breakthrough = run("fast", daysAgo = 1, meters = 3_800.0, minutes = 16)
        val comparison = compareToCurve(breakthrough, straightLine() + breakthrough)!!

        // The straight line predicts 1200s for 3800m; being in its own baseline would have
        // pulled that prediction toward the 960s actually run.
        assertEquals(1_200.0, comparison.predictedSeconds, 1.0)
        assertEquals(960.0, comparison.actualSeconds, 1.0)
        assertEquals(240.0, comparison.secondsFasterThanPredicted, 1.0)
        assertTrue(comparison.isMeaningful)
    }

    @Test
    fun `a session slower than the curve is reported as slower`() {
        val bad = run("bad", daysAgo = 1, meters = 3_800.0, minutes = 24)
        val comparison = compareToCurve(bad, straightLine() + bad)!!

        assertTrue(comparison.secondsFasterThanPredicted < 0)
        assertTrue(comparison.fractionFasterThanPredicted < 0)
    }

    @Test
    fun `two points fit perfectly and therefore prove nothing`() {
        val fit = fitCriticalSpeed(
            runKey,
            listOf(
                run("a", daysAgo = 20, meters = 2_000.0, minutes = 10),
                run("b", daysAgo = 14, meters = 3_800.0, minutes = 20),
            ),
        )!!

        assertEquals(FitQuality.FITTED, fit.quality)
        assertFalse(fit.isWellConstrained)
    }

    @Test
    fun `three points are enough to be believed`() {
        assertTrue(fitCriticalSpeed(runKey, straightLine())!!.isWellConstrained)
    }

    @Test
    fun `a distance far outside the fitted range is not covered`() {
        val fit = fitCriticalSpeed(runKey, straightLine())!!

        assertTrue(fit.covers(3_800.0))
        assertFalse(fit.covers(800.0))
        assertFalse(fit.covers(21_000.0))
    }

    @Test
    fun `a comparison from a thin curve is not confident`() {
        val breakthrough = run("fast", daysAgo = 1, meters = 3_800.0, minutes = 16)
        val thin = listOf(
            run("a", daysAgo = 20, meters = 2_000.0, minutes = 10),
            run("b", daysAgo = 14, meters = 5_600.0, minutes = 30),
        )

        val comparison = compareToCurve(breakthrough, thin + breakthrough)!!

        assertTrue(comparison.secondsFasterThanPredicted > 0)
        assertFalse(comparison.isConfident)
    }

    @Test
    fun `a comparison from a full curve inside its range is confident`() {
        val breakthrough = run("fast", daysAgo = 1, meters = 3_800.0, minutes = 16)

        assertTrue(compareToCurve(breakthrough, straightLine() + breakthrough)!!.isConfident)
    }

    @Test
    fun `a reference distance is a round number near the usual one`() {
        val reference = referenceDistance(straightLine(), SportCategory.RUNNING)!!

        assertEquals(3_800.0, reference, 100.0)
    }
}
