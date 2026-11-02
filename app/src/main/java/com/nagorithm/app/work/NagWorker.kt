package com.nagorithm.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nagorithm.app.data.ActivityRepository
import com.nagorithm.app.data.NagorithmDatabase
import com.nagorithm.app.effort.EffortSnapshot
import com.nagorithm.app.health.HealthConnectAvailability
import com.nagorithm.app.health.HealthConnectManager
import com.nagorithm.app.health.ImportWatermark
import com.nagorithm.app.health.importWindowStart
import com.nagorithm.app.llm.ApiKeyStore
import com.nagorithm.app.llm.GeminiClient
import com.nagorithm.app.llm.NagWriter
import com.nagorithm.app.llm.RecentPicture
import com.nagorithm.app.verdict.NagDecision
import com.nagorithm.app.verdict.NagSchedule
import com.nagorithm.app.verdict.VerdictEngine
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * The thing that makes this a nag engine rather than a dashboard.
 *
 * Runs on its own, reads Health Connect, forms the same verdict the app would, and decides
 * whether it is worth interrupting the user over. All the judgment lives in [VerdictEngine] and
 * [NagSchedule]; this only wires them to the clock and the notification shade.
 */
class NagWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val healthConnect = HealthConnectManager(applicationContext)
        if (healthConnect.availability() != HealthConnectAvailability.AVAILABLE) {
            return Result.success()
        }

        val database = NagorithmDatabase.get(applicationContext)
        val repository = ActivityRepository(
            healthConnect = healthConnect,
            activityDao = database.activityDao(),
            profileDao = database.profileDao(),
            goalDao = database.goalDao(),
            spokenNagDao = database.spokenNagDao(),
            noteDao = database.noteDao(),
        )

        // Without a profile there is no max heart rate to score against, so there is no verdict
        // to deliver. Nagging someone through onboarding is not this worker's job.
        val profile = repository.currentProfile() ?: return Result.success()

        val now = Instant.now()
        val watermark = ImportWatermark(applicationContext)
        val from = importWindowStart(
            importedFrom = watermark.importedFrom,
            now = now,
            canReadHistory = healthConnect.canReadHistory(),
        )
        val sync = runCatching { repository.syncFromHealthConnect(from, now) }
            .onSuccess { watermark.record(from) }

        // A failed read is usually a revoked permission or a Health Connect update in progress.
        // Retrying is right; inventing a verdict from stale data and calling someone lazy is not.
        val feedAlive = sync.getOrElse { return Result.retry() }.feedAlive

        val activities = repository.allActivities()
        val verdict = VerdictEngine.evaluate(
            activities = activities,
            snapshot = EffortSnapshot.from(activities, now),
            goal = repository.activeGoal(),
            lastCheckInEpochMillis = profile.lastCheckInEpochMillis,
            feedAlive = feedAlive,
            now = now,
        )

        // Cadence governs *unprompted* nags. When the user asks for one directly, the rules about
        // not being a pest do not apply — they are the one who pressed the button.
        val decision = if (inputData.getBoolean(KEY_ON_DEMAND, false)) {
            NagDecision(true, "You asked for it.")
        } else {
            NagSchedule.decide(verdict, repository.latestSpokenNag(), now)
        }
        if (!decision.speak) return Result.success()

        val key = ApiKeyStore(applicationContext).geminiKey
        val recentNotes =
            repository.recentNotes(now.minus(RecentPicture.NOTE_WINDOW_DAYS, ChronoUnit.DAYS))
        val written = NagWriter(key?.let(::GeminiClient)).write(
            verdict = verdict,
            tone = profile.tone,
            picture = RecentPicture.build(activities, now),
            notes = RecentPicture.notes(recentNotes, now),
            noteTexts = RecentPicture.noteTexts(recentNotes),
        )
        val notified = NagNotifier.post(applicationContext, written.nag)

        repository.recordSpokenNag(
            verdict = verdict,
            nag = written.nag,
            notified = notified,
            // Why it spoke now belongs in the audit trail as much as what it decided, since a nag
            // arriving at the wrong moment is its own kind of wrong. So does how it got worded,
            // because a template appearing where a model answer was expected is a symptom.
            scheduleReason = "${decision.reason} ${written.note}",
            at = now,
        )

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "nagorithm-nag"
        private const val ON_DEMAND_WORK_NAME = "nagorithm-nag-now"
        private const val KEY_ON_DEMAND = "onDemand"

        /**
         * Four times a day against a schedule that mostly stays quiet. Doze will defer these
         * anyway, so a tighter period would buy nothing but battery; the cadence the user
         * experiences is [NagSchedule]'s, not this one.
         */
        private const val PERIOD_HOURS = 6L

        fun schedule(context: Context) {
            NagNotifier.ensureChannel(context)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                // KEEP, so opening the app does not reset the timer and postpone the next nag.
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<NagWorker>(PERIOD_HOURS, TimeUnit.HOURS).build(),
            )
        }

        /**
         * Runs the whole unattended path immediately — sync, verdict, notification — so the thing
         * that normally happens invisibly every few hours can be watched happening.
         */
        fun runNow(context: Context) {
            NagNotifier.ensureChannel(context)
            WorkManager.getInstance(context).enqueueUniqueWork(
                ON_DEMAND_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<NagWorker>()
                    .setInputData(workDataOf(KEY_ON_DEMAND to true))
                    .build(),
            )
        }
    }
}
