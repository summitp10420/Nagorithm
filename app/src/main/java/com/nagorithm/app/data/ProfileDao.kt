package com.nagorithm.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nagorithm.app.verdict.NagTone
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profile WHERE userId = :userId")
    fun observeProfile(userId: String = LOCAL_USER_ID): Flow<Profile?>

    @Query("SELECT * FROM profile WHERE userId = :userId")
    suspend fun profile(userId: String = LOCAL_USER_ID): Profile?

    @Upsert
    suspend fun save(profile: Profile)

    @Query(
        """
        UPDATE profile SET detectedRestingHr = :restingHr, detectedMaxHr = :maxHr
        WHERE userId = :userId
        """,
    )
    suspend fun updateDetectedHeartRates(
        restingHr: Int?,
        maxHr: Int?,
        userId: String = LOCAL_USER_ID,
    )

    @Query("UPDATE profile SET lastCheckInEpochMillis = :atEpochMillis WHERE userId = :userId")
    suspend fun markCheckedIn(atEpochMillis: Long, userId: String = LOCAL_USER_ID)

    @Query("UPDATE profile SET tone = :tone WHERE userId = :userId")
    suspend fun setTone(tone: NagTone, userId: String = LOCAL_USER_ID)

    /**
     * Only ever fires once. The baseline is what long-term progress is measured against, so
     * recomputing it would erase the progress it exists to show.
     */
    @Query(
        """
        UPDATE profile SET
            baselineLoad28Day = :load28Day,
            baselineCapabilityName = :capabilityName,
            baselineCapabilityValue = :capabilityValue,
            baselineCapabilityUnit = :capabilityUnit
        WHERE userId = :userId AND baselineLoad28Day IS NULL
        """,
    )
    suspend fun captureBaselineIfAbsent(
        load28Day: Double,
        capabilityName: String,
        capabilityValue: Double?,
        capabilityUnit: String,
        userId: String = LOCAL_USER_ID,
    )

    /**
     * Escape hatch for when the baseline was captured from arithmetic that later turned out to be
     * wrong. Not a routine operation: clearing it discards the reference point that long-term
     * progress is measured against, and it can only be re-taken as of today.
     */
    @Query(
        """
        UPDATE profile SET
            baselineLoad28Day = NULL,
            baselineCapabilityName = NULL,
            baselineCapabilityValue = NULL,
            baselineCapabilityUnit = NULL
        WHERE userId = :userId
        """,
    )
    suspend fun clearBaseline(userId: String = LOCAL_USER_ID)
}
