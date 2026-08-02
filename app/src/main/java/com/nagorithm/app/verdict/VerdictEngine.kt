package com.nagorithm.app.verdict

import com.nagorithm.app.data.Goal
import com.nagorithm.app.data.GoalDimension
import com.nagorithm.app.data.StoredActivity
import com.nagorithm.app.effort.CriticalSpeedFit
import com.nagorithm.app.effort.CurveComparison
import com.nagorithm.app.effort.EfficiencyTrend
import com.nagorithm.app.effort.EffortSnapshot
import com.nagorithm.app.effort.compareToCurve
import com.nagorithm.app.effort.criticalSpeedFits
import com.nagorithm.app.effort.efficiencyTrends
import com.nagorithm.app.effort.referenceDistance
import com.nagorithm.app.health.SportCategory
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** A number in the goal's dimension, taken the same way whether it is a baseline or a current value. */
data class GoalMeasurement(
    val value: Double,
    val unit: String,
    /** For [GoalDimension.PACE]: the distance the predicted time refers to. */
    val bucketMeters: Double? = null,
) {
    companion object {
        const val UNIT_METERS = "m"
        const val UNIT_SECONDS = "s"
        const val UNIT_LOAD_PER_WEEK = "HRSS/week"
    }
}

/**
 * Turns stored history into a decision. Everything here is local arithmetic — no network, no model.
 * The wording layer downstream may only choose how to phrase what this returns.
 */
object VerdictEngine {

    /** Above this, acute load has outrun the base supporting it. */
    private const val RAMPING_ACWR = 1.5

    /**
     * Absolute floor before the ratio is allowed to say anything, because a ratio of small numbers
     * is still small. Doubling from sixty to a hundred and twenty is arithmetically alarming and
     * physically nothing — about two hours of moderate work — and the injury thresholds ACWR comes
     * from were measured on athletes carrying real load. Warning someone off at that volume is
     * both wrong and the exact opposite of what this app is for.
     */
    private const val RAMPING_MINIMUM_ACUTE_LOAD = 150.0

    /** Nothing at all is news forever, so a first check-in looks at the last week instead. */
    private const val FIRST_CHECK_IN_NEWS_DAYS = 7L

    fun evaluate(
        activities: List<StoredActivity>,
        snapshot: EffortSnapshot,
        goal: Goal?,
        lastCheckInEpochMillis: Long?,
        feedAlive: Boolean?,
        now: Instant = Instant.now(),
    ): Verdict {
        val news = newsSince(activities, lastCheckInEpochMillis, now)
        val headline = headlineComparison(activities, news, goal?.sport)
        val efficiency = pickEfficiency(efficiencyTrends(activities, now), goal?.sport)
        val progress = goalProgress(goal, activities, snapshot)
        val band = detrainingBand(snapshot.daysSinceLastActivity, snapshot.chronic.loadPerWeek)

        // Silence from Health Connect is indistinguishable from a lazy week by looking at
        // activities alone, so the passive-data check is the only thing standing between a broken
        // sync and an accusation.
        val feedSilent = feedAlive == false && news.isEmpty()

        val ramping = !snapshot.isColdStart &&
            (snapshot.acwr ?: 0.0) > RAMPING_ACWR &&
            snapshot.acute.totalLoad >= RAMPING_MINIMUM_ACUTE_LOAD &&
            news.isNotEmpty()

        // Beating the curve is the stronger claim, because it is one session against everything
        // else. Efficiency needs two windows of history and moves slowly, so it can carry a
        // verdict on its own only when there is no fresh session to talk about.
        val improving = (headline != null && headline.isMeaningful &&
            headline.secondsFasterThanPredicted > 0) ||
            (news.isNotEmpty() && efficiency != null && efficiency.isMeaningful &&
                efficiency.improvementFraction > 0)

        val state = when {
            feedSilent -> VerdictState.FEED_SILENT
            activities.size < 2 -> VerdictState.COLD_START
            ramping -> VerdictState.RAMPING_HARD
            improving -> VerdictState.PROGRESSING
            news.isNotEmpty() -> VerdictState.STEADY
            band == DetrainingBand.FRESH -> VerdictState.RESTING
            else -> VerdictState.IDLE
        }

        return Verdict(
            state = state,
            computedAt = now,
            goal = goal,
            goalProgress = progress,
            news = news,
            headline = headline,
            efficiency = efficiency,
            detraining = band,
            daysSinceLastActivity = snapshot.daysSinceLastActivity,
            acwr = snapshot.acwr.takeIf { !snapshot.isColdStart },
            snapshot = snapshot,
            reasons = explain(state, snapshot, news, headline, efficiency, progress, band, feedAlive, goal),
        )
    }

