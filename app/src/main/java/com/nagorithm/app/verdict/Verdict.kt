package com.nagorithm.app.verdict

import com.nagorithm.app.data.Goal
import com.nagorithm.app.data.GoalDimension
import com.nagorithm.app.data.StoredActivity
import com.nagorithm.app.effort.CurveComparison
import com.nagorithm.app.effort.EffortSnapshot
import com.nagorithm.app.effort.EfficiencyTrend
import java.time.Instant

/**
 * What the app has decided, before anything is written. The wording layer — template or LLM — may
 * only choose how to say one of these. It never gets to decide which one is true.
 */
enum class VerdictState {
    /**
     * Health Connect returned nothing at all, including the passive data that should always be
     * there. Silence from the pipe looks identical to a lazy week, and guessing wrong here means
     * telling someone to get off the couch two days after a sixty-mile ride.
     */
    FEED_SILENT,

    /** Too little local history for any ratio or trend to mean anything yet. */
    COLD_START,

    /** Inside the recovery window for the load being carried. Rest is productive here. */
    RESTING,

    /** Past the point where the absence is costing fitness. */
    IDLE,

    /** Work is going in, nothing has measurably changed. The common case. */
    STEADY,

    /** Something got measurably better in the dimension the goal cares about. */
    PROGRESSING,

    /** Acute load well above what the base supports. Continuing costs more than it buys. */
    RAMPING_HARD,
}

/**
 * How far absence has gone. Bands rather than a percentage: the research supports "two weeks off
 * and you are losing it", not a per-day figure precise enough to quote at somebody.
 */
enum class DetrainingBand {
    /** Still inside useful recovery. */
    FRESH,

    /** Past recovery, not yet losing anything. The cheapest moment to intervene. */
    DRIFTING,

    /** Aerobic base is measurably receding. */
    LOSING,

    /** Long enough that coming back is its own project. */
    SOFT,
}

/**
 * Movement in the one measurement the goal made interesting, against where the user was when they
 * set it. [startValue] is never recomputed, so improvement cannot erase itself.
 */
data class GoalProgress(
    val dimension: GoalDimension,
    val startValue: Double,
    val currentValue: Double,
    val unit: String,
    val bucketMeters: Double? = null,
) {
    /**
     * Positive always means "better". Pace is inverted because a smaller number is a faster one,
     * and every consumer of this would otherwise have to remember that.
     */
    val improvementFraction: Double
        get() = when (dimension) {
            GoalDimension.PACE -> (startValue - currentValue) / startValue
            else -> (currentValue - startValue) / startValue
        }

    val hasMoved: Boolean get() = kotlin.math.abs(improvementFraction) >= 0.02
}

data class Verdict(
    val state: VerdictState,
    val computedAt: Instant,
    val goal: Goal?,
    val goalProgress: GoalProgress?,
    /** Sessions this app had not seen at the last check-in, so late syncs still count as news. */
    val news: List<StoredActivity>,
    /** The newest session measured against what the rest of the history predicted for it. */
    val headline: CurveComparison?,
    /** Speed per beat, recent against earlier. Null until both windows have sessions in them. */
    val efficiency: EfficiencyTrend?,
    val detraining: DetrainingBand,
    val daysSinceLastActivity: Long?,
    val acwr: Double?,
    val snapshot: EffortSnapshot,
    /** Plain-language trace of every input that moved the decision. Drives "Why this nag?". */
    val reasons: List<String>,
) {
    val hasNews: Boolean get() = news.isNotEmpty()
}
