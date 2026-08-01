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
import com.nagorithm.app.data.Profile
import com.nagorithm.app.effort.EffortSnapshot
import com.nagorithm.app.effort.LoadWindow

@Composable
fun EffortSection(snapshot: EffortSnapshot?, profile: Profile?) {
    if (snapshot == null) return

    Text("Effort", style = MaterialTheme.typography.titleMedium)

    if (!snapshot.hasAnyActivity) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Nothing stored yet, so there is nothing to score.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Verdict inputs", style = MaterialTheme.typography.labelMedium)

            DetailRow(
                label = "Last session",
                value = snapshot.daysSinceLastActivity
                    ?.let { if (it == 0L) "today" else "$it day(s) ago" }
                    ?: "—",
                labelWidth = LABEL_WIDTH,
            )
            DetailRow(
                label = "Longest gap",
                value = snapshot.longestGapDays?.let { "$it day(s)" } ?: "—",
                labelWidth = LABEL_WIDTH,
            )
            DetailRow(
                label = "ACWR",
                value = snapshot.acwr?.let { "%.2f — %s".format(it, acwrReading(it)) }
                    ?: "not enough history",
                labelWidth = LABEL_WIDTH,
            )

            profile?.baselineLoad28Day?.let { baseline ->
                DetailRow(
                    label = "vs. baseline",
                    value = if (baseline > 0.0) {
                        "%.0f%% of onboarding 28-day load"
                            .format(snapshot.chronic.totalLoad / baseline * 100.0)
                    } else {
                        "baseline was zero"
                    },
                    labelWidth = LABEL_WIDTH,
                )
            }

            if (snapshot.longestEverBySport.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Longest ever", style = MaterialTheme.typography.labelMedium)
                snapshot.longestEverBySport.entries
                    .sortedByDescending { it.value }
                    .forEach { (sport, meters) ->
                        DetailRow(
                            label = sport.label,
                            value = formatDistance(meters, sport),
                            labelWidth = LABEL_WIDTH,
                        )
                    }
            }
        }
    }

    WindowCard("Last 7 days (acute)", snapshot.acute)
    WindowCard("Last 28 days (chronic)", snapshot.chronic)
}

@Composable
private fun WindowCard(title: String, window: LoadWindow) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text("%.0f HRSS".format(window.totalLoad), style = MaterialTheme.typography.titleMedium)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            DetailRow("Per week", "%.0f".format(window.loadPerWeek), LABEL_WIDTH)
            DetailRow("Sessions", window.activityCount.toString(), LABEL_WIDTH)
            DetailRow("Active days", "${window.activeDays} of ${window.days}", LABEL_WIDTH)
            DetailRow("Moving time", formatDuration(window.movingTime), LABEL_WIDTH)

            if (window.loadBySport.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Load by sport", style = MaterialTheme.typography.labelMedium)
                window.loadBySport.entries
                    .sortedByDescending { it.value }
                    .forEach { (sport, load) ->
                        DetailRow(
                            label = sport.label,
                            value = "%.0f  (%.0f%%)".format(
                                load,
                                window.loadShare(sport) * 100.0,
                            ),
                            labelWidth = LABEL_WIDTH,
                        )
                    }
            }

            if (window.estimatedCount > 0) {
                Text(
                    text = "${window.estimatedCount} session(s) had no usable heart rate and " +
                        "were estimated from duration at an assumed intensity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun acwrReading(acwr: Double): String = when {
    acwr < 0.8 -> "fading"
    acwr <= 1.3 -> "holding steady"
    acwr <= 1.5 -> "ramping"
    else -> "ramping hard"
}

private val LABEL_WIDTH = 132.dp
