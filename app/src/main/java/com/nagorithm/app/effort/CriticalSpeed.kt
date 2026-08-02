package com.nagorithm.app.effort

import com.nagorithm.app.data.StoredActivity
import com.nagorithm.app.health.SportCategory
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/**
 * Threshold speed fitted from the distance–time relationship, rather than picked out of a bucket.
 *
 * Over hard efforts, distance is close to linear in duration: `distance = criticalSpeed × time +
 * reserve`. Fitting that line means a 900 m swim and a 1400 m swim both inform the same estimate,
 * so nothing is discarded for being an unusual distance and there are no band boundaries to fall
 * either side of. It is the same idea behind Critical Swim Speed and functional threshold power.
 *
 * The fit is the honest one only if each point is a genuinely hard effort for its duration, which
 * training sessions are not. Fitting the upper envelope rather than the whole scatter is the usual
 * answer, and it is what makes an easy hour count as evidence of nothing rather than of decline.
 */
data class CriticalSpeedFit(
    val sport: SportCategory,
    val isIndoor: Boolean,
    /** Metres per second the user can hold more or less indefinitely. */
    val criticalSpeed: Double,
    /** Distance available above threshold before it runs out. The line's intercept. */
    val reserveMeters: Double,
    val sampleCount: Int,
    val quality: FitQuality,
    val shortestMeters: Double,
    val longestMeters: Double,
) {
    /**
     * Two points and two parameters means the line passes exactly through both and leaves no
     * residual behind — there is nothing left over to say whether the model fits or whether those
     * two sessions simply happened. A third point is the first evidence the relationship is real,
     * so anything thinner may state a fact but must not quote a percentage.
     */
    val isWellConstrained: Boolean get() = quality == FitQuality.FITTED && sampleCount >= 3

    /**
     * Predicting well outside the distances actually recorded is where a straight line stops
     * describing physiology. A little either side is fine; a lot is invention.
     */
    fun covers(distanceMeters: Double): Boolean =
        distanceMeters >= shortestMeters * 0.7 && distanceMeters <= longestMeters * 1.4

    /**
     * Time the model expects over a distance. This is what turns the abstraction back into
     * something a person recognises — a predicted 1000 m rather than a speed in metres per second.
     */
    fun predictSeconds(distanceMeters: Double): Double? {
        if (distanceMeters <= reserveMeters * RESERVE_MARGIN) return null
        return (distanceMeters - reserveMeters) / criticalSpeed
    }

    fun predictedPaceSecondsPerKm(distanceMeters: Double): Double? =
        predictSeconds(distanceMeters)?.let { it / (distanceMeters / 1000.0) }

    private companion object {
        /** Below this the distance is in the sprint domain and the linear model does not hold. */
        const val RESERVE_MARGIN = 1.2
    }
}

enum class FitQuality {
    /** Enough spread of durations to separate threshold speed from the anaerobic reserve. */
    FITTED,

    /** Everything was about the same length, so the reserve had to be assumed away. */
    APPROXIMATE,
}

data class SportKey(val sport: SportCategory, val isIndoor: Boolean)

/** How much a session beat, or missed, what the rest of the history predicted for it. */
data class CurveComparison(
    val activity: StoredActivity,
    val actualSeconds: Double,
    val predictedSeconds: Double,
    val fit: CriticalSpeedFit,
) {
    /** Positive means faster than the model expected. */
    val secondsFasterThanPredicted: Double get() = predictedSeconds - actualSeconds

    val fractionFasterThanPredicted: Double
        get() = secondsFasterThanPredicted / predictedSeconds

    /**
     * Whether the gap is worth quoting as a number. A thin or extrapolated curve can still say
     * "that was your quickest", which is a fact about the sessions themselves — but it cannot put
     * a percentage on it without dressing up ignorance as precision.
     */
    val isConfident: Boolean
        get() = fit.isWellConstrained &&
            activity.distanceMeters?.let { fit.covers(it) } == true

    /** Below this it is conditions, traffic and GPS drift rather than fitness. */
    val isMeaningful: Boolean
        get() = kotlin.math.abs(fractionFasterThanPredicted) >= MEANINGFUL_FRACTION

    companion object {
        const val MEANINGFUL_FRACTION = 0.02
    }
}

/**
 * Trainer and pool sessions are fitted separately from road ones: a trainer invents its distance
 * from a power model, so mixing them measures the resistance setting rather than the athlete.
 */
fun criticalSpeedFits(activities: List<StoredActivity>): Map<SportKey, CriticalSpeedFit> =
    activities
        .filter { it.isFittable }
        .groupBy { SportKey(it.sport, it.isIndoor) }
        .mapNotNull { (key, sessions) -> fitCriticalSpeed(key, sessions)?.let { key to it } }
        .toMap()

/**
 * What the history would have predicted for this session, fitted *without* it. Including the
 * session in its own baseline would let a breakthrough quietly explain itself away.
 */
