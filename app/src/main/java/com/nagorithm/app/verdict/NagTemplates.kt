package com.nagorithm.app.verdict

import com.nagorithm.app.data.GoalDimension
import com.nagorithm.app.effort.CurveComparison
import com.nagorithm.app.format.formatClock
import com.nagorithm.app.format.formatDistanceShort
import com.nagorithm.app.format.formatGap
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

enum class NagSource { TEMPLATE, MODEL }

data class Nag(val headline: String, val body: String, val source: NagSource)

/**
 * The fallback voice, and the one that has to carry the app before an API key exists.
 *
 * Templates are held to the same rule as the model: they may only say what the verdict already
 * decided. Numbers are quoted from the verdict rather than recomputed, so the words and the
 * "Why this nag?" audit can never disagree.
 */
object NagTemplates {

    fun render(verdict: Verdict): Nag {
        val pick = verdict.computedAt.atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

        return when (verdict.state) {
            VerdictState.FEED_SILENT -> feedSilent(pick)
            VerdictState.COLD_START -> coldStart(verdict)
            VerdictState.RESTING -> resting(verdict, pick)
            VerdictState.IDLE -> idle(verdict, pick)
            VerdictState.STEADY -> steady(verdict, pick)
            VerdictState.PROGRESSING -> progressing(verdict)
            VerdictState.RAMPING_HARD -> rampingHard(verdict)
        }
    }

    private fun feedSilent(pick: Long) = Nag(
        headline = choose(pick, "I have gone deaf.", "Nothing is coming through."),
        body = "Health Connect has not handed me a single thing lately — no workouts, and no " +
            "passive data either. That is a broken pipe rather than a lazy week, so I am not " +
            "going to accuse you of anything. Check that your watch is still syncing.",
        source = NagSource.TEMPLATE,
    )

    private fun coldStart(verdict: Verdict) = Nag(
        headline = "Not enough rope yet.",
        body = buildString {
            append(
                "I have ${verdict.snapshot.chronic.activityCount} session(s) across " +
                    "${verdict.snapshot.historySpanDays ?: 0} day(s). I can tell you what " +
                    "happened, but not yet whether it means anything — trends need a few weeks " +
                    "before they stop lying. ",
            )
            append(goalLine(verdict))
        },
        source = NagSource.TEMPLATE,
    )

    private fun resting(verdict: Verdict, pick: Long) = Nag(
        headline = choose(
            pick,
            "Rest is doing something.",
            "Legitimately recovering.",
            "Nothing new, and that is fine.",
        ),
        body = buildString {
            append(daysLine(verdict))
            append(
                " At ${verdict.snapshot.chronic.loadPerWeek.roundToInt()} HRSS a week that is " +
                    "still inside the window where rest pays you back rather than costing you. ",
            )
            append(statusLine(verdict))
            append(" ").append(goalLine(verdict))
        },
        source = NagSource.TEMPLATE,
    )

    private fun idle(verdict: Verdict, pick: Long): Nag {
        val days = verdict.daysSinceLastActivity ?: 0L

        val headline = when (verdict.detraining) {
            DetrainingBand.LOSING -> choose(pick, "You are losing it now.", "Day $days. Measurably.")
            DetrainingBand.SOFT -> choose(pick, "Right — this is a rebuild.", "Day $days.")
            else -> choose(pick, "Day $days. The rot starts here.", "This is where it slips.")
        }

        val physiology = when (verdict.detraining) {
            DetrainingBand.LOSING ->
                "Past a fortnight the aerobic base actually recedes. That is not nagging, it is " +
                    "physiology, and it does not care why you stopped."

            DetrainingBand.SOFT ->
                "After $days days most of what you built has gone. Coming back is its own " +
                    "project now, so make today small and boring rather than heroic."

            else ->
                "Nothing is lost yet, but this is exactly where a rest week quietly turns into a " +
                    "rest month."
        }

        return Nag(
            headline = headline,
            body = buildString {
                append(daysLine(verdict)).append(' ')
                append(physiology).append(' ')
                append(statusLine(verdict)).append(' ')
                append(goalLine(verdict)).append(' ')
                append(shove(pick))
            },
            source = NagSource.TEMPLATE,
        )
    }

    private fun steady(verdict: Verdict, pick: Long) = Nag(
        headline = choose(pick, "Work is going in.", "Showing up. Nothing dramatic."),
        body = buildString {
            append(newsLine(verdict)).append(' ')
            append(statusLine(verdict)).append(' ')
            append(
                "Nothing moved far enough to celebrate, which is what most weeks look like when " +
                    "they are going well. ",
            )
            append(goalLine(verdict))
        },
        source = NagSource.TEMPLATE,
    )

    private fun progressing(verdict: Verdict): Nag {
        val curve = verdict.headline
        if (curve != null && curve.secondsFasterThanPredicted > 0 && curve.isMeaningful) {
            return beatTheCurve(verdict, curve)
        }

        val efficiency = verdict.efficiency ?: return steady(verdict, 0L)
        return Nag(
            headline = "More speed per heartbeat.",
            body = buildString {
                append(
                    "Over the last 28 days your ${efficiency.key.sport.label.lowercase()} is " +
                        "covering ${"%.1f".format(abs(efficiency.improvementFraction) * 100)}% " +
                        "more ground per beat than the ${efficiency.priorCount} session(s) " +
                        "before that. Same heart rate, more distance — that is aerobic fitness " +
                        "rather than one good day. ",
                )
                append(statusLine(verdict)).append(' ')
                append(goalLine(verdict))
            },
            source = NagSource.TEMPLATE,
        )
    }

