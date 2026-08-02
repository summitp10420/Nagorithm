package com.nagorithm.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nagorithm.app.verdict.CheckIn
import com.nagorithm.app.verdict.VerdictState

/**
 * The product. Everything else on this screen exists to make this card trustworthy.
 */
@Composable
fun CheckInSection(checkIn: CheckIn?, onAcknowledge: () -> Unit) {
    if (checkIn == null) return

    var showReasons by rememberSaveable { mutableStateOf(false) }
    val colors = colorsFor(checkIn.verdict.state)

    Card(modifier = Modifier.fillMaxWidth(), colors = colors) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = checkIn.nag.headline,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = checkIn.nag.body,
                style = MaterialTheme.typography.bodyLarge,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showReasons = !showReasons }) {
                    Text(if (showReasons) "Hide reasoning" else "Why this nag?")
                }
                if (checkIn.verdict.hasNews) {
                    OutlinedButton(onClick = onAcknowledge) { Text("Got it") }
                }
            }

            if (showReasons) {
                HorizontalDivider()
                Text(
                    text = "Everything the decision used, in the order it was considered. No " +
                        "part of this came from a language model.",
                    style = MaterialTheme.typography.bodySmall,
                )
                checkIn.verdict.reasons.forEach { reason ->
                    Text(text = "• $reason", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * Warnings look like warnings and wins look like wins, but nothing here is red for merely being
 * idle — the app is allowed to be blunt, not alarming.
 */
@Composable
private fun colorsFor(state: VerdictState) = when (state) {
    VerdictState.PROGRESSING -> CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    VerdictState.RAMPING_HARD, VerdictState.FEED_SILENT -> CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    )

    VerdictState.IDLE -> CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    )

    else -> CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = Color.Unspecified,
    )
}
