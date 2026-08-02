package com.nagorithm.app.ui

import com.nagorithm.app.data.StoredActivity
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm a", Locale.getDefault())

private val shortDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

fun StoredActivity.formattedStart(): String {
    val zone: ZoneId = zoneOffsetSeconds
        ?.let { ZoneOffset.ofTotalSeconds(it) }
        ?: ZoneId.systemDefault()
    return dateFormatter.format(Instant.ofEpochMilli(startTimeEpochMillis).atZone(zone))
}

fun StoredActivity.formattedShortDate(): String =
    shortDateFormatter.format(
        Instant.ofEpochMilli(startTimeEpochMillis).atZone(ZoneId.systemDefault()),
    )

fun StoredActivity.formattedFirstSeen(): String =
    dateFormatter.format(
        Instant.ofEpochMilli(firstSeenEpochMillis).atZone(ZoneId.systemDefault()),
    )
