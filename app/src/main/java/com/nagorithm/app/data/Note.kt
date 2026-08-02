package com.nagorithm.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Something the user said about their life, in their own words.
 *
 * Deliberately not a tracker. There is no habit to tick, no streak, no follow-up asking how the
 * sugar is going — the moment the app starts checking in on these it becomes the thing it was
 * built to avoid. You write a line when you feel like it and it becomes context; you write
 * nothing and there is nothing to say.
 *
 * These are evidence, not decoration. Perceived strength, sleep and energy track real changes in
 * fitness and often move before the splits do, so "I feel stronger" three weeks into a build is a
 * signal rather than a mood. What it cannot do is overrule the measurements — the interesting part
 * is whether the two agree, and that is a judgement the prompt asks for explicitly.
 */
@Entity(
    tableName = "notes",
    indices = [Index("userId", "writtenAtEpochMillis")],
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String = LOCAL_USER_ID,
    val writtenAtEpochMillis: Long,
    val text: String,
)
