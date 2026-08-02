package com.nagorithm.app.llm

import com.nagorithm.app.data.Note
import com.nagorithm.app.data.StoredActivity
import com.nagorithm.app.format.formatClock
import com.nagorithm.app.format.formatPace
import com.nagorithm.app.health.SportCategory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The last few weeks, written out the way a person would describe them.
 *
 * The verdict alone is not enough material to say anything interesting. "You are progressing" is a
 * conclusion; "the swim came after the row and the lift, and was still quicker than three weeks
 * ago" is an observation, and only the second one sounds like it was paid attention to. That
 * observation needs sessions in order, which is all this is.
 *
 * Deliberately far more than ever gets said. The point of the extra is to let the model choose
 * which two or three things are worth mentioning, not to have it recite them.
 */
object RecentPicture {

    /** Long enough for a trend to be visible, short enough that last spring is not evidence. */
    private const val WINDOW_DAYS = 42L

    /** Sports drop off the radar quietly. Past this it is worth naming out loud. */
    private const val QUIET_DAYS = 14L

    private const val SERIES_LENGTH = 6

    /** Notes stay relevant for far longer than sessions do, so they get their own horizon. */
    const val NOTE_WINDOW_DAYS = 180L

    private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

    /**
     * What the user has said about themselves, with how long ago they said it.
     *
     * Ages are attached rather than dates because the whole value of a note is when it was true
     * relative to the training around it. "Eleven days ago: cutting sugar" can be lined up against
     * the sessions since; "14 Jul: cutting sugar" makes the model do date arithmetic to reach the
     * same place, and it will occasionally get it wrong.
     */
    fun notes(notes: List<Note>, now: Instant = Instant.now()): List<String> {
        if (notes.isEmpty()) return emptyList()

        return buildList {
            add("WHAT THEY HAVE SAID ABOUT THEMSELVES, most recent first:")
            notes
                .sortedByDescending { it.writtenAtEpochMillis }
                .take(MAX_NOTES)
                .forEach { note ->
                    val days = ChronoUnit.DAYS.between(
                        Instant.ofEpochMilli(note.writtenAtEpochMillis),
                        now,
                    )
                    val age = when (days) {
                        0L -> "today"
                        1L -> "yesterday"
                        else -> "$days days ago"
                    }
                    add("- $age: \"${note.text}\"")
                }
        }
    }

    private const val MAX_NOTES = 20

    fun build(activities: List<StoredActivity>, now: Instant = Instant.now()): List<String> {
        val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
        val from = now.minus(WINDOW_DAYS, ChronoUnit.DAYS).toEpochMilli()
        val recent = activities.filter { it.startTimeEpochMillis >= from }

        if (recent.isEmpty()) return listOf("No sessions at all in the last $WINDOW_DAYS days.")

        return buildList {
            add("RECENT SESSIONS, newest day first. Within a day they are in the order they happened:")
            addAll(byDay(recent, today))

            val trends = bySport(recent)
            if (trends.isNotEmpty()) {
                add("")
                add("THE SAME SESSIONS GROUPED BY SPORT, newest first, so a trend is visible:")
                addAll(trends)
            }

            val quiet = quietSports(activities, now)
            if (quiet.isNotEmpty()) {
                add("")
                add("NOT TOUCHED IN A WHILE:")
                addAll(quiet)
            }
        }
    }

    private fun byDay(recent: List<StoredActivity>, today: LocalDate): List<String> =
        recent
            .groupBy { it.localDate() }
            .toSortedMap(reverseOrder())
            .map { (date, sessions) ->
                val ordered = sessions.sortedBy { it.startTimeEpochMillis }.map(::describe)
                "${dayFormat.format(date)} (${ago(date, today)}): ${ordered.joinToString("; then ")}"
            }

    private fun bySport(recent: List<StoredActivity>): List<String> =
        recent
            .groupBy { it.sport }
            .filter { (_, sessions) -> sessions.size >= 2 }
            .map { (sport, sessions) ->
                val series = sessions
                    .sortedByDescending { it.startTimeEpochMillis }
                    .take(SERIES_LENGTH)
                    .joinToString(", ") { describe(it, withHeartRate = false) }
                "${sport.label}: $series"
            }

    /**
     * Measured from the whole history rather than the window, or a sport abandoned two months ago
     * would look like one that was never done at all and quietly stop being mentioned.
     */
    private fun quietSports(activities: List<StoredActivity>, now: Instant): List<String> =
        activities
            .groupBy { it.sport }
            .mapNotNull { (sport, sessions) ->
                val last = sessions.maxOf { it.startTimeEpochMillis }
                val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(last), now)
                if (days >= QUIET_DAYS) "${sport.label}: nothing for $days days" else null
            }
            .sorted()

    private fun describe(activity: StoredActivity, withHeartRate: Boolean = true): String {
        val seconds = activity.scoredDuration.seconds
        val meters = activity.distanceMeters

        val core = if (meters != null && meters > 0.0 && seconds > 0L) {
            val pace = formatPace(seconds / (meters / 1000.0), activity.sport)
            "${activity.sport.label} ${distance(meters, activity.sport)} in " +
                "${formatClock(seconds.toDouble())} ($pace)"
        } else {
            "${activity.sport.label} for ${formatClock(seconds.toDouble())}"
        }

        val extras = buildList {
            if (withHeartRate) activity.averageHeartRateBpm?.let { add("avg HR $it") }
            if (withHeartRate) activity.load?.let { add("load ${it.hrss.toInt()}") }
            if (activity.isIndoor) add("indoor")
        }

        return if (extras.isEmpty()) core else "$core, ${extras.joinToString(", ")}"
    }

    /** Pool sessions are hundreds of metres; rendering them in kilometres reads as a rounding error. */
    private fun distance(meters: Double, sport: SportCategory): String =
        if (sport == SportCategory.SWIMMING || meters < 2_000.0) {
            String.format(Locale.US, "%,.0f m", meters)
        } else {
            String.format(Locale.US, "%.1f km", meters / 1000.0)
        }

    private fun ago(date: LocalDate, today: LocalDate): String =
        when (val days = ChronoUnit.DAYS.between(date, today)) {
            0L -> "today"
            1L -> "yesterday"
            else -> "$days days ago"
        }

    private fun StoredActivity.localDate(): LocalDate {
        val zone = zoneOffsetSeconds?.let { ZoneOffset.ofTotalSeconds(it) } ?: ZoneId.systemDefault()
        return Instant.ofEpochMilli(startTimeEpochMillis).atZone(zone).toLocalDate()
    }
}
