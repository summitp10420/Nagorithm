package com.nagorithm.app.data

import com.nagorithm.app.effort.EffortSnapshot
import com.nagorithm.app.effort.HrssCalculator
import com.nagorithm.app.effort.peakHeartRate
import com.nagorithm.app.health.ExerciseScanDiagnostics
import com.nagorithm.app.health.HealthConnectManager
import com.nagorithm.app.health.SportCategory
import com.nagorithm.app.verdict.Nag
import com.nagorithm.app.verdict.NagTone
import com.nagorithm.app.verdict.Verdict
import com.nagorithm.app.verdict.VerdictEngine
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
    /** False means Health Connect had no passive data either, so silence is not the user's fault. */
    val feedAlive: Boolean,
    val diagnostics: ExerciseScanDiagnostics,
)

class ActivityRepository(
    private val healthConnect: HealthConnectManager,
    private val activityDao: ActivityDao,
    private val profileDao: ProfileDao,
    private val goalDao: GoalDao,
    private val spokenNagDao: SpokenNagDao,
    private val noteDao: NoteDao,
) {

    fun observeActivities(): Flow<List<StoredActivity>> = activityDao.observeActivities()

    fun observeProfile(): Flow<Profile?> = profileDao.observeProfile()

    fun observeActiveGoal(): Flow<Goal?> = goalDao.observeActiveGoal()

    fun observeSpokenNags(): Flow<List<SpokenNag>> = spokenNagDao.observeRecent()

    suspend fun currentProfile(): Profile? = profileDao.profile()

    suspend fun allActivities(): List<StoredActivity> = activityDao.allActivities()

    suspend fun activeGoal(): Goal? = goalDao.activeGoal()

    suspend fun latestSpokenNag(): SpokenNag? = spokenNagDao.latest()

    fun observeLatestSpokenNag(): Flow<SpokenNag?> = spokenNagDao.observeLatest()

    fun observeNotes(): Flow<List<Note>> = noteDao.observeRecent()

    suspend fun recentNotes(since: Instant): List<Note> = noteDao.since(since.toEpochMilli())

    suspend fun addNote(text: String, at: Instant = Instant.now()) {
        if (text.isBlank()) return
        noteDao.insert(Note(writtenAtEpochMillis = at.toEpochMilli(), text = text.trim()))
        noteDao.trimTo()
    }

    suspend fun deleteNote(id: Long) = noteDao.delete(id)

    /**
     * Records what was said, so the next verdict knows it has already said it. The schedule's own
     * reasoning is folded into the audit trail because "why now" is as much a part of a nag being
     * right as "why this".
     */
    suspend fun recordSpokenNag(
        verdict: Verdict,
        nag: Nag,
        notified: Boolean,
        scheduleReason: String,
        at: Instant = Instant.now(),
    ) {
        spokenNagDao.insert(
            SpokenNag(
                spokenAtEpochMillis = at.toEpochMilli(),
                state = verdict.state,
                headline = nag.headline,
                body = nag.body,
                source = nag.source,
                reasons = (verdict.reasons + "Spoke now because: $scheduleReason")
                    .joinToString("\n"),
                notified = notified,
            ),
        )
        spokenNagDao.trimTo()
    }

    suspend fun saveProfile(profile: Profile) = profileDao.save(profile)

    suspend fun clearBaseline() = profileDao.clearBaseline()

    suspend fun markCheckedIn(at: Instant = Instant.now()) =
        profileDao.markCheckedIn(at.toEpochMilli())

    suspend fun setTone(tone: NagTone) = profileDao.setTone(tone)

    /**
     * Replaces the active goal rather than editing it. A goal carries the value it was measured
     * from, so changing its dimension in place would leave a starting number that answers a
     * question nobody asked any more.
     */
    suspend fun setGoal(
        text: String,
        dimension: GoalDimension,
        sport: SportCategory?,
        targetDistanceMeters: Double?,
        targetDateEpochMillis: Long?,
    ) {
        val now = Instant.now()
        goalDao.archiveActive(now.toEpochMilli())

        val activities = activityDao.allActivities()
        val start = VerdictEngine.measure(
            dimension = dimension,
            sport = sport,
            activities = activities,
            snapshot = EffortSnapshot.from(activities, now),
        )

        goalDao.insert(
            Goal(
                text = text,
                dimension = dimension,
                sport = sport,
                targetDistanceMeters = targetDistanceMeters,
                targetDateEpochMillis = targetDateEpochMillis,
                createdAtEpochMillis = now.toEpochMilli(),
                start = start?.let {
                    GoalBaseline(
                        value = it.value,
                        unit = it.unit,
                        bucketMeters = it.bucketMeters,
                        capturedAtEpochMillis = now.toEpochMilli(),
                    )
                },
            ),
        )
    }

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
                    load.isFullyMeasured -> measured++
                    else -> estimated++
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
            feedAlive = fetch.activities.isNotEmpty() ||
                healthConnect.hasPassiveData(to.minus(FEED_PROBE_DAYS, ChronoUnit.DAYS), to),
            diagnostics = fetch.diagnostics,
        )
    }

    private suspend fun captureBaselineIfAbsent() {
        val activities = activityDao.allActivities()
        val snapshot = EffortSnapshot.from(activities)
        if (!snapshot.hasAnyActivity) return

        profileDao.captureBaselineIfAbsent(
            load28Day = snapshot.chronic.totalLoad,
            capabilityName = "Longest session",
            capabilityValue = snapshot.longestEverBySport.values.maxOrNull(),
            capabilityUnit = "m",
        )

        // A goal set before the first sync has nothing to measure from. This is the first moment
        // it can be given a starting value, and the DAO guard keeps it from ever being retaken.
        val goal = goalDao.activeGoal() ?: return
        if (goal.start != null) return

        VerdictEngine.measure(
            dimension = goal.dimension,
            sport = goal.sport,
            activities = activities,
            snapshot = snapshot,
        )?.let {
            goalDao.captureBaselineIfAbsent(
                goalId = goal.id,
                value = it.value,
                unit = it.unit,
                bucketMeters = it.bucketMeters,
                capturedAtEpochMillis = Instant.now().toEpochMilli(),
            )
        }
    }

    private companion object {
        const val RESTING_HR_WINDOW_DAYS = 30L

        /** Short enough that a feed which died this week is caught before the next nag. */
        const val FEED_PROBE_DAYS = 3L
    }
}
