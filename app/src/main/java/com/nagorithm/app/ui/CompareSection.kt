package com.nagorithm.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nagorithm.app.ComparisonUiState
import com.nagorithm.app.SignalCandidate

/**
 * A bake-off, not a feature.
 *
 * ACWR and form are computed from the same loads and mostly agree, so the question is not which is
 * more correct in the abstract but which produces the better message on the weeks they diverge.
 * That is a judgement about output, which is why this shows two nags rather than two numbers.
 */
@Composable
fun CompareSection(
    state: ComparisonUiState,
    onCompare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Compare signals", style = MaterialTheme.typography.titleMedium)

            Text(
                "Runs the same check-in twice — once letting ACWR decide whether you have " +
                    "overcooked it, once letting form decide. Nothing is saved or sent as a " +
                    "notification.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = onCompare,
                enabled = state !is ComparisonUiState.Running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state is ComparisonUiState.Running) "Writing both…" else "Run both")
            }

            when (state) {
                is ComparisonUiState.Idle, is ComparisonUiState.Running -> Unit

                is ComparisonUiState.Failed -> Text(
                    "Could not run it: ${state.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                is ComparisonUiState.Ready -> {
                    HorizontalDivider()

                    Text(
                        text = if (state.disagree) {
                            "They disagree — ${state.acwr.state.name} against " +
                                "${state.form.state.name}. This is the case worth judging."
                        } else {
                            "Both reached ${state.acwr.state.name}, so today the choice makes no " +
                                "difference. Any wording difference is the model, not the signal."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    CandidateCard(state.acwr, state.disagree)
                    CandidateCard(state.form, state.disagree)

                    state.caveats.forEach {
                        Text(
                            "• $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(candidate: SignalCandidate, highlight: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (highlight) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "${candidate.signal.label} → ${candidate.state.name}",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(candidate.nag.headline, style = MaterialTheme.typography.titleSmall)
            Text(candidate.nag.body, style = MaterialTheme.typography.bodyMedium)

            HorizontalDivider()

            Text(
                candidate.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                candidate.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
