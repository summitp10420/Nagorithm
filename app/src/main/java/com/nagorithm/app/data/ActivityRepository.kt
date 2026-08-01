package com.nagorithm.app.data

import com.nagorithm.app.effort.EffortSnapshot
import com.nagorithm.app.effort.HrssCalculator
import com.nagorithm.app.effort.peakHeartRate
import com.nagorithm.app.health.ExerciseScanDiagnostics
import com.nagorithm.app.health.HealthConnectManager
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.temporal.ChronoUnit

data class SyncResult(
    val aerobicFound: Int,
    val newlyStored: Int,
    val alreadyStored: Int,
    val scoredFromHeartRate: Int,
    val scoredFromEstimate: Int,
    val detectedRestingHr: Int?,
    val detectedMaxHr: Int?,
    val diagnostics: ExerciseScanDiagnostics,
)

class ActivityRepository(
    private val healthConnect: HealthConnectManager,
    private val activityDao: ActivityDao,
    private val profileDao: ProfileDao,
) {

    fun observeActivities(): Flow<List<StoredActivity>> = activityDao.observeActivities()

    fun observeProfile(): Flow<Profile?> = profileDao.observeProfile()

    suspend fun currentProfile(): Profile? = profileDao.profile()

    suspend fun saveProfile(profile: Profile) = profileDao.save(profile)

    suspend fun syncFromHealthConnect(from: Instant, to: Instant): SyncResult {
        val fetch = healthConnect.readAerobicSessions(from, to)
        val seenAt = Instant.now()
        val peaks = fetch.activities.associate { it.recordId to it.heartRateSamples.peakHeartRate() }

        val insertedRowIds = activityDao.insertNew(
            fetch.activities.map {
                it.toStoredActivity(firstSeenAt = seenAt, peakHeartRateBpm = peaks[it.recordId])
            },
        )
        val newlyStored = insertedRowIds.count { it != -1L }

        val restingHr = healthConnect.readRestingHeartRate(
            from = to.minus(RESTING_HR_WINDOW_DAYS, ChronoUnit.DAYS),
            to = to,
        )
        val maxHr = activityDao.highestPeakHeartRate()?.toInt()
        profileDao.updateDetectedHeartRates(restingHr = restingHr, maxHr = maxHr)

        // Loads are recomputed for every fetched session, not just new ones, so a corrected
        // resting or max heart rate propagates through the whole readable window on next sync.
        val profile = profileDao.profile()
        var measured = 0
        var estimated = 0
        if (profile != null) {
            fetch.activities.forEach { activity ->
                val load = HrssCalculator.compute(
                    samples = activity.heartRateSamples,
                    duration = activity.activeDuration ?: activity.elapsedDuration,
                    sport = activity.sport,
                    profile = profile,
                )
                when {
                    load == null -> Unit
                    load.isEstimated -> estimated++
                    else -> measured++
                }
                activityDao.updateLoad(
                    recordId = activity.recordId,
                    peakHeartRateBpm = peaks[activity.recordId],
                    hrSampleCount = activity.heartRateSamples.size,
                    loadHrss = load?.hrss,
                    loadModel = load?.model,
                    restingHr = load?.restingHr,
                    maxHr = load?.maxHr,
                    thresholdHr = load?.thresholdHr,
                    assumedReserve = load?.assumedReserve,
                )
            }
            captureBaselineIfAbsent()
        }

        return SyncResult(
            aerobicFound = fetch.activities.size,
            newlyStored = newlyStored,
            alreadyStored = fetch.activities.size - newlyStored,
            scoredFromHeartRate = measured,
            scoredFromEstimate = estimated,
            detectedRestingHr = restingHr,
            detectedMaxHr = maxHr,
            diagnostics = fetch.diagnostics,
        )
    }

    private suspend fun captureBaselineIfAbsent() {
        val snapshot = EffortSnapshot.from(activityDao.allActivities())
        if (!snapshot.hasAnyActivity) return
        profileDao.captureBaselineIfAbsent(
            load28Day = snapshot.chronic.totalLoad,
            capabilityName = "Longest session",
            capabilityValue = snapshot.longestEverBySport.values.maxOrNull(),
            capabilityUnit = "m",
        )
    }

    private companion object {
        const val RESTING_HR_WINDOW_DAYS = 30L
    }
}