fun compareToCurve(
    activity: StoredActivity,
    history: List<StoredActivity>,
): CurveComparison? {
    if (!activity.isFittable) return null
    val distance = activity.distanceMeters ?: return null

    val others = history.filter { it.recordId != activity.recordId }
    val key = SportKey(activity.sport, activity.isIndoor)
    val fit = fitCriticalSpeed(key, others.filter { it.isFittable && SportKey(it.sport, it.isIndoor) == key })
        ?: return null

    val predicted = fit.predictSeconds(distance) ?: return null

    return CurveComparison(
        activity = activity,
        actualSeconds = activity.scoredDuration.seconds.toDouble(),
        predictedSeconds = predicted,
        fit = fit,
    )
}

/**
 * A round distance near the middle of what the user actually does, used to turn a fitted speed
 * back into a time they recognise. Rounded because "your 1043 m" is false precision for something
 * the model will happily evaluate anywhere.
 */
fun referenceDistance(activities: List<StoredActivity>, sport: SportCategory?): Double? {
    val distances = activities
        .filter { (sport == null || it.sport == sport) && it.isFittable }
        .mapNotNull { it.distanceMeters }
        .sorted()

    if (distances.isEmpty()) return null

    val median = distances[distances.size / 2]
    val magnitude = 10.0.pow(floor(log10(median)) - 1)
    return round(median / magnitude) * magnitude
}

/** Warm-ups and strays carry no information about threshold and destabilise the fit. */
private const val MINIMUM_SECONDS = 600L

private val MINIMUM_DISTANCE_METERS = mapOf(
    SportCategory.SWIMMING to 200.0,
    SportCategory.RUNNING to 800.0,
    SportCategory.WALKING to 1_000.0,
    SportCategory.ROWING to 500.0,
    SportCategory.CYCLING to 3_000.0,
)

private const val DEFAULT_MINIMUM_METERS = 1_000.0

private val StoredActivity.isFittable: Boolean
    get() {
        val meters = distanceMeters ?: return false
        return meters >= (MINIMUM_DISTANCE_METERS[sport] ?: DEFAULT_MINIMUM_METERS) &&
            scoredDuration.seconds >= MINIMUM_SECONDS
    }

private data class Effort(val seconds: Double, val meters: Double)

/**
 * Filters its own input rather than trusting the caller to have done it. A single warm-up left in
 * by accident bends the line badly, and that is far too quiet a way to get a wrong answer.
 */
fun fitCriticalSpeed(key: SportKey, sessions: List<StoredActivity>): CriticalSpeedFit? {
    val frontier = frontier(
        sessions
            .filter { it.isFittable }
            .map { Effort(it.scoredDuration.seconds.toDouble(), it.distanceMeters!!) },
    )
    if (frontier.size < 2) return null

    val spread = frontier.maxOf { it.seconds } / frontier.minOf { it.seconds }
    val regression = if (spread >= MINIMUM_SPREAD) regress(frontier) else null

    // A negative reserve means the line is fitting noise rather than physiology. Falling back to
    // a straight average speed is worse in theory and far better than reporting a nonsense curve.
    val usable = regression != null && regression.speed > 0.0 && regression.reserve >= 0.0

    return CriticalSpeedFit(
        sport = key.sport,
        isIndoor = key.isIndoor,
        criticalSpeed = if (usable) {
            regression!!.speed
        } else {
            frontier.sumOf { it.meters } / frontier.sumOf { it.seconds }
        },
        reserveMeters = if (usable) regression!!.reserve else 0.0,
        sampleCount = frontier.size,
        quality = if (usable) FitQuality.FITTED else FitQuality.APPROXIMATE,
        shortestMeters = frontier.minOf { it.meters },
        longestMeters = frontier.maxOf { it.meters },
    )
}

/** Enough range in duration that threshold speed and the reserve can be told apart. */
private const val MINIMUM_SPREAD = 1.5

/**
 * The best efforts only. A point survives if nothing else covered at least as far in no more time,
 * which leaves the outer edge of what has actually been done.
 */
private fun frontier(efforts: List<Effort>): List<Effort> {
    val kept = mutableListOf<Effort>()
    var furthest = 0.0
    efforts.sortedBy { it.seconds }.forEach { effort ->
        if (effort.meters > furthest) {
            kept += effort
            furthest = effort.meters
        }
    }
    return kept
}

private data class Regression(val speed: Double, val reserve: Double)

private fun regress(efforts: List<Effort>): Regression? {
    val n = efforts.size
    val sumT = efforts.sumOf { it.seconds }
    val sumD = efforts.sumOf { it.meters }
    val sumTT = efforts.sumOf { it.seconds * it.seconds }
    val sumTD = efforts.sumOf { it.seconds * it.meters }

    val denominator = n * sumTT - sumT * sumT
    if (denominator == 0.0) return null

    val speed = (n * sumTD - sumT * sumD) / denominator
    return Regression(speed = speed, reserve = (sumD - speed * sumT) / n)
}
