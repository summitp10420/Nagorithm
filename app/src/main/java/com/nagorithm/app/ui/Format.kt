package com.nagorithm.app.ui

import com.nagorithm.app.data.StoredActivity
import com.nagorithm.app.health.SportCategory
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm a", Locale.getDefault())

fun StoredActivity.formattedStart(): String {
    val zone: ZoneId = zoneOffsetSeconds
        ?.let { ZoneOffset.ofTotalSeconds(it) }
        ?: ZoneId.systemDefault()
    return dateFormatter.format(Instant.ofEpochMilli(startTimeEpochMillis).atZone(zone))
}

fun StoredActivity.formattedFirstSeen(): String =
    dateFormatter.format(
        Instant.ofEpochMilli(firstSeenEpochMillis).atZone(ZoneId.systemDefault()),
    )

fun formatDuration(duration: Duration): String {
    val totalSeconds = duration.seconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/**
 * Swims are hundreds of metres, not tens of kilometres, so rendering them in km makes every pool
 * session read as a rounding error.
 */
fun formatDistance(meters: Double?, sport: SportCategory? = null): String {
    if (meters == null || meters <= 0.0) return "—"
    return if (sport == SportCategory.SWIMMING) {
        String.format(Locale.US, "%.0f m (%.0f yd)", meters, meters / 0.9144)
    } else {
        String.format(Locale.US, "%.2f km (%.2f mi)", meters / 1000.0, meters / 1609.344)
    }
}

fun formatHeartRate(bpm: Long?): String = bpm?.let { "$it bpm" } ?: "—"
