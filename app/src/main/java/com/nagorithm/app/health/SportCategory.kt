package com.nagorithm.app.health

import androidx.health.connect.client.records.ExerciseSessionRecord

enum class SportCategory(val label: String) {
    CYCLING("Cycling"),
    SWIMMING("Swimming"),
    ROWING("Rowing"),
    RUNNING("Running"),
    WALKING("Walking"),
    OTHER("Other"),
}

/**
 * Excluded by allow-list inversion rather than listing what counts, because the aerobic set is
 * open-ended and a new Health Connect exercise type should default to being credited. Strength
 * work is out of scope: heart rate is a poor proxy for lifting stress, so scoring it would need
 * a second load model with no mathematics in common with this one.
 */
private val NON_AEROBIC_TYPES = setOf(
    ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
    ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING,
    ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING,
    ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
    ExerciseSessionRecord.EXERCISE_TYPE_PILATES,
)

private val INDOOR_TYPES = setOf(
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
    ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL,
    ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE,
)

/** Null means the session is not aerobic work and should not be scored or counted. */
fun sportCategoryOf(exerciseType: Int): SportCategory? {
    if (exerciseType in NON_AEROBIC_TYPES) return null
    return when (exerciseType) {
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
        -> SportCategory.CYCLING

        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
        -> SportCategory.SWIMMING

        ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
        -> SportCategory.ROWING

        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
        -> SportCategory.RUNNING

        ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
        -> SportCategory.WALKING

        else -> SportCategory.OTHER
    }
}

fun isIndoorType(exerciseType: Int): Boolean = exerciseType in INDOOR_TYPES

fun exerciseTypeName(type: Int): String = when (type) {
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "BIKING"
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "BIKING_STATIONARY"
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "RUNNING"
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "RUNNING_TREADMILL"
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "WALKING"
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "HIKING"
    ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "ELLIPTICAL"
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING -> "ROWING"
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "ROWING_MACHINE"
    ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "WEIGHTLIFTING"
    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "STRENGTH_TRAINING"
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "SWIMMING_POOL"
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "SWIMMING_OPEN_WATER"
    ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "YOGA"
    ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT -> "OTHER_WORKOUT"
    else -> "TYPE_$type"
}
