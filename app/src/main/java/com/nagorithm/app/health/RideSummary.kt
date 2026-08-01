package com.nagorithm.app.health

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Debug readout for Stage 2. An empty ride list is ambiguous on its own — it could mean no
 * cycling was recorded, or that nothing writes to Health Connect at all. This separates those.
 *
 * The scan cannot look past the window: without READ_HEALTH_DATA_HISTORY, Health Connect only
 * serves data from the 30 days before permissions were first granted.
 */
data class ExerciseScanDiagnostics(
    val sessionsInWindow: Int,
    val typeCounts: Map<String, Int>,
    val writerPackages: Set<String>,
)

data class RideFetchResult(
    val rides: List<RideSummary>,
    val diagnostics: ExerciseScanDiagnostics,
)

data class RideSummary(
    val recordId: String,
    val startTime: Instant,
    val endTime: Instant,
    val startZoneOffset: ZoneOffset?,
    val title: String?,
    val isStationary: Boolean,
    val activeDuration: Duration?,
    val distanceMeters: Double?,
    val averageHeartRateBpm: Long?,
    val sourcePackage: String,
) {
    val elapsedDuration: Duration get() = Duration.between(startTime, endTime)
}

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm a", Locale.getDefault())

fun RideSummary.formattedStart(): String {
    val zone: ZoneId = startZoneOffset ?: ZoneId.systemDefault()
    return dateFormatter.format(startTime.atZone(zone))
}

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

fun formatDistance(meters: Double?): String {
    if (meters == null) return "—"
    val km = meters / 1000.0
    val miles = meters / 1609.344
    return String.format(Locale.US, "%.2f km (%.2f mi)", km, miles)
}

fun formatHeartRate(bpm: Long?): String = bpm?.let { "$it bpm" } ?: "—"
