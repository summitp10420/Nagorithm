package com.nagorithm.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Sex { MALE, FEMALE }

/**
 * Single row in v1. Holds the biological facts HRSS needs, plus the once-only baseline the
 * whole progress model measures against.
 */
@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val userId: String = LOCAL_USER_ID,
    val age: Int,
    val sex: Sex,

    /** Overrides win over anything detected from Health Connect. */
    val restingHrOverride: Int? = null,
    val maxHrOverride: Int? = null,
    val thresholdHrOverride: Int? = null,

    /** Detected from Health Connect on sync; overrides take precedence when set. */
    val detectedRestingHr: Int? = null,
    val detectedMaxHr: Int? = null,

    val onboardedAtEpochMillis: Long,

    /**
     * When the user last saw a verdict. Sessions first seen after this are news, which is what
     * lets a late-syncing ride still count as something to talk about.
     */
    val lastCheckInEpochMillis: Long? = null,

    /** Written once at onboarding. Everything long-term is measured against these. */
    val baselineLoad28Day: Double? = null,
    val baselineCapabilityName: String? = null,
    val baselineCapabilityValue: Double? = null,
    val baselineCapabilityUnit: String? = null,
) {
    /** Tanaka is a better estimator than 220 − age, and is the usual modern default. */
    val estimatedMaxHr: Int get() = (208 - 0.7 * age).toInt()

    /**
     * An observed peak is a *lower bound* on max heart rate, never an upper one — you only see a
     * number by actually reaching it, and an easy session proves nothing about the ceiling. Taking
     * the observed peak at face value after one endurance ride drags threshold down with it and
     * inflates every score, so the age estimate acts as a floor.
     */
    val maxHr: Int get() = maxHrOverride ?: maxOf(detectedMaxHr ?: 0, estimatedMaxHr)

    /** Rough population default when Health Connect has nothing. */
    val restingHr: Int get() = restingHrOverride ?: detectedRestingHr ?: DEFAULT_RESTING_HR

    /** Lactate threshold sits near 85% of max for most riders absent a real test. */
    val thresholdHr: Int get() = thresholdHrOverride ?: (maxHr * 0.85).toInt()

    val maxHrSource: String
        get() = when {
            maxHrOverride != null -> "set by you"
            (detectedMaxHr ?: 0) >= estimatedMaxHr -> "highest observed"
            else -> "age estimate — observed peak is only $detectedMaxHr"
        }

    val isMaxHrEstimated: Boolean get() = maxHrOverride == null
    val isRestingHrEstimated: Boolean get() = restingHrOverride == null && detectedRestingHr == null
    val isThresholdHrEstimated: Boolean get() = thresholdHrOverride == null

    companion object {
        const val DEFAULT_RESTING_HR = 60
    }
}
