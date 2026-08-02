package com.nagorithm.app.effort

import com.nagorithm.app.data.StoredActivity
import com.nagorithm.app.health.SportCategory
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Holds the sessions it covers rather than a fixed set of precomputed totals. New questions become
 * new properties instead of new constructor parameters, and the transparency view needs the list
 * of counted sessions anyway.
 */
data class LoadWindow(
    val days: Long,
    val from: Instant,
    val to: Instant,
    val activities: List<StoredActivity>,
) {
    val totalLoad: Double get() = activities.sumOf { it.load?.hrss ?: 0.0 }

    val loadPerWeek: Double get() = totalLoad / days * 7.0

    val activityCount: Int get() = activities.size

    /** Counts partly estimated sessions too — both are load the heart rate did not fully justify. */
    val estimatedCount: Int get() = activities.count { it.load?.isFullyMeasured == false }

    val movingTime: Duration
        get() = activities.fold(Duration.ZERO) { acc, it -> acc + it.scoredDuration }

    val distanceMeters: Double get() = activities.sumOf { it.distanceMeters ?: 0.0 }

    val activeDays: Int
        get() = activities.map { it.startTimeEpochMillis / MILLIS_PER_DAY }.distinct().size

    val loadBySport: Map<SportCategory, Double>
        get() = activities
            .groupBy { it.sport }
            .mapValues { (_, list) -> list.sumOf { it.load?.hrss ?: 0.0 } }
            .filterValues { it > 0.0 }

    val longestBySport: Map<SportCategory, Double>
        get() = activities
            .groupBy { it.sport }
            .mapValues { (_, list) -> list.mapNotNull { it.distanceMeters }.maxOrNull() ?: 0.0 }
            .filterValues { it > 0.0 }

    /**
     * Share of load in one sport. The verdict layer uses this for goal specificity — aerobic work
     * always counts, but a cycling goal still wants to know whether the bike is being ridden.
     */
    fun loadShare(sport: SportCategory): Double =
        totalLoad.takeIf { it > 0.0 }?.let { (loadBySport[sport] ?: 0.0) / it } ?: 0.0

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}

/**
 * Everything the verdict needs, computed locally. The LLM is handed these numbers; it never
 * decides whether the user did enough.
 */
data class EffortSnapshot(
    val computedAt: Instant,
    val acute: LoadWindow,
    val chronic: LoadWindow,
    val daysSinceLastActivity: Long?,
    val longestGapDays: Long?,
    val longestEverBySport: Map<SportCategory, Double>,
    val hasAnyActivity: Boolean,
    /** Oldest stored session to now. Every ratio below is noise until this is wide enough. */
    val historySpanDays: Long?,
) {
    /**
     * Acute-to-chronic workload ratio. Self-normalizing, so the same thresholds work for someone
     * carrying 200 a week and someone carrying 600 — which is what a model with no target requires.
     */
    val acwr: Double? get() = chronic.loadPerWeek.takeIf { it > 0.0 }?.let { acute.totalLoad / it }

    /**
     * A ratio needs a denominator built from a full chronic block, and nothing else will do.
     *
     * Short of that the 28-day window is partly empty, so the average it produces is not a light
     * month — it is a month that did not exist. The denominator comes out too small and the ratio
     * too large, which is how three weeks of tracking reported a ratio of two and a half and told
     * a man who had gone for a bike ride to calm down.
     *
     * Unlike the exponential averages this needs exactly its own length and no more. A flat window
     * has no burn-in to wait out; it is either full or it is not.
     */
    val isColdStart: Boolean
        get() = (historySpanDays ?: 0L) < MINIMUM_HISTORY_DAYS ||
            chronic.activityCount < MINIMUM_CHRONIC_SESSIONS

    companion object {
        const val ACUTE_DAYS = 7L
        const val CHRONIC_DAYS = 28L

        /** A full chronic block. Anything less is dividing by a month that was never recorded. */
        const val MINIMUM_HISTORY_DAYS = CHRONIC_DAYS

        /**
         * Twenty-eight days containing three sessions is arithmetically a month and practically a
         * rumour: one of them lands in the acute window and the ratio becomes a coin toss.
         */
        const val MINIMUM_CHRONIC_SESSIONS = 8

        fun from(
            activities: List<StoredActivity>,
            now: Instant = Instant.now(),
        ): EffortSnapshot = EffortSnapshot(
            computedAt = now,
            acute = window(activities, now, ACUTE_DAYS),
            chronic = window(activities, now, CHRONIC_DAYS),
            daysSinceLastActivity = activities.maxOfOrNull { it.startTimeEpochMillis }
                ?.let { ChronoUnit.DAYS.between(Instant.ofEpochMilli(it), now) },
            longestGapDays = longestGapDays(activities, now),
            longestEverBySport = activities
                .groupBy { it.sport }
                .mapValues { (_, list) -> list.mapNotNull { it.distanceMeters }.maxOrNull() ?: 0.0 }
                .filterValues { it > 0.0 },
            hasAnyActivity = activities.isNotEmpty(),
            historySpanDays = activities.minOfOrNull { it.startTimeEpochMillis }
                ?.let { ChronoUnit.DAYS.between(Instant.ofEpochMilli(it), now) },
        )

        fun window(activities: List<StoredActivity>, now: Instant, days: Long): LoadWindow {
            val from = now.minus(days, ChronoUnit.DAYS)
            return LoadWindow(
                days = days,
                from = from,
                to = now,
                activities = activities.filter {
                    it.startTimeEpochMillis >= from.toEpochMilli() &&
                        it.startTimeEpochMillis <= now.toEpochMilli()
                },
            )
        }

        /** Longest stretch with nothing recorded inside the chronic window, including up to now. */
        private fun longestGapDays(activities: List<StoredActivity>, now: Instant): Long? {
            val from = now.minus(CHRONIC_DAYS, ChronoUnit.DAYS)
            val starts = activities
                .map { it.startTimeEpochMillis }
                .filter { it >= from.toEpochMilli() }
                .sorted()
            if (starts.isEmpty()) return null

            val boundaries = listOf(from.toEpochMilli()) + starts + now.toEpochMilli()
            return boundaries.zipWithNext { a, b -> (b - a) / MILLIS_PER_DAY }.max()
        }

        private const val MILLIS_PER_DAY = 86_400_000L
    }
}
