package com.nagorithm.app.effort

import com.nagorithm.app.data.LoadModel
import com.nagorithm.app.data.LoadScore
import com.nagorithm.app.data.Profile
import com.nagorithm.app.data.Sex
import com.nagorithm.app.health.HeartRateSample
import com.nagorithm.app.health.SportCategory
import java.time.Duration
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Banister TRIMP, summed per heart-rate sample rather than applied to the session average.
 * Averaging first understates variable efforts — the same flaw Normalized Power exists to fix —
 * because load rises roughly with the square of intensity.
 */
object HrssCalculator {

    /**
     * Gaps longer than this are treated as a pause rather than sustained effort. Without the
     * clamp, a ride paused at a cafe would bill the whole stop at the last heart rate seen.
     */
    private val MAX_SAMPLE_GAP: Duration = Duration.ofSeconds(120)

    fun compute(
        samples: List<HeartRateSample>,
        duration: Duration?,
        sport: SportCategory,
        profile: Profile,
    ): LoadScore? {
        val resting = profile.restingHr
        val max = profile.maxHr
        val threshold = profile.thresholdHr
        if (max <= resting) return null

        val reference = 60.0 * weight(reserveFraction(threshold.toDouble(), resting, max), profile.sex)
        if (reference <= 0.0) return null

        val measured = trimpOf(samples, resting, max, profile.sex)
        if (measured != null) {
            return LoadScore(
                hrss = measured / reference * 100.0,
                model = LoadModel.HRSS.name,
                restingHr = resting,
                maxHr = max,
                thresholdHr = threshold,
            )
        }

        val minutes = duration?.seconds?.toDouble()?.div(60.0) ?: return null
        val assumed = assumedReserveWithoutHeartRate(sport)
        return LoadScore(
            hrss = minutes * weight(assumed, profile.sex) / reference * 100.0,
            model = LoadModel.ESTIMATED.name,
            restingHr = resting,
            maxHr = max,
            thresholdHr = threshold,
            assumedReserve = assumed,
        )
    }

    private fun trimpOf(
        samples: List<HeartRateSample>,
        restingHr: Int,
        maxHr: Int,
        sex: Sex,
    ): Double? {
        if (samples.size < 2) return null
        val sorted = samples.sortedBy { it.time }

        val gaps = sorted.zipWithNext { a, b ->
            Duration.between(a.time, b.time).coerceIn(Duration.ZERO, MAX_SAMPLE_GAP).seconds
        }
        // The final sample has no successor, so it inherits the typical cadence of the session.
        val durations = gaps + gaps.sorted()[gaps.size / 2]

        var trimp = 0.0
        sorted.forEachIndexed { index, sample ->
            val minutes = durations[index] / 60.0
            trimp += minutes * weight(reserveFraction(sample.bpm.toDouble(), restingHr, maxHr), sex)
        }
        return trimp.takeIf { it > 0.0 }
    }

    private fun reserveFraction(bpm: Double, restingHr: Int, maxHr: Int): Double =
        ((bpm - restingHr) / (maxHr - restingHr)).coerceIn(0.0, 1.0)

    /** Per-minute TRIMP weight: `HRr * a * e^(b * HRr)`, with sex-specific constants. */
    private fun weight(reserve: Double, sex: Sex): Double = when (sex) {
        Sex.MALE -> reserve * 0.64 * exp(1.92 * reserve)
        Sex.FEMALE -> reserve * 0.86 * exp(1.67 * reserve)
    }
}

/**
 * A single spurious sensor spike would inflate max HR and silently deflate every load score that
 * divides by heart-rate reserve, so the session's peak is a high percentile, not the raw maximum.
 */
fun List<HeartRateSample>.peakHeartRate(percentile: Double = 0.99): Long? {
    if (isEmpty()) return null
    val sorted = map { it.bpm }.sorted()
    val index = ((sorted.size - 1) * percentile).roundToInt().coerceIn(0, sorted.lastIndex)
    return sorted[index]
}
