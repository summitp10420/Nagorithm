package com.nagorithm.app.format

import com.nagorithm.app.health.SportCategory
import java.time.Duration
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

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

/** Short form for prose, where the parenthetical conversion would get in the way. */
fun formatDistanceShort(meters: Double?, sport: SportCategory? = null): String {
    if (meters == null || meters <= 0.0) return "—"
    return if (sport == SportCategory.SWIMMING) {
        String.format(Locale.US, "%.0f m", meters)
    } else {
        String.format(Locale.US, "%.1f km", meters / 1000.0)
    }
}

fun formatHeartRate(bpm: Long?): String = bpm?.let { "$it bpm" } ?: "—"

/**
 * Swimmers and rowers think in time per 100 m; everyone else thinks per kilometre or mile. Quoting
 * a swim at 19:22 per kilometre is technically correct and completely unreadable.
 */
fun formatPace(secondsPerKm: Double, sport: SportCategory?): String = when (sport) {
    SportCategory.SWIMMING -> "${clock((secondsPerKm / 10.0).roundToLong())} /100m"

    // Rowers read splits per 500 m and nothing else. Quoting a 2 km piece per 100 m is the kind
    // of detail that tells a rower the app was built by someone who has never sat on an erg.
    SportCategory.ROWING -> "${clock((secondsPerKm / 2.0).roundToLong())} /500m"

    SportCategory.CYCLING ->
        String.format(Locale.US, "%.1f km/h", 3600.0 / secondsPerKm)

    else -> "${clock(secondsPerKm.roundToLong())} /km"
}

/** A total time, the way it would appear on a race clock. */
fun formatClock(seconds: Double): String {
    val total = seconds.roundToLong()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val secs = total % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, secs)
    }
}

/** A bare gap in seconds, said the way a person would say it. */
fun formatGap(seconds: Double): String {
    val rounded = abs(seconds).roundToLong()
    return when {
        rounded < 60L -> "$rounded seconds"
        rounded < 600L -> clock(rounded) + " minutes"
        else -> "${rounded / 60L} minutes"
    }
}

private fun clock(totalSeconds: Long): String =
    String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
