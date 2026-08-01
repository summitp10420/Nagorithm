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

    /** Returns a row id per activity, or -1 where the record id was already stored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNew(activities: List<StoredActivity>): List<Long>
}
