package com.nagorithm.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nagorithm.app.health.ActivitySummary
import com.nagorithm.app.health.SportCategory
import com.nagorithm.app.health.sportCategoryOf
import java.time.Duration
import java.time.Instant

/** v1 is single user. This stands in for an account id so multi-user is a data change later. */
const val LOCAL_USER_ID = "local"

@Entity(
    tableName = "activities",
    indices = [Index("userId", "startTimeEpochMillis")],
)
data class StoredActivity(
    /** Health Connect's own record id. Primary key, so re-reads dedupe for free. */
    @PrimaryKey val recordId: String,
    val userId: String,
    val startTimeEpochMillis: Long,
    val endTimeEpochMillis: Long,
    val zoneOffsetSeconds: Int?,
    val title: String?,
    val exerciseType: Int,
    val isIndoor: Boolean,
    val activeDurationSeconds: Long?,
    val distanceMeters: Double?,
    val averageHeartRateBpm: Long?,
    val sourcePackage: String,
    /**
     * When this app first saw the record, which is not when the session happened. A session can
     * sync late and land inside a window that has already been judged.
     */
    val firstSeenEpochMillis: Long,

    /** High percentile of the session's samples, so one sensor spike cannot define it. */
    val peakHeartRateBpm: Long? = null,
    val hrSampleCount: Int = 0,

    @Embedded val load: LoadScore? = null,
) {
    /** Derived rather than stored, so the taxonomy has one definition. */
    val sport: SportCategory get() = sportCategoryOf(exerciseType) ?: SportCategory.OTHER

    val elapsedDuration: Duration
        get() = Duration.ofMillis(endTimeEpochMillis - startTimeEpochMillis)

    val activeDuration: Duration?
        get() = activeDurationSeconds?.let(Duration::ofSeconds)

    val scoredDuration: Duration get() = activeDuration ?: elapsedDuration

    val startInstant: Instant get() = Instant.ofEpochMilli(startTimeEpochMillis)
}

fun ActivitySummary.toStoredActivity(
    userId: String = LOCAL_USER_ID,
    firstSeenAt: Instant = Instant.now(),
    peakHeartRateBpm: Long? = null,
): StoredActivity = StoredActivity(
    recordId = recordId,
    userId = userId,
    startTimeEpochMillis = startTime.toEpochMilli(),
    endTimeEpochMillis = endTime.toEpochMilli(),
    zoneOffsetSeconds = startZoneOffset?.totalSeconds,
    title = title,
    exerciseType = exerciseType,
    isIndoor = isIndoor,
    activeDurationSeconds = activeDuration?.seconds,
    distanceMeters = distanceMeters,
    averageHeartRateBpm = averageHeartRateBpm,
    sourcePackage = sourcePackage,
    firstSeenEpochMillis = firstSeenAt.toEpochMilli(),
    peakHeartRateBpm = peakHeartRateBpm,
    hrSampleCount = heartRateSamples.size,
)
