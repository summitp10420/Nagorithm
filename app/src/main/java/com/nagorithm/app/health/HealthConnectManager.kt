package com.nagorithm.app.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
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
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
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

    /** Aerobic sessions in the window, newest first, plus a debug scan of everything present. */
    suspend fun readAerobicSessions(from: Instant, to: Instant): ActivityFetchResult {
        val client = clientOrNull()
            ?: return ActivityFetchResult(
                emptyList(),
                ExerciseScanDiagnostics(0, 0, emptyMap(), emptySet()),
            )

        val sessions = readSessions(client, from, to)
        val aerobic = sessions.filter { sportCategoryOf(it.exerciseType) != null }

        return ActivityFetchResult(
            activities = aerobic
                .sortedByDescending { it.startTime }
                .map { summarize(client, it) },
            diagnostics = ExerciseScanDiagnostics(
                sessionsInWindow = sessions.size,
                aerobicSessions = aerobic.size,
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
     * the same session from being counted twice.
     */
    private suspend fun summarize(
        client: HealthConnectClient,
        session: ExerciseSessionRecord,
    ): ActivitySummary {
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

        return ActivitySummary(
            recordId = session.metadata.id,
            startTime = session.startTime,
            endTime = session.endTime,
            startZoneOffset = session.startZoneOffset,
            title = session.title,
            exerciseType = session.exerciseType,
            sport = sportCategoryOf(session.exerciseType) ?: SportCategory.OTHER,
            isIndoor = isIndoorType(session.exerciseType),
            activeDuration = aggregated?.get(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL),
            distanceMeters = aggregated?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters,
            averageHeartRateBpm = aggregated?.get(HeartRateRecord.BPM_AVG),
            sourcePackage = session.metadata.dataOrigin.packageName,
            heartRateSamples = readHeartRateSamples(client, session),
        )
    }

    private suspend fun readHeartRateSamples(
        client: HealthConnectClient,
        session: ExerciseSessionRecord,
    ): List<HeartRateSample> = runCatching {
        val samples = mutableListOf<HeartRateSample>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime),
                    dataOriginFilter = setOf(session.metadata.dataOrigin),
                    pageToken = pageToken,
                ),
            )
            response.records.forEach { record ->
                record.samples.forEach { samples += HeartRateSample(it.time, it.beatsPerMinute) }
            }
            pageToken = response.pageToken
        } while (!pageToken.isNullOrEmpty())
        samples
    }.getOrDefault(emptyList())

    /**
     * Median rather than latest. Resting HR sits in the denominator of every load score, so a
     * single bad night's reading would skew the whole history.
     */
    suspend fun readRestingHeartRate(from: Instant, to: Instant): Int? {
        val client = clientOrNull() ?: return null
        val values = runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = RestingHeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                ),
            ).records.map { it.beatsPerMinute }
        }.getOrDefault(emptyList())

        if (values.isEmpty()) return null
        return values.sorted()[values.size / 2].toInt()
    }

    companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

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
