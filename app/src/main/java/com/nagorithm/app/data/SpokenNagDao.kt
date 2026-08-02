package com.nagorithm.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nagorithm.app.verdict.VerdictState
import kotlinx.coroutines.flow.Flow

@Dao
interface SpokenNagDao {

    @Query(
        "SELECT * FROM spoken_nags WHERE userId = :userId ORDER BY spokenAtEpochMillis DESC LIMIT 1",
    )
    suspend fun latest(userId: String = LOCAL_USER_ID): SpokenNag?

    @Query(
        "SELECT * FROM spoken_nags WHERE userId = :userId ORDER BY spokenAtEpochMillis DESC LIMIT :limit",
    )
    fun observeRecent(limit: Int = 20, userId: String = LOCAL_USER_ID): Flow<List<SpokenNag>>

    @Query(
        "SELECT * FROM spoken_nags WHERE userId = :userId ORDER BY spokenAtEpochMillis DESC LIMIT 1",
    )
    fun observeLatest(userId: String = LOCAL_USER_ID): Flow<SpokenNag?>

    /**
     * How many times in a row the same verdict has been delivered. Drives escalation: the third
     * "you have done nothing" in a row should not sound like the first.
     */
    @Query(
        """
        SELECT COUNT(*) FROM spoken_nags
        WHERE userId = :userId AND spokenAtEpochMillis > (
            SELECT COALESCE(MAX(spokenAtEpochMillis), 0) FROM spoken_nags
            WHERE userId = :userId AND state != :state
        )
        """,
    )
    suspend fun streak(state: VerdictState, userId: String = LOCAL_USER_ID): Int

    @Insert
    suspend fun insert(nag: SpokenNag): Long

    /** History is small and only the recent tail is ever read, but it should not grow forever. */
    @Query(
        """
        DELETE FROM spoken_nags WHERE userId = :userId AND id NOT IN (
            SELECT id FROM spoken_nags WHERE userId = :userId
            ORDER BY spokenAtEpochMillis DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimTo(keep: Int = 200, userId: String = LOCAL_USER_ID)
}
