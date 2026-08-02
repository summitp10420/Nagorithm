package com.nagorithm.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query(
        "SELECT * FROM notes WHERE userId = :userId ORDER BY writtenAtEpochMillis DESC LIMIT :limit",
    )
    fun observeRecent(limit: Int = 30, userId: String = LOCAL_USER_ID): Flow<List<Note>>

    /**
     * A far longer window than sessions get. A shoulder that has been the limiting factor for
     * months is still the reason this week looks the way it does, whereas a ride from March
     * explains nothing. Age travels with each note so the model can discount the stale ones.
     */
    @Query(
        """
        SELECT * FROM notes
        WHERE userId = :userId AND writtenAtEpochMillis >= :sinceEpochMillis
        ORDER BY writtenAtEpochMillis DESC
        """,
    )
    suspend fun since(sinceEpochMillis: Long, userId: String = LOCAL_USER_ID): List<Note>

    @Insert
    suspend fun insert(note: Note): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        """
        DELETE FROM notes WHERE userId = :userId AND id NOT IN (
            SELECT id FROM notes WHERE userId = :userId
            ORDER BY writtenAtEpochMillis DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimTo(keep: Int = 200, userId: String = LOCAL_USER_ID)
}
