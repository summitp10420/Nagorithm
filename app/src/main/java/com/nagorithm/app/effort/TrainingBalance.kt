package com.nagorithm.app.effort

import com.nagorithm.app.data.StoredActivity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Which arithmetic is allowed to declare that someone has ramped up too hard. */
enum class RampSignal(val label: String) {
    ACWR("ACWR"),
    FORM("Form"),
}

/** The shared vocabulary that makes two different calculations comparable. */
enum class LoadReading(val label: String) {
    FADING("fading"),
    STEADY("holding steady"),
    BUILDING("building"),
    TOO_MUCH("too much"),
}

/**
 * Fitness, fatigue and form, the way the Performance Management Chart has done it for twenty years.
 *
 * Two exponential averages of the same daily load: one over 42 days that barely moves, one over 7
 * that reacts to yesterday. Their difference is form — negative while fatigue outruns fitness,
 * which is what productive training feels like, and positive after a taper.
 *
 * Reported against the user's own fitness rather than as a raw number. The published zones were
 * calibrated on cyclists carrying five to ten times this load, where −30 is a meaningful warning;
 * at two hours a week it is unreachable, and a band nobody can enter is not a band.
 */
data class TrainingBalance(
    val fitness: Double,
    val fatigue: Double,
    val daysOfHistory: Long,
    /** How much of the recent load was guessed from duration rather than measured from heart rate. */
    val estimatedShare: Double,
) {
    val form: Double get() = fitness - fatigue

    /**
     * A 42-day average needs roughly three of its own time constants before it stops being mostly
     * the zero it started from. Until then fitness reads low, form reads punishing, and the app
     * would warn a beginner off training they have not actually done.
     */
    val isSettled: Boolean get() = daysOfHistory >= SETTLING_DAYS

    /**
     * Whether the number means anything yet.
     *
     * Two ways it can fail. The average may not have burnt in, in which case fitness is an artefact
     * of when tracking started rather than of any training. Or fitness may be genuinely tiny, and
     * dividing by it turns an unremarkable week into a three-figure catastrophe — a first draft of
     * this reported −296% for a man who had gone for a bike ride.
     */
    val isUsable: Boolean get() = isSettled && fitness >= MINIMUM_FITNESS

    /** Null rather than a number, so nothing downstream can accidentally render a placeholder. */
    val formPercent: Double? get() = if (isUsable) form / fitness * 100.0 else null

    val reading: LoadReading?
        get() = formPercent?.let {
            when {
                it > FADING_PERCENT -> LoadReading.FADING
                it > STEADY_PERCENT -> LoadReading.STEADY
                it > OVERREACHING_PERCENT -> LoadReading.BUILDING
                else -> LoadReading.TOO_MUCH
            }
        }

    /** False while unusable, which keeps an uncalibrated average from vetoing anyone's training. */
    val isOverreaching: Boolean get() = reading == LoadReading.TOO_MUCH

    companion object {
        const val SETTLING_DAYS = 90L

        /**
         * Below roughly 35 load a week there is no base to be a ratio of. Someone at that level
         * is not overreaching, they are starting, and the honest answer is to say nothing.
         */
        const val MINIMUM_FITNESS = 5.0

        /**
         * Proportional equivalents of the classic +10 / −10 / −30 boundaries for someone whose
         * fitness sits near 100. A starting point to be tuned against real weeks, not a constant
         * anyone has validated at these volumes.
         */
        const val FADING_PERCENT = 15.0
        const val STEADY_PERCENT = -10.0
        const val OVERREACHING_PERCENT = -30.0
    }
}

private const val FITNESS_TIME_CONSTANT = 42.0
private const val FATIGUE_TIME_CONSTANT = 7.0
private const val ESTIMATED_SHARE_DAYS = 28L

/** Where ACWR lands on the same four-way scale, so the two can be held against each other. */
fun acwrReading(acwr: Double): LoadReading = when {
    acwr < 0.8 -> LoadReading.FADING
    acwr <= 1.3 -> LoadReading.STEADY
    acwr <= 1.5 -> LoadReading.BUILDING
    else -> LoadReading.TOO_MUCH
}

/**
 * Walks every day from the first session to today, including the empty ones — a rest day is not a
 * missing value, it is a zero, and skipping it would leave fatigue permanently elevated because
 * nothing ever decayed it.
 */
fun trainingBalance(
    activities: List<StoredActivity>,
    now: Instant = Instant.now(),
): TrainingBalance? {
    val scored = activities.filter { it.load != null }
    if (scored.isEmpty()) return null

    val zone = ZoneId.systemDefault()
    val today = now.atZone(zone).toLocalDate()

    val daily: Map<LocalDate, Double> = scored
        .groupBy { Instant.ofEpochMilli(it.startTimeEpochMillis).atZone(zone).toLocalDate() }
        .mapValues { (_, sessions) -> sessions.sumOf { it.load!!.hrss } }

    val first = daily.keys.min()
    if (first.isAfter(today)) return null

    var fitness = 0.0
    var fatigue = 0.0
    var day = first
    while (!day.isAfter(today)) {
        val load = daily[day] ?: 0.0
        fitness += (load - fitness) / FITNESS_TIME_CONSTANT
        fatigue += (load - fatigue) / FATIGUE_TIME_CONSTANT
        day = day.plusDays(1)
    }

    return TrainingBalance(
        fitness = fitness,
        fatigue = fatigue,
        daysOfHistory = ChronoUnit.DAYS.between(first, today) + 1,
        estimatedShare = estimatedShare(scored, now),
    )
}

/**
 * Form is a difference between two large numbers, so error in the inputs is amplified rather than
 * averaged away. Swims contribute a duration multiplied by an assumed intensity, and when that is
 * most of the week the number deserves to be read with suspicion.
 */
private fun estimatedShare(scored: List<StoredActivity>, now: Instant): Double {
    val from = now.minus(ESTIMATED_SHARE_DAYS, ChronoUnit.DAYS).toEpochMilli()
    val recent = scored.filter { it.startTimeEpochMillis >= from }

    val total = recent.sumOf { it.load!!.hrss }
    if (total <= 0.0) return 0.0

    return recent.filterNot { it.load!!.isFullyMeasured }.sumOf { it.load!!.hrss } / total
}
