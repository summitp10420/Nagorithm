package com.nagorithm.app.data

import androidx.room.ColumnInfo

enum class LoadModel {
    /** Summed per-sample TRIMP, normalized so an hour at threshold scores 100. */
    HRSS,

    /** Samples covered only part of the session; the remainder was filled at assumed intensity. */
    PARTIAL,

    /** No usable heart rate. Duration run through TRIMP at an assumed intensity for the sport. */
    ESTIMATED,
}

/**
 * The scoring decision and every input behind it, kept together so the transparency view can show
 * why a session scored what it did without recomputing anything.
 */
data class LoadScore(
    @ColumnInfo(name = "loadHrss") val hrss: Double,
    @ColumnInfo(name = "loadModel") val model: String,
    @ColumnInfo(name = "loadRestingHr") val restingHr: Int,
    @ColumnInfo(name = "loadMaxHr") val maxHr: Int,
    @ColumnInfo(name = "loadThresholdHr") val thresholdHr: Int,
    /** Only set for [LoadModel.ESTIMATED]: the heart-rate reserve fraction that was assumed. */
    @ColumnInfo(name = "loadAssumedReserve") val assumedReserve: Double? = null,
) {
    val isEstimated: Boolean get() = model == LoadModel.ESTIMATED.name

    val isFullyMeasured: Boolean get() = model == LoadModel.HRSS.name
}
