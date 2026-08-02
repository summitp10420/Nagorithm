package com.nagorithm.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nagorithm.app.data.Goal
import com.nagorithm.app.data.GoalDimension
import com.nagorithm.app.format.formatClock
import com.nagorithm.app.format.formatDistanceShort
import com.nagorithm.app.health.SportCategory
import com.nagorithm.app.verdict.GoalProgress
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun GoalSection(
    goal: Goal?,
    progress: GoalProgress?,
    onSetGoal: (
        text: String,
        dimension: GoalDimension,
        sport: SportCategory?,
        targetDistanceMeters: Double?,
        targetDateEpochMillis: Long?,
    ) -> Unit,
) {
    var editing by rememberSaveable(goal == null) { mutableStateOf(goal == null) }

    if (editing) {
        GoalForm(
            existing = goal,
            onCancel = { editing = false }.takeIf { goal != null },
            onSave = { text, dimension, sport, distance, date ->
                onSetGoal(text, dimension, sport, distance, date)
                editing = false
            },
        )
    } else if (goal != null) {
        GoalCard(goal = goal, progress = progress, onEdit = { editing = true })
    }
}

@Composable
private fun GoalCard(goal: Goal, progress: GoalProgress?, onEdit: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Goal", style = MaterialTheme.typography.labelMedium)
            Text(goal.text, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${goal.dimension.label} · ${goal.sport?.label ?: "Any aerobic work"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = goal.dimension.question,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            goal.targetDateEpochMillis?.let {
                val days = Duration.between(Instant.now(), Instant.ofEpochMilli(it)).toDays()
                DetailRow(
                    label = "Deadline",
                    value = if (days >= 0) "$days day(s) away" else "${abs(days)} day(s) ago",
                    labelWidth = LABEL_WIDTH,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            if (progress == null) {
                Text(
                    text = "Nothing to measure from yet. Sync a session that counts toward this " +
                        "and the starting number gets captured.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                DetailRow(
                    label = "Started at",
                    value = describe(progress.startValue, progress, goal.sport),
                    labelWidth = LABEL_WIDTH,
                )
                DetailRow(
                    label = "Now",
                    value = describe(progress.currentValue, progress, goal.sport),
                    labelWidth = LABEL_WIDTH,
                )
                DetailRow(
                    label = "Change",
                    value = if (progress.hasMoved) {
                        "%+.1f%%".format(progress.improvementFraction * 100)
                    } else {
                        "flat"
                    },
                    labelWidth = LABEL_WIDTH,
                )
            }

            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Text("Change goal")
            }
        }
    }
}

private fun describe(value: Double, progress: GoalProgress, sport: SportCategory?): String =
    when (progress.dimension) {
        GoalDimension.DISTANCE -> formatDistanceShort(value, sport)
        GoalDimension.PACE ->
            "${formatClock(value)} over ${formatDistanceShort(progress.bucketMeters, sport)}"

        GoalDimension.VOLUME -> "${value.roundToInt()} HRSS/week"
    }

@Composable
private fun GoalForm(
    existing: Goal?,
    onCancel: (() -> Unit)?,
    onSave: (String, GoalDimension, SportCategory?, Double?, Long?) -> Unit,
) {
    var text by remember { mutableStateOf(existing?.text.orEmpty()) }
    var dimension by remember { mutableStateOf(existing?.dimension ?: GoalDimension.VOLUME) }
    var sport by remember { mutableStateOf(existing?.sport) }
    var targetKm by remember {
        mutableStateOf(existing?.targetDistanceMeters?.let { (it / 1000).roundToInt().toString() }.orEmpty())
    }
    var weeks by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (existing == null) "What are you actually after?" else "Change goal",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "There is no target to hit. The goal picks which measurement counts, and " +
                    "you are then measured against your own starting point on that measurement.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("In your own words") },
                placeholder = { Text("Ride a century in the autumn") },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("How should I judge it?", style = MaterialTheme.typography.labelMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                GoalDimension.entries.forEach { option ->
                    FilterChip(
                        selected = dimension == option,
                        onClick = { dimension = option },
                        label = { Text(option.label) },
                    )
                }
            }
            Text(
                text = dimension.question,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Which sport?", style = MaterialTheme.typography.labelMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                FilterChip(
                    selected = sport == null,
                    onClick = { sport = null },
                    label = { Text("Any") },
                )
                SportCategory.entries.forEach { option ->
                    FilterChip(
                        selected = sport == option,
                        onClick = { sport = option },
                        label = { Text(option.label) },
                    )
                }
            }

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = targetKm,
                    onValueChange = { targetKm = it.filter(Char::isDigit).take(4) },
                    label = { Text("Target km") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = weeks,
                    onValueChange = { weeks = it.filter(Char::isDigit).take(3) },
                    label = { Text("In weeks") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "Both optional. They add urgency to the nagging; they are not a target to " +
                    "be scored against.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onSave(
                            text.trim(),
                            dimension,
                            sport,
                            targetKm.toDoubleOrNull()?.times(1000.0),
                            weeks.toLongOrNull()?.let {
                                Instant.now().plus(Duration.ofDays(it * 7)).toEpochMilli()
                            },
                        )
                    },
                    enabled = text.isNotBlank(),
                ) {
                    Text("Set goal")
                }
                onCancel?.let { OutlinedButton(onClick = it) { Text("Cancel") } }
            }
        }
    }
}

private val LABEL_WIDTH = 96.dp