    /**
     * News is measured by when this app first saw a session, not when it happened. Garmin can
     * deliver a ride days late, and a session that arrives after it was judged is still new
     * information to the user.
     */
    private fun newsSince(
        activities: List<StoredActivity>,
        lastCheckInEpochMillis: Long?,
        now: Instant,
    ): List<StoredActivity> {
        val cutoff = lastCheckInEpochMillis
            ?: now.minus(FIRST_CHECK_IN_NEWS_DAYS, ChronoUnit.DAYS).toEpochMilli()
        return activities
            .filter { it.firstSeenEpochMillis > cutoff || it.startTimeEpochMillis > cutoff }
            .sortedByDescending { it.startTimeEpochMillis }
    }

    /**
     * The best story among the new sessions, judged against the curve the rest of the history
     * predicts. Preferring the goal's sport keeps a casual walk from upstaging the ride the goal
     * actually depends on.
     */
    private fun headlineComparison(
        activities: List<StoredActivity>,
        news: List<StoredActivity>,
        goalSport: SportCategory?,
    ): CurveComparison? = news
        .mapNotNull { compareToCurve(it, activities) }
        .sortedWith(
            compareByDescending<CurveComparison> { it.activity.sport == goalSport }
                .thenByDescending { abs(it.fractionFasterThanPredicted) },
        )
        .firstOrNull()

    private fun pickEfficiency(
        trends: List<EfficiencyTrend>,
        goalSport: SportCategory?,
    ): EfficiencyTrend? = trends
        .sortedWith(
            compareByDescending<EfficiencyTrend> { it.key.sport == goalSport }
                .thenByDescending { it.recentCount + it.priorCount },
        )
        .firstOrNull()

    /**
     * Where the goal's measurement stands now against where it stood when the goal was set. Null
     * until both ends exist — claiming progress against a baseline that was never captured would
     * be inventing the thing the user most needs to trust.
     */
    private fun goalProgress(
        goal: Goal?,
        activities: List<StoredActivity>,
        snapshot: EffortSnapshot,
    ): GoalProgress? {
        val start = goal?.start ?: return null
        val current = measure(
            dimension = goal.dimension,
            sport = goal.sport,
            activities = activities,
            snapshot = snapshot,
            bucketMeters = start.bucketMeters,
        ) ?: return null

        return GoalProgress(
            dimension = goal.dimension,
            startValue = start.value,
            currentValue = current.value,
            unit = current.unit,
            bucketMeters = current.bucketMeters,
        )
    }

    /**
     * The one measurement the goal made interesting. Used for both the baseline at goal creation
     * and the current value afterwards, so the two ends can never be taken different ways.
     */
    fun measure(
        dimension: GoalDimension,
        sport: SportCategory?,
        activities: List<StoredActivity>,
        snapshot: EffortSnapshot,
        bucketMeters: Double? = null,
    ): GoalMeasurement? {
        val relevant = activities.filter { sport == null || it.sport == sport }

        return when (dimension) {
            GoalDimension.DISTANCE -> relevant
                .mapNotNull { it.distanceMeters }
                .maxOrNull()
                ?.let { GoalMeasurement(it, GoalMeasurement.UNIT_METERS) }

            // Predicted time over a fixed distance rather than a measured one, so every session
            // informs the answer and none has to be discarded for being an unusual length.
            GoalDimension.PACE -> {
                val reference = bucketMeters ?: referenceDistance(relevant, sport) ?: return null
                bestFit(relevant, sport)
                    ?.predictSeconds(reference)
                    ?.let { GoalMeasurement(it, GoalMeasurement.UNIT_SECONDS, reference) }
            }

            GoalDimension.VOLUME -> snapshot.chronic.loadPerWeek
                .takeIf { it > 0.0 }
                ?.let { GoalMeasurement(it, GoalMeasurement.UNIT_LOAD_PER_WEEK) }
        }
    }

