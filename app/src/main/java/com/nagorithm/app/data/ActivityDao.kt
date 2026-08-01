package com.nagorithm.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {

    @Query(
        "SELECT * FROM activities WHERE userId = :userId ORDER BY startTimeEpochMillis DESC",
    )
    fun observeActivities(userId: String = LOCAL_USER_ID): Flow<List<StoredActivity>>

    @Query(
        "SELECT * FROM activities WHERE userId = :userId ORDER BY startTimeEpochMillis DESC",
    )
    suspend fun allActivities(userId: String = LOCAL_USER_ID): List<StoredActivity>

    /** Returns a row id per activity, or -1 where the record id was already stored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNew(activities: List<StoredActivity>): List<Long>

    @Query("SELECT MAX(peakHeartRateBpm) FROM activities WHERE userId = :userId")
    suspend fun highestPeakHeartRate(userId: String = LOCAL_USER_ID): Long?

    /**
     * Load is recomputed on every sync rather than written once, because it depends on profile
     * heart-rate values that can change. Kept separate from insert so a re-sync does not reset
     * [StoredActivity.firstSeenEpochMillis].
     */
    @Query(
        """
        UPDATE activities SET
            peakHeartRateBpm = :peakHeartRateBpm,
            hrSampleCount = :hrSampleCount,
            loadHrss = :loadHrss,
            loadModel = :loadModel,
            loadRestingHr = :restingHr,
            loadMaxHr = :maxHr,
            loadThresholdHr = :thresholdHr,
            loadAssumedReserve = :assumedReserve
        WHERE recordId = :recordId
        """,
    )
    suspend fun updateLoad(
        recordId: String,
        peakHeartRateBpm: Long?,
        hrSampleCount: Int,
        loadHrss: Double?,
        loadModel: String?,
        restingHr: Int?,
        maxHr: Int?,
        thresholdHr: Int?,
        assumedReserve: Double?,
    )
}
