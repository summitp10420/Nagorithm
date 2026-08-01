package com.nagorithm.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nagorithm.app.data.LoadModel
import com.nagorithm.app.data.StoredActivity

@Composable
fun ActivityListSection(activities: List<StoredActivity>) {
    Text(
        text = if (activities.isEmpty()) {
            "No stored activities yet"
        } else {
            "Stored activities (${activities.size})"
        },
        style = MaterialTheme.typography.titleMedium,
    )

    if (activities.isEmpty()) {
        Text(
            text = "Sync from Health Connect to populate the local database.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    activities.forEach { StoredActivityCard(it) }
}

@Composable
private fun StoredActivityCard(activity: StoredActivity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = activity.title?.takeIf { it.isNotBlank() }
                    ?: buildString {
                        if (activity.isIndoor) append("Indoor ")
                        append(activity.sport.label)
                    },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${activity.sport.label} · ${activity.formattedStart()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DetailRow("Elapsed", formatDuration(activity.elapsedDuration))
            activity.activeDuration?.let { DetailRow("Active", formatDuration(it)) }
            DetailRow("Distance", formatDistance(activity.distanceMeters, activity.sport))
            DetailRow("Avg HR", formatHeartRate(activity.averageHeartRateBpm))
            DetailRow("Peak HR", formatHeartRate(activity.peakHeartRateBpm))

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            val load = activity.load
            DetailRow(
                label = "Load",
                value = load?.let { "%.0f HRSS".format(it.hrss) } ?: "not scored",
            )
            if (load != null) {
                DetailRow(
                    label = "Model",
                    value = when (load.model) {
                        LoadModel.HRSS.name -> "${activity.hrSampleCount} heart rate samples"
                        LoadModel.PARTIAL.name -> "${activity.hrSampleCount} samples, gaps filled " +
                            "at %.0f%% reserve".format((load.assumedReserve ?: 0.0) * 100)
                        else -> "estimated at %.0f%% reserve"
                            .format((load.assumedReserve ?: 0.0) * 100)
                    },
                )
                DetailRow(
                    label = "Scored with",
                    value = "rest ${load.restingHr} · max ${load.maxHr} · thr ${load.thresholdHr}",
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            DetailRow("First seen", activity.formattedFirstSeen())
            DetailRow("Source", activity.sourcePackage)
        }
    }
}