    /**
     * Indoor and outdoor are fitted separately, so a goal that spans both has to pick one. The
     * better-evidenced curve wins rather than an average of two that measure different things.
     */
    private fun bestFit(activities: List<StoredActivity>, sport: SportCategory?): CriticalSpeedFit? =
        criticalSpeedFits(activities)
            .filterKeys { sport == null || it.sport == sport }
            .values
            .maxByOrNull { it.sampleCount }

    /**
     * How long absence is tolerated before it costs something, scaled by how much there is to
     * lose. Detraining is not a fixed clock: someone carrying 400 a week sheds more in ten days
     * than someone carrying 80, because they built more that needs maintaining.
     */
    private fun slackDays(loadPerWeek: Double): Long = when {
        loadPerWeek >= 400.0 -> 3L
        loadPerWeek >= 250.0 -> 4L
        loadPerWeek >= 120.0 -> 5L
        else -> 7L
    }

    fun detrainingBand(daysSinceLastActivity: Long?, loadPerWeek: Double): DetrainingBand = when {
        daysSinceLastActivity == null -> DetrainingBand.SOFT
        daysSinceLastActivity <= slackDays(loadPerWeek) -> DetrainingBand.FRESH
        daysSinceLastActivity < 10L -> DetrainingBand.DRIFTING
        daysSinceLastActivity < 21L -> DetrainingBand.LOSING
        else -> DetrainingBand.SOFT
    }

    /**
     * The audit trail. Written at decision time rather than reconstructed later, so "Why this
     * nag?" shows the inputs that were actually used and not a plausible retelling.
     */
    private fun explain(
        state: VerdictState,
        snapshot: EffortSnapshot,
        news: List<StoredActivity>,
        headline: CurveComparison?,
        efficiency: EfficiencyTrend?,
        progress: GoalProgress?,
        band: DetrainingBand,
        feedAlive: Boolean?,
        goal: Goal?,
    ): List<String> = buildList {
        add("Verdict: ${state.name}")

        if (goal == null) {
            add("No goal set, so nothing selects which measurement matters.")
        } else {
            add("Goal: \"${goal.text}\" — judged on ${goal.dimension.label.lowercase()}.")
        }

        add(
            when {
                news.isEmpty() -> "No sessions seen since the last check-in."
                else -> "${news.size} new session(s) since the last check-in."
            },
        )

        snapshot.daysSinceLastActivity?.let {
            add(
                "Last session $it day(s) ago; at ${"%.0f".format(snapshot.chronic.loadPerWeek)} " +
                    "HRSS/week that counts as ${band.name.lowercase()}.",
            )
        } ?: add("Nothing stored at all.")

        if (snapshot.isColdStart) {
            add(
                "Only ${snapshot.historySpanDays ?: 0} day(s) of history across " +
                    "${snapshot.chronic.activityCount} session(s) — ratios suppressed until " +
                    "there is a full block to divide by.",
            )
        } else {
            snapshot.acwr?.let { add("ACWR ${"%.2f".format(it)} (7-day load over 28-day average).") }
        }

        headline?.let {
            add(
                "Newest ${it.activity.sport.label.lowercase()} came in " +
                    "${"%+.1f".format(it.fractionFasterThanPredicted * 100)}% against the time " +
                    "the speed curve predicted, fitted from ${it.fit.sampleCount} best effort(s) " +
                    "excluding this one (${it.fit.quality.name.lowercase()}).",
            )
            if (!it.isConfident) {
                add(
                    "That percentage is not quoted to you: ${it.fit.sampleCount} points between " +
                        "${"%.0f".format(it.fit.shortestMeters)} m and " +
                        "${"%.0f".format(it.fit.longestMeters)} m is too thin to measure a gap " +
                        "with, so the nag states the result without sizing it.",
                )
            }
        }

        efficiency?.let {
            add(
                "Speed per heartbeat in ${it.key.sport.label.lowercase()} is " +
                    "${"%+.1f".format(it.improvementFraction * 100)}% over the last 28 days " +
                    "against the ${it.priorCount} session(s) before that.",
            )
        }

        progress?.let {
            add(
                "Goal measurement has moved ${"%+.1f".format(it.improvementFraction * 100)}% " +
                    "since the goal was set.",
            )
        }

        if (feedAlive == false) {
            add("Health Connect returned no passive data either, so the feed itself is suspect.")
        }
    }
}
