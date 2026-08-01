package com.nagorithm.app.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

enum class HealthConnectAvailability {
    NOT_SUPPORTED,
    UPDATE_REQUIRED,
    AVAILABLE,
}

class HealthConnectManager(private val context: Context) {

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
    )

    fun availability(): HealthConnectAvailability =
        when (HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)) {
            HealthConnectClient.SDK_UNAVAILABLE ->
                HealthConnectAvailability.NOT_SUPPORTED

            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.UPDATE_REQUIRED

            else -> HealthConnectAvailability.AVAILABLE
        }

    fun clientOrNull(): HealthConnectClient? =
        if (availability() == HealthConnectAvailability.AVAILABLE) {
            HealthConnectClient.getOrCreate(context, PROVIDER_PACKAGE)
        } else {
            null
        }

    suspend fun grantedPermissions(): Set<String> =
        clientOrNull()?.permissionController?.getGrantedPermissions().orEmpty()

    /** Cycling sessions in the window, newest first, plus a debug scan of everything else. */
    suspend fun readCyclingSessions(from: Instant, to: Instant): RideFetchResult {
        val client = clientOrNull()
            ?: return RideFetchResult(
                emptyList(),
                ExerciseScanDiagnostics(0, emptyMap(), emptySet()),
            )

        val sessions = readSessions(client, from, to)

        val rides = sessions
            .filter { it.exerciseType in CYCLING_EXERCISE_TYPES }
            .sortedByDescending { it.startTime }
            .map { summarize(client, it) }

        return RideFetchResult(
            rides = rides,
            diagnostics = ExerciseScanDiagnostics(
                sessionsInWindow = sessions.size,
                typeCounts = sessions
                    .groupingBy { exerciseTypeName(it.exerciseType) }
                    .eachCount(),
                writerPackages = sessions
                    .map { it.metadata.dataOrigin.packageName }
                    .toSet(),
            ),
        )
    }

    private suspend fun readSessions(
        client: HealthConnectClient,
        from: Instant,
        to: Instant,
    ): List<ExerciseSessionRecord> {
        val sessions = mutableListOf<ExerciseSessionRecord>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    pageToken = pageToken,
                ),
            )
            sessions += response.records
            pageToken = response.pageToken
        } while (!pageToken.isNullOrEmpty())
        return sessions
    }

    /**
     * Distance and heart rate live in separate record types, so they have to be aggregated over
     * the session's window. The data origin filter keeps a second app's overlapping recording of
     * the same ride from being counted twice.
     */
    private suspend fun summarize(
        client: HealthConnectClient,
        session: ExerciseSessionRecord,
    ): RideSummary {
        val aggregated = runCatching {
            client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
                        DistanceRecord.DISTANCE_TOTAL,
                        HeartRateRecord.BPM_AVG,
                    ),
                    timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime),
                    dataOriginFilter = setOf(session.metadata.dataOrigin),
                ),
            )
        }.getOrNull()

        return RideSummary(
            recordId = session.metadata.id,
            startTime = session.startTime,
            endTime = session.endTime,
            startZoneOffset = session.startZoneOffset,
            title = session.title,
            isStationary = session.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
            activeDuration = aggregated?.get(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL),
            distanceMeters = aggregated?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters,
            averageHeartRateBpm = aggregated?.get(HeartRateRecord.BPM_AVG),
            sourcePackage = session.metadata.dataOrigin.packageName,
        )
    }

    companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

        private val CYCLING_EXERCISE_TYPES = setOf(
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
        )

        private fun exerciseTypeName(type: Int): String = when (type) {
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "BIKING"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "BIKING_STATIONARY"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "RUNNING"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "RUNNING_TREADMILL"
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "WALKING"
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "HIKING"
            ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "ELLIPTICAL"
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "ROWING_MACHINE"
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "WEIGHTLIFTING"
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "STRENGTH_TRAINING"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "SWIMMING_POOL"
            ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT -> "OTHER_WORKOUT"
            else -> "TYPE_$type"
        }
        private const val PLAY_STORE_PACKAGE = "com.android.vending"

        /**
         * Deep link that sends the user to the Health Connect listing, including the onboarding
         * hint the provider expects so a fresh install lands on setup rather than a blank app.
         */
        fun providerInstallIntent(callerPackage: String): Intent =
            Intent(Intent.ACTION_VIEW).apply {
                setPackage(PLAY_STORE_PACKAGE)
                data = Uri.parse(
                    "market://details" +
                        "?id=$PROVIDER_PACKAGE" +
                        "&url=healthconnect%3A%2F%2Fonboarding",
                )
                putExtra("overlay", true)
                putExtra("callerId", callerPackage)
            }
    }
}
