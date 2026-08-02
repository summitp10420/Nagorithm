package com.nagorithm.app.verdict

import com.nagorithm.app.data.GoalDimension
import java.time.ZoneId

enum class NagSource { TEMPLATE, MODEL }

data class Nag(val headline: String, val body: String, val source: NagSource)

/**
 * The fallback voice, and the one that has to carry the app before an API key exists.
 *
 * Held to the same rule as the model: it may only say what the verdict already decided, and it says
 * it without a single digit. The figures still exist and are still exact — they are in the
 * "Why this nag?" audit, computed once and quoted nowhere else. But a person deciding whether to
 * train today is not helped by a ratio, and an app that recites one has confused being precise with
 * being useful.
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
                "I have ${Words.sessions(verdict.snapshot.chronic.activityCount)} to go on, " +
                    "spread over ${Words.span(verdict.snapshot.historySpanDays)}. I can tell you " +
                    "what happened, but not yet whether it means anything — trends need longer " +
                    "than this before they stop lying. ",
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
            append(daysLine(verdict)).append(' ')
            append(
                "For weeks as ${Words.weekly(verdict.snapshot.chronic.loadPerWeek)} as yours, " +
                    "that is still inside the stretch where rest pays you back rather than " +
                    "costing you. ",
            )
            append(statusLine(verdict)).append(' ')
            append(goalLine(verdict))
        },
        source = NagSource.TEMPLATE,
    )

    private fun idle(verdict: Verdict, pick: Long): Nag {
        val headline = when (verdict.detraining) {
            DetrainingBand.LOSING ->
                choose(pick, "You are losing it now.", "This has gone on too long.")

            DetrainingBand.SOFT ->
                choose(pick, "Right — this is a rebuild.", "Long enough to be a restart.")

            else -> choose(pick, "The rot starts here.", "This is where it slips.")
        }

        val physiology = when (verdict.detraining) {
            DetrainingBand.LOSING ->
                "Past a fortnight the aerobic base actually recedes. That is not nagging, it is " +
                    "physiology, and it does not care why you stopped."

            DetrainingBand.SOFT ->
                "After this long most of what you built has gone. Coming back is its own project " +
                    "now, so make today small and boring rather than heroic."

            else ->
                "Nothing is lost yet, but this is exactly where a rest week quietly turns into a " +
                    "rest month."
        }

        return Nag(
            headline = headline,
            body = buildString {
                append(daysLine(verdict)).append(' ')
                append(physiology).append(' ')
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
            val sport = curve.activity.sport.label.lowercase()

            // A thin curve may report what happened but not how good it was. Two points agree with
            // any line drawn through them, and being caught overclaiming costs more than the
            // compliment was worth.
            val body = if (curve.isConfident) {
                "That $sport came in ${Words.magnitude(curve.fractionFasterThanPredicted)} ahead " +
                    "of what the curve through your own best efforts says you should manage — and " +
                    "that curve was fitted without counting this one. Your own form did not " +
                    "predict this. "
            } else {
                "That $sport came in ahead of what the rest of your history suggests. I will not " +
                    "make more of it than that yet — there are only a couple of other efforts to " +
                    "draw the line through, and two points will agree with anything. Do it again " +
                    "and I can tell you whether it is a trend or a good day. "
            }

            return Nag(
                headline = if (curve.isConfident) {
                    "Faster than your own form predicts."
                } else {
                    "Your quickest yet at that distance."
                },
                body = buildString {
                    append(body)
                    append(statusLine(verdict)).append(' ')
                    append(goalLine(verdict))
                },
                source = NagSource.TEMPLATE,
            )
        }

        val efficiency = verdict.efficiency ?: return steady(verdict, 0L)
        return Nag(
            headline = "More speed per heartbeat.",
            body = buildString {
                append(
                    "Your ${efficiency.key.sport.label.lowercase()} is covering " +
                        "${Words.magnitude(efficiency.improvementFraction)} more ground per beat " +
                        "than it was before. Same heart rate, more distance — that is aerobic " +
                        "fitness rather than one good day. ",
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
                "You have piled on far more this week than the weeks behind it have been " +
                    "carrying. That is the range where the next session starts costing more than " +
                    "it buys, and the cost is usually paid somewhere you were not watching — a " +
                    "tendon, a night's sleep, a fortnight off. ",
            )
            append("Nothing you are training for gets closer if you are hurt for a month. ")
            append(goalLine(verdict))
        },
        source = NagSource.TEMPLATE,
    )

    private fun daysLine(verdict: Verdict): String = when (verdict.daysSinceLastActivity) {
        null -> "Nothing stored at all."
        0L -> "You went out today."
        1L -> "Your last session was yesterday."
        else -> "Your last session was ${Words.days(verdict.daysSinceLastActivity)}."
    }

    private fun newsLine(verdict: Verdict): String {
        if (verdict.news.isEmpty()) return "Nothing new since I last looked."

        val sports = verdict.news.map { it.sport.label.lowercase() }.distinct()
        return "${Words.capitalise(Words.sessions(verdict.news.size))} since I last looked — " +
            "${sports.joinToString(", ")}."
    }

    /**
     * The "here is how you have been going" line, as a register rather than a scoreline.
     *
     * "Across the whole week" earns its length: this follows a line about what arrived since the
     * last check-in, and without the change of scope being spelled out, one new session followed by
     * a busy week reads as a contradiction.
     */
    private fun statusLine(verdict: Verdict): String {
        val week = verdict.snapshot.acute
        if (week.activityCount == 0) return "Nothing at all in the last week."

        return "Across the whole week that is ${Words.sessions(week.activityCount)} at a " +
            "${Words.weekly(week.loadPerWeek)} load for you."
    }

    /**
     * The goal is what decides which of this matters, so every nag ends by tying back to it.
     * Without one, the app is a spreadsheet with opinions.
     */
    private fun goalLine(verdict: Verdict): String {
        val goal = verdict.goal
            ?: return "No goal set, so I have nothing to tell you this is for. Set one."

        val progress = verdict.goalProgress
            ?: return "Goal is \"${goal.text}\", but there is nothing to measure it from yet."

        if (!progress.hasMoved) return "On \"${goal.text}\": flat so far."

        // Each dimension needs its own comparative. "The load you are carrying is worse" is not
        // English, and a goal line that reads like a translation undoes the point of writing it out.
        val (subject, better, worse) = when (progress.dimension) {
            GoalDimension.DISTANCE ->
                Triple("your longest effort is", "further", "shorter")

            GoalDimension.PACE ->
                Triple("your pace over that distance is", "quicker", "slower")

            GoalDimension.VOLUME ->
                Triple("the load you are carrying is", "higher", "lower")
        }
        val direction = if (progress.improvementFraction >= 0) better else worse

        return "On \"${goal.text}\": $subject " +
            "${Words.magnitude(progress.improvementFraction)} $direction than when you set it."
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
