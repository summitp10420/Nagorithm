package com.nagorithm.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {

    /**
     * Newest active goal wins. v1 sets one at a time, but archiving rather than overwriting keeps
     * the old baseline intact — the record of where someone started is the one number in this app
     * that cannot be reconstructed from Health Connect.
     */
    @Query(
        """
        SELECT * FROM goals
        WHERE userId = :userId AND archivedAtEpochMillis IS NULL
        ORDER BY createdAtEpochMillis DESC LIMIT 1
        """,
    )
    fun observeActiveGoal(userId: String = LOCAL_USER_ID): Flow<Goal?>

    @Query(
        """
        SELECT * FROM goals
        WHERE userId = :userId AND archivedAtEpochMillis IS NULL
        ORDER BY createdAtEpochMillis DESC LIMIT 1
        """,
    )
    suspend fun activeGoal(userId: String = LOCAL_USER_ID): Goal?

    @Insert
    suspend fun insert(goal: Goal): Long

    @Query(
        """
        UPDATE goals SET archivedAtEpochMillis = :atEpochMillis
        WHERE userId = :userId AND archivedAtEpochMillis IS NULL
        """,
    )
    suspend fun archiveActive(atEpochMillis: Long, userId: String = LOCAL_USER_ID)

    /**
     * Backfills the starting value for a goal that was set before any qualifying activity had been
     * synced. Guarded so it can never overwrite a baseline that already exists.
     */
    @Query(
        """
        UPDATE goals SET
            startValue = :value,
            startUnit = :unit,
            startBucketMeters = :bucketMeters,
            startCapturedAt = :capturedAtEpochMillis
        WHERE id = :goalId AND startValue IS NULL
        """,
    )
    suspend fun captureBaselineIfAbsent(
        goalId: Long,
        value: Double,
        unit: String,
        bucketMeters: Double?,
        capturedAtEpochMillis: Long,
    )
}
