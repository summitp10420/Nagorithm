package com.nagorithm.app.verdict

import com.nagorithm.app.data.SpokenNag
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

data class NagDecision(val speak: Boolean, val reason: String)

/**
 * When to open its mouth.
 *
 * Deliberately not a timer. A fixed weekly ping arrives whether or not anything happened, which
 * trains the user to dismiss it unread — and an app that gets dismissed unread has failed no
 * matter how good the analysis behind it was. So the rule is: speak when there is something to
 * say, with a floor so silence never becomes permanent and a ceiling so enthusiasm never becomes
 * harassment.
 *
 * Pure, because the difference between useful and obnoxious lives entirely in these thresholds
 * and they need to be adjustable against tests rather than against a phone over several days.
 */
object NagSchedule {

    /** Nagging someone at 3am about their cycling is how an app gets uninstalled by morning. */
    private const val WAKING_FROM_HOUR = 7
    private const val WAKING_UNTIL_HOUR = 21

    /**
     * Under a day, so a nag delivered late one afternoon is not pushed past the same slot the
     * next day and gradually walked into the evening.
     */
    private val CEILING = Duration.ofHours(20)

    /** Doing nothing is the case worth interrupting for, so it gets the shortest leash. */
    private val IDLE_FLOOR = Duration.ofDays(2)

    /** Otherwise the app checks in weekly whether or not it has anything new. */
    private val ROUTINE_FLOOR = Duration.ofDays(7)

    fun decide(
        verdict: Verdict,
        last: SpokenNag?,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): NagDecision {
        val hour = now.atZone(zone).hour
        if (hour < WAKING_FROM_HOUR || hour >= WAKING_UNTIL_HOUR) {
            return NagDecision(false, "Outside waking hours.")
        }

        if (last == null) return NagDecision(true, "Nothing has ever been said.")

        val since = Duration.between(Instant.ofEpochMilli(last.spokenAtEpochMillis), now)
        if (since < CEILING) {
            return NagDecision(false, "Already spoke ${since.toHours()} hour(s) ago.")
        }

        val newsSinceSpeaking = verdict.news.any {
            it.firstSeenEpochMillis > last.spokenAtEpochMillis
        }

        return when {
            // Not the user's fault, so it gets said once and then left alone. Repeating it daily
            // would be blaming them for a broken pipe on a schedule.
            verdict.state == VerdictState.FEED_SILENT ->
                floorReached(since, ROUTINE_FLOOR, "The feed is still silent.")

            verdict.state == VerdictState.COLD_START ->
                floorReached(since, ROUTINE_FLOOR, "Still nothing much to go on.")

            newsSinceSpeaking ->
                NagDecision(true, "A session arrived since I last spoke.")

            verdict.state == VerdictState.IDLE && last.state != VerdictState.IDLE ->
                NagDecision(true, "Crossed from ${last.state.name.lowercase()} into idle.")

            verdict.state == VerdictState.IDLE ->
                floorReached(since, IDLE_FLOOR, "Still nothing being done.")

            else -> floorReached(since, ROUTINE_FLOOR, "Weekly check-in.")
        }
    }

    private fun floorReached(since: Duration, floor: Duration, reason: String): NagDecision =
        if (since >= floor) {
            NagDecision(true, reason)
        } else {
            NagDecision(false, "$reason Not due for another ${(floor - since).toHours()} hour(s).")
        }
}
