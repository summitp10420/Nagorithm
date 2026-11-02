package com.nagorithm.app.effort

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.nagorithm.app.activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MaxHeartRateTest {

    private val ride = activity("ride", daysAgo = 3).copy(peakHeartRateBpm = 176, hrSampleCount = 900)

    @Test
    fun `a well sampled session sets max`() {
        assertEquals(176, detectedMaxHeartRate(listOf(ride)))
    }

    @Test
    fun `a stray reading from a swim cannot raise max`() {
        val swim = activity(
            "swim",
            daysAgo = 1,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ).copy(peakHeartRateBpm = 214, hrSampleCount = 400)

        assertEquals(176, detectedMaxHeartRate(listOf(ride, swim)))
    }

    @Test
    fun `a handful of samples is a raw maximum, not a percentile, and is ignored`() {
        val sparse = activity("sparse", daysAgo = 2).copy(peakHeartRateBpm = 205, hrSampleCount = 4)

        assertEquals(176, detectedMaxHeartRate(listOf(ride, sparse)))
    }

    @Test
    fun `nothing trustworthy means nothing detected, so the age estimate stands`() {
        val sparse = activity("sparse", daysAgo = 2).copy(peakHeartRateBpm = 205, hrSampleCount = 4)

        assertNull(detectedMaxHeartRate(listOf(sparse)))
    }
}
