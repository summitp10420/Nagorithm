package com.nagorithm.app.health

import android.content.Context
import java.time.Instant
import java.time.temporal.ChronoUnit

/** A year, which is several times over what the 42-day fitness average needs to be honest. */
const val DEEP_IMPORT_DAYS = 365L

/** Enough to catch a session that arrived late, and nothing more. */
const val ROUTINE_SYNC_DAYS = 30L

/**
 * How far back the app has already asked Health Connect to look.
 *
 * Reading a year of sessions means reading a year of heart-rate samples with them, which is slow
 * and pointless to repeat — there is nothing older next time. So the deep read happens once, and
 * every sync after it goes back a month to pick up anything that arrived late.
 *
 * Stored outside the database on purpose. This is bookkeeping about a fetch, not something the
 * user did, and it should not survive as a row that a future migration has to reason about.
 */
class ImportWatermark(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    val importedFrom: Instant?
        get() = prefs.getLong(KEY_FROM, 0L).takeIf { it > 0L }?.let(Instant::ofEpochMilli)

    /** Only ever moves earlier. A shallow sync must not erase the fact that a deep one happened. */
    fun record(from: Instant) {
        val existing = importedFrom
        if (existing == null || from.isBefore(existing)) {
            prefs.edit().putLong(KEY_FROM, from.toEpochMilli()).apply()
        }
    }

    /** Forces the next sync to go deep again, for when history is granted after the first run. */
    fun forget() {
        prefs.edit().remove(KEY_FROM).apply()
    }

    private companion object {
        const val FILE = "nagorithm-import"
        const val KEY_FROM = "importedFromEpochMillis"
    }
}

/**
 * Where the next read should start.
 *
 * Without the history permission there is no point asking for more than a month; Health Connect
 * will serve the 30 days before permissions were granted and quietly drop the rest, so a wider
 * request costs time and returns the same rows.
 */
fun importWindowStart(
    importedFrom: Instant?,
    now: Instant,
    canReadHistory: Boolean,
): Instant {
    val routine = now.minus(ROUTINE_SYNC_DAYS, ChronoUnit.DAYS)
    if (!canReadHistory) return routine

    val deep = now.minus(DEEP_IMPORT_DAYS, ChronoUnit.DAYS)
    return if (importedFrom != null && !importedFrom.isAfter(deep)) routine else deep
}