    private fun beatTheCurve(verdict: Verdict, curve: CurveComparison): Nag {
        val sport = curve.activity.sport
        val distance = formatDistanceShort(curve.activity.distanceMeters, sport)

        // A thin curve is allowed to report what happened but not to put a number on how good it
        // was. Quoting a percentage off two sessions is precision the data has not earned, and
        // being caught inventing one costs more trust than the compliment was worth.
        if (!curve.isConfident) {
            return Nag(
                headline = "Quickest $distance you have logged.",
                body = buildString {
                    append(
                        "That ${sport.label.lowercase()} came in at " +
                            "${formatClock(curve.actualSeconds)}, ahead of what the rest of your " +
                            "history suggests. I will not put a number on it yet — I have only " +
                            "${curve.fit.sampleCount} other efforts to draw the line through, " +
                            "and two points will agree with anything. Do it a couple more times " +
                            "and I can tell you whether it is a trend or a good day. ",
                    )
                    append(statusLine(verdict)).append(' ')
                    append(goalLine(verdict))
                },
                source = NagSource.TEMPLATE,
            )
        }

        return Nag(
            headline = "${formatGap(curve.secondsFasterThanPredicted)} quicker than you should be.",
            body = buildString {
                append(
                    "That $distance ${sport.label.lowercase()} came in at " +
                        "${formatClock(curve.actualSeconds)}. Fitted from your " +
                        "${curve.fit.sampleCount} best efforts — and deliberately not counting " +
                        "this one — the curve said ${formatClock(curve.predictedSeconds)}. You " +
                        "were ${"%.1f".format(abs(curve.fractionFasterThanPredicted) * 100)}% " +
                        "better than your own form predicts. ",
                )
                append(statusLine(verdict)).append(' ')
                append(goalLine(verdict))
            },
            source = NagSource.TEMPLATE,
        )
    }

    private fun rampingHard(verdict: Verdict) = Nag(
        headline = "Easy, tiger.",
        body = buildString {
            append(
                "${verdict.snapshot.acute.totalLoad.roundToInt()} HRSS in seven days against a " +
                    "base of ${verdict.snapshot.chronic.loadPerWeek.roundToInt()} a week — " +
                    "a ratio of ${"%.2f".format(verdict.acwr ?: 0.0)}. ",
            )
            append(
                "That is the range where the next session starts costing more than it buys. " +
                    "Nothing you are training for gets closer if you are hurt for a month. ",
            )
            append(goalLine(verdict))
        },
        source = NagSource.TEMPLATE,
    )

    private fun daysLine(verdict: Verdict): String = when (val days = verdict.daysSinceLastActivity) {
        null -> "Nothing stored at all."
        0L -> "You went out today."
        1L -> "Last session was yesterday."
        else -> "It has been $days days since your last session."
    }

    private fun newsLine(verdict: Verdict): String {
        val summary = verdict.news.take(3).joinToString(", ") { activity ->
            val sport = activity.sport.label.lowercase()
            // A pool session with no distance recorded still happened; describing it by duration
            // is better than an em dash where a number should be.
            activity.distanceMeters
                ?.let { "${formatDistanceShort(it, activity.sport)} $sport" }
                ?: "${activity.scoredDuration.toMinutes()} min $sport"
        }
        return "${verdict.news.size} session(s) since I last looked: $summary."
    }

    /** The "here is how you have been doing" line the no-news case is built around. */
    private fun statusLine(verdict: Verdict): String {
        val week = verdict.snapshot.acute
        return "Last seven days: ${week.activityCount} session(s), " +
            "${week.activeDays} active day(s), ${week.totalLoad.roundToInt()} HRSS."
    }

    /**
     * The goal is what decides which of these numbers matters, so every nag ends by tying back to
     * it. Without one, the app is a spreadsheet with opinions.
     */
    private fun goalLine(verdict: Verdict): String {
        val goal = verdict.goal
            ?: return "No goal set, so I have nothing to tell you this is for. Set one."

        val progress = verdict.goalProgress
            ?: return "Goal is \"${goal.text}\", but there is nothing to measure it from yet."

        val direction = if (progress.improvementFraction >= 0) "up" else "down"
        val percent = "%.1f".format(abs(progress.improvementFraction) * 100)

        val detail = when (progress.dimension) {
            GoalDimension.DISTANCE ->
                "Longest is ${formatDistanceShort(progress.currentValue, goal.sport)} against " +
                    "${formatDistanceShort(progress.startValue, goal.sport)} when you set it"

            GoalDimension.PACE ->
                "Your ${formatDistanceShort(progress.bucketMeters, goal.sport)} is worth " +
                    "${formatClock(progress.currentValue)} now, against " +
                    "${formatClock(progress.startValue)} when you set it"

            GoalDimension.VOLUME ->
                "You are carrying ${progress.currentValue.roundToInt()} HRSS a week against " +
                    "${progress.startValue.roundToInt()} when you started"
        }

        return if (progress.hasMoved) {
            "On \"${goal.text}\": $detail — $direction $percent%."
        } else {
            "On \"${goal.text}\": $detail. Flat so far."
        }
    }

    private fun shove(pick: Long): String = choose(
        pick,
        "Now go and do something instead of reading this.",
        "Put the phone down and go.",
        "You know what to do. Go do it.",
    )

    private fun choose(pick: Long, vararg options: String): String =
        options[(pick.mod(options.size))]
}
