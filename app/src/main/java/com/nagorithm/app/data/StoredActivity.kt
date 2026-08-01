package com.nagorithm.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nagorithm.app.health.RideSummary
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val isStationary: Boolean,
    val activeDurationSeconds: Long?,
    val distanceMeters: Double?,
    val averageHeartRateBpm: Long?,
    val sourcePackage: String,
    /**
     * When this app first saw the record, which is not when the ride happened. A ride can sync
     * late and land inside a window that has already been judged.
     */
    val firstSeenEpochMillis: Long,
) {
    val elapsedDuration: Duration
        get() = Duration.ofMillis(endTimeEpochMillis - startTimeEpochMillis)

    val activeDuration: Duration?
        get() = activeDurationSeconds?.let(Duration::ofSeconds)
}

fun RideSummary.toStoredActivity(
    userId: String = LOCAL_USER_ID,
    firstSeenAt: Instant = Instant.now(),
): StoredActivity = StoredActivity(
    recordId = recordId,
    userId = userId,
    startTimeEpochMillis = startTime.toEpochMilli(),
    endTimeEpochMillis = endTime.toEpochMilli(),
    zoneOffsetSeconds = startZoneOffset?.totalSeconds,
    title = title,
    isStationary = isStationary,
    activeDurationSeconds = activeDuration?.seconds,
    distanceMeters = distanceMeters,
    averageHeartRateBpm = averageHeartRateBpm,
    sourcePackage = sourcePackage,
    firstSeenEpochMillis = firstSeenAt.toEpochMilli(),
)

private val activityDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm a", Locale.getDefault())

fun StoredActivity.formattedStart(): String {
    val zone: ZoneId = zoneOffsetSeconds
        ?.let { ZoneOffset.ofTotalSeconds(it) }
        ?: ZoneId.systemDefault()
    return activityDateFormatter.format(
        Instant.ofEpochMilli(startTimeEpochMillis).atZone(zone),
    )
}

fun StoredActivity.formattedFirstSeen(): String =
    activityDateFormatter.format(
        Instant.ofEpochMilli(firstSeenEpochMillis).atZone(ZoneId.systemDefault()),
    )
