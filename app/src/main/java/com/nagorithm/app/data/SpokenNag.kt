package com.nagorithm.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nagorithm.app.verdict.NagSource
import com.nagorithm.app.verdict.VerdictState

/**
 * A nag the app actually delivered, kept because a nag engine has to remember its own voice.
 *
 * Three things need this and none of them can be reconstructed from activity data. Escalation —
 * "day four", then "day six", then "right, this is a rebuild" — requires knowing that day four was
 * already said. Not repeating yourself requires the same. And the reasoning behind a message has
 * to survive the process that produced it, or "Why this nag?" can only explain a nag the app
 * happens to be recomputing right now.
 */
@Entity(
    tableName = "spoken_nags",
    indices = [Index("userId", "spokenAtEpochMillis")],
)
data class SpokenNag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String = LOCAL_USER_ID,

    val spokenAtEpochMillis: Long,
    val state: VerdictState,
    val headline: String,
    val body: String,
    val source: NagSource,

    /** The audit trail, newline separated. Stored as text because nothing queries inside it. */
    val reasons: String,

    /** False when the verdict was rendered in-app without a notification going out. */
    val notified: Boolean,
) {
    val reasonLines: List<String> get() = reasons.lines().filter { it.isNotBlank() }
}
