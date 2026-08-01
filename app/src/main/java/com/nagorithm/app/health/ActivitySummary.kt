package com.nagorithm.app.health

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Debug readout. An empty result is ambiguous on its own — it could mean nothing was recorded, or
 * that nothing writes to Health Connect at all. This separates those.
 *
 * The scan cannot look past the window: without READ_HEALTH_DATA_HISTORY, Health Connect only
 * serves data from the 30 days before permissions were first granted.
 */
data class ExerciseScanDiagnostics(
    val sessionsInWindow: Int,
    val aerobicSessions: Int,
    val typeCounts: Map<String, Int>,
    val writerPackages: Set<String>,
)

data class ActivityFetchResult(
    val activities: List<ActivitySummary>,
    val diagnostics: ExerciseScanDiagnostics,
)

data class HeartRateSample(
    val time: Instant,
    val bpm: Long,
)

data class ActivitySummary(
    val recordId: String,
    val startTime: Instant,
    val endTime: Instant,
    val startZoneOffset: ZoneOffset?,
    val title: String?,
    val exerciseType: Int,
    val sport: SportCategory,
    val isIndoor: Boolean,
    val activeDuration: Duration?,
    val distanceMeters: Double?,
    val averageHeartRateBpm: Long?,
    val sourcePackage: String,
    /** Full series, not the average. Load is summed per sample. */
    val heartRateSamples: List<HeartRateSample> = emptyList(),
) {
    val elapsedDuration: Duration get() = Duration.between(startTime, endTime)
}
