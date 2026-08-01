package com.nagorithm.app.data

import com.nagorithm.app.health.ExerciseScanDiagnostics
import com.nagorithm.app.health.HealthConnectManager
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class SyncResult(
    val cyclingFound: Int,
    val newlyStored: Int,
    val alreadyStored: Int,
    val diagnostics: ExerciseScanDiagnostics,
)

class ActivityRepository(
    private val healthConnect: HealthConnectManager,
    private val activityDao: ActivityDao,
) {

    fun observeActivities(): Flow<List<StoredActivity>> = activityDao.observeActivities()

    suspend fun syncFromHealthConnect(from: Instant, to: Instant): SyncResult {
        val fetch = healthConnect.readCyclingSessions(from, to)
        val seenAt = Instant.now()

        val insertedRowIds = activityDao.insertNew(
            fetch.rides.map { it.toStoredActivity(firstSeenAt = seenAt) },
        )
        val newlyStored = insertedRowIds.count { it != -1L }

        return SyncResult(
            cyclingFound = fetch.rides.size,
            newlyStored = newlyStored,
            alreadyStored = fetch.rides.size - newlyStored,
            diagnostics = fetch.diagnostics,
        )
    }
}
