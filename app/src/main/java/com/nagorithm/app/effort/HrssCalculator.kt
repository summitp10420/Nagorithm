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

    /** Above this share of the session covered by samples, the reading is treated as complete. */
    private const val FULL_COVERAGE = 0.9

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

        val assumedReserve = assumedReserveWithoutHeartRate(sport)
        val assumedPerMinute = weight(assumedReserve, profile.sex)

        // A watch that cannot read heart rate underwater still emits the odd stray value. Those
        // are noise, not a measurement, so swimming never takes the sampled path.
        val measured = if (sport == SportCategory.SWIMMING) {
            null
        } else {
            measure(samples, resting, max, profile.sex)
        }

        fun score(hrss: Double, model: LoadModel, assumed: Double?) = LoadScore(
            hrss = hrss,
            model = model.name,
            restingHr = resting,
            maxHr = max,
            thresholdHr = threshold,
            assumedReserve = assumed,
        )

        if (measured == null) {
            val minutes = duration?.seconds?.toDouble()?.div(60.0) ?: return null
            return score(
                hrss = minutes * assumedPerMinute / reference * 100.0,
                model = LoadModel.ESTIMATED,
                assumed = assumedReserve,
            )
        }

        // Samples only bill the time between them, so a session with a handful of stray readings
        // would otherwise be charged at nothing for the stretches they do not span. Fill the gap
        // at the sport's assumed intensity instead of silently under-scoring the session.
        val sessionSeconds = duration?.seconds ?: measured.coveredSeconds
        val uncoveredSeconds = (sessionSeconds - measured.coveredSeconds).coerceAtLeast(0L)
        val complete = uncoveredSeconds <= sessionSeconds * (1.0 - FULL_COVERAGE)

        return score(
            hrss = (measured.trimp + uncoveredSeconds / 60.0 * assumedPerMinute) / reference * 100.0,
            model = if (complete) LoadModel.HRSS else LoadModel.PARTIAL,
            assumed = assumedReserve.takeUnless { complete },
        )
    }

    private class Measurement(val trimp: Double, val coveredSeconds: Long)

    private fun measure(
        samples: List<HeartRateSample>,
        restingHr: Int,
        maxHr: Int,
        sex: Sex,
    ): Measurement? {
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
        return Measurement(trimp, durations.sum()).takeIf { trimp > 0.0 }
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
