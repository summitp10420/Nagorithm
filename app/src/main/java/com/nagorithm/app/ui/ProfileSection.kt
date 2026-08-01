package com.nagorithm.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.nagorithm.app.ProfileUiState
import com.nagorithm.app.data.Profile
import com.nagorithm.app.data.Sex

@Composable
fun ProfileSection(
    state: ProfileUiState,
    onSave: (age: Int, sex: Sex, restingHr: Int?, maxHr: Int?, thresholdHr: Int?) -> Unit,
) {
    if (state !is ProfileUiState.Loaded) return
    val profile = state.profile

    var editing by rememberSaveable(profile == null) { mutableStateOf(profile == null) }

    if (editing) {
        ProfileForm(
            profile = profile,
            onCancel = { editing = false }.takeIf { profile != null },
            onSave = { age, sex, resting, max, threshold ->
                onSave(age, sex, resting, max, threshold)
                editing = false
            },
        )
    } else if (profile != null) {
        ProfileCard(profile = profile, onEdit = { editing = true })
    }
}

@Composable
private fun ProfileCard(profile: Profile, onEdit: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Profile", style = MaterialTheme.typography.labelMedium)
            Text(
                text = "${profile.age} · ${profile.sex.name.lowercase()}",
                style = MaterialTheme.typography.titleMedium,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("Heart rate inputs to every load score", style = MaterialTheme.typography.labelMedium)
            DetailRow(
                label = "Resting",
                value = if (profile.isRestingHrEstimated) {
                    "${profile.restingHr} bpm (default — none in Health Connect)"
                } else {
                    "${profile.restingHr} bpm"
                },
            )
            DetailRow("Max", "${profile.maxHr} bpm (${profile.maxHrSource})")
            DetailRow("Threshold", hrLabel(profile.thresholdHr, profile.isThresholdHrEstimated))

            if (profile.baselineLoad28Day != null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Baseline at onboarding", style = MaterialTheme.typography.labelMedium)
                DetailRow("28-day load", "%.0f".format(profile.baselineLoad28Day))
                DetailRow(
                    label = "Longest",
                    value = profile.baselineCapabilityValue
                        ?.let { "%.2f km".format(it / 1000.0) }
                        ?: "—",
                )
            }

            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Text("Edit profile")
            }
        }
    }
}

@Composable
private fun ProfileForm(
    profile: Profile?,
    onCancel: (() -> Unit)?,
    onSave: (age: Int, sex: Sex, restingHr: Int?, maxHr: Int?, thresholdHr: Int?) -> Unit,
) {
    var age by remember { mutableStateOf(profile?.age?.toString().orEmpty()) }
    var sex by remember { mutableStateOf(profile?.sex ?: Sex.MALE) }
    var resting by remember { mutableStateOf(profile?.restingHrOverride?.toString().orEmpty()) }
    var max by remember { mutableStateOf(profile?.maxHrOverride?.toString().orEmpty()) }
    var threshold by remember { mutableStateOf(profile?.thresholdHrOverride?.toString().orEmpty()) }

    val parsedAge = age.toIntOrNull()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (profile == null) "Set up your profile" else "Edit profile",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Load scores divide by heart-rate reserve, so these numbers drive every " +
                    "figure below. Leave the overrides blank to use Health Connect data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            NumberField(value = age, onValueChange = { age = it }, label = "Age")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sex.entries.forEach { option ->
                    FilterChip(
                        selected = sex == option,
                        onClick = { sex = option },
                        label = { Text(option.name.lowercase().replaceFirstChar(Char::uppercase)) },
                    )
                }
            }
            Text(
                text = "Sex selects the TRIMP coefficients. It is not cosmetic.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            NumberField(resting, { resting = it }, "Resting HR override (optional)")
            NumberField(max, { max = it }, "Max HR override (optional)")
            NumberField(threshold, { threshold = it }, "Threshold HR override (optional)")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onSave(
                            parsedAge ?: return@Button,
                            sex,
                            resting.toIntOrNull(),
                            max.toIntOrNull(),
                            threshold.toIntOrNull(),
                        )
                    },
                    enabled = parsedAge != null && parsedAge in 10..110,
                ) {
                    Text("Save and re-score")
                }
                onCancel?.let {
                    OutlinedButton(onClick = it) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter(Char::isDigit).take(3)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun hrLabel(bpm: Int, estimated: Boolean): String =
    "$bpm bpm${if (estimated) " (estimated)" else ""}"
