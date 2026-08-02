package com.nagorithm.app

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.nagorithm.app.data.LoadModel
import com.nagorithm.app.data.LoadScore
import com.nagorithm.app.data.StoredActivity
import java.time.Instant
import java.time.temporal.ChronoUnit

val NOW: Instant = Instant.parse("2026-08-01T12:00:00Z")

fun Instant.daysAgo(days: Long): Instant = minus(days, ChronoUnit.DAYS)

/**
 * Fixture for the scoring maths. Defaults are deliberately unremarkable so each test only has to
 * state the one thing it is actually about.
 */
fun activity(
    id: String,
    daysAgo: Long,
    exerciseType: Int = ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
    distanceMeters: Double? = 40_000.0,
    durationMinutes: Long = 80,
    hrss: Double? = 60.0,
    isIndoor: Boolean = false,
    firstSeenDaysAgo: Long = daysAgo,
    now: Instant = NOW,
): StoredActivity {
    val start = now.daysAgo(daysAgo)
    return StoredActivity(
        recordId = id,
        userId = "local",
        startTimeEpochMillis = start.toEpochMilli(),
        endTimeEpochMillis = start.plus(durationMinutes, ChronoUnit.MINUTES).toEpochMilli(),
        zoneOffsetSeconds = 0,
        title = null,
        exerciseType = exerciseType,
        isIndoor = isIndoor,
        activeDurationSeconds = durationMinutes * 60,
        distanceMeters = distanceMeters,
        averageHeartRateBpm = 140,
        sourcePackage = "test",
        firstSeenEpochMillis = now.daysAgo(firstSeenDaysAgo).toEpochMilli(),
        peakHeartRateBpm = 170,
        hrSampleCount = 100,
        load = hrss?.let {
            LoadScore(
                hrss = it,
                model = LoadModel.HRSS.name,
                restingHr = 45,
                maxHr = 180,
                thresholdHr = 153,
            )
        },
    )
}
