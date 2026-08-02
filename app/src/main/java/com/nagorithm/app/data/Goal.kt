package com.nagorithm.app.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nagorithm.app.health.SportCategory

/**
 * What the goal makes interesting. There is no numeric target behind any of these — the dimension
 * only decides *which* measurement becomes the yardstick, and the yardstick is always the user's
 * own earlier value of it. A century and a first 5k are the same arithmetic pointed at different
 * columns.
 */
enum class GoalDimension(val label: String, val question: String) {
    /** Longest single effort. "Ride a century", "finish a 5k". */
    DISTANCE("Go further", "Is the longest one getting longer?"),

    /** Speed over a distance already repeated. "Six minute mile", "sub-20 5k". */
    PACE("Get faster", "Is the same distance coming round quicker?"),

    /** Consistency and weekly load. "Trim up", "stop falling apart". */
    VOLUME("Keep showing up", "Is the work still going in?"),
}

/**
 * The value the goal is measured from, captured when the goal is set rather than derived later.
 *
 * Re-deriving it would silently move the finish line every time the user improved, which is the
 * exact failure the whole no-target model exists to avoid. It is stored per goal instead of on the
 * profile because a pace goal and a distance goal start from different numbers on the same day.
 */
data class GoalBaseline(
    @ColumnInfo(name = "startValue") val value: Double,
    @ColumnInfo(name = "startUnit") val unit: String,
    /** For [GoalDimension.PACE]: the distance the starting pace was set over. */
    @ColumnInfo(name = "startBucketMeters") val bucketMeters: Double? = null,
    @ColumnInfo(name = "startCapturedAt") val capturedAtEpochMillis: Long,
)

@Entity(
    tableName = "goals",
    indices = [Index("userId", "archivedAtEpochMillis")],
)
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String = LOCAL_USER_ID,

    /** The user's own words. Shown back to them and handed to the LLM verbatim. */
    val text: String,
    val dimension: GoalDimension,

    /** Null means any aerobic work counts toward it. */
    val sport: SportCategory? = null,

    val targetDistanceMeters: Double? = null,
    val targetDateEpochMillis: Long? = null,

    val createdAtEpochMillis: Long,
    val archivedAtEpochMillis: Long? = null,

    /**
     * Null when the goal was set before any qualifying activity existed. That is a real state, not
     * an error: it means there is nothing to measure from yet and the verdict has to say so.
     */
    @Embedded val start: GoalBaseline? = null,
) {
    val isActive: Boolean get() = archivedAtEpochMillis == null
}
