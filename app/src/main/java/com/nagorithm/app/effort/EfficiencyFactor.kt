package com.nagorithm.app.effort

import com.nagorithm.app.data.StoredActivity
import com.nagorithm.app.health.SportCategory
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Speed per beat: how much pace a heart rate is buying.
 *
 * Going faster at the same heart rate is the cleanest evidence of aerobic improvement there is,
 * and because both terms are rates it does not care how long the session was. That makes it the
 * counterpart to the fitted speed curve — the curve wants clean distance and tolerates missing
 * heart rate, this wants heart rate and tolerates a messy route.
 */
fun StoredActivity.efficiencyFactor(): Double? {
    val meters = distanceMeters ?: return null
    val bpm = averageHeartRateBpm ?: return null
    val seconds = scoredDuration.seconds

    if (!isEfficiencyComparable || bpm <= 0L || seconds <= 0L) return null
    return (meters / seconds) / bpm
}

/**
 * Swimming is excluded outright. Heart rate underwater is either missing or wrong — the same
 * reason load scoring ignores stray swim samples — and the pool is already covered by the speed
 * curve, where its fixed, windless distance is an advantage rather than a problem.
 */
private val StoredActivity.isEfficiencyComparable: Boolean
    get() = sport != SportCategory.SWIMMING &&
        hrSampleCount >= MINIMUM_SAMPLES &&
        scoredDuration.seconds >= MINIMUM_SECONDS

/** Short sessions are mostly warm-up, where heart rate lags the effort and flatters the ratio. */
private const val MINIMUM_SECONDS = 1_200L
private const val MINIMUM_SAMPLES = 10

data class EfficiencyTrend(
    val key: SportKey,
    val recentMedian: Double,
    val priorMedian: Double,
    val recentCount: Int,
    val priorCount: Int,
) {
    /** Positive means more speed per beat than before. */
    val improvementFraction: Double get() = (recentMedian - priorMedian) / priorMedian

    /**
     * Higher bar than the speed curve. Efficiency moves with heat, hills, sleep and how hard the
     * session was meant to be, so small changes are noise wearing a lab coat.
     */
    val isMeaningful: Boolean get() = abs(improvementFraction) >= MEANINGFUL_FRACTION

    companion object {
        const val MEANINGFUL_FRACTION = 0.03
    }
}

const val EFFICIENCY_RECENT_DAYS = 28L
const val EFFICIENCY_PRIOR_DAYS = 84L

fun efficiencyTrends(
    activities: List<StoredActivity>,
    now: Instant = Instant.now(),
): List<EfficiencyTrend> {
    val recentFrom = now.minus(EFFICIENCY_RECENT_DAYS, ChronoUnit.DAYS).toEpochMilli()
    val priorFrom = now.minus(EFFICIENCY_PRIOR_DAYS, ChronoUnit.DAYS).toEpochMilli()

    return activities
        .mapNotNull { activity -> activity.efficiencyFactor()?.let { activity to it } }
        .groupBy { SportKey(it.first.sport, it.first.isIndoor) }
        .mapNotNull { (key, scored) ->
            val recent = scored
                .filter { it.first.startTimeEpochMillis >= recentFrom }
                .map { it.second }
            val prior = scored
                .filter {
                    it.first.startTimeEpochMillis in priorFrom until recentFrom
                }
                .map { it.second }

            if (recent.isEmpty() || prior.isEmpty()) return@mapNotNull null

            EfficiencyTrend(
                key = key,
                recentMedian = recent.median(),
                priorMedian = prior.median(),
                recentCount = recent.size,
                priorCount = prior.size,
            )
        }
}

private fun List<Double>.median(): Double = sorted()[size / 2]
