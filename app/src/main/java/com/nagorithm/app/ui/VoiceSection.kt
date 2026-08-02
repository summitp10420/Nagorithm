package com.nagorithm.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nagorithm.app.verdict.NagTone
import com.nagorithm.app.verdict.VoiceFamily

/**
 * Where the nag gets its voice. Deliberately separate from the effort numbers, because nothing
 * chosen here changes a single figure — only the wording wrapped around them.
 */
@Composable
fun VoiceSection(
    tone: NagTone,
    maskedApiKey: String?,
    onToneChange: (NagTone) -> Unit,
    onSaveApiKey: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Voice", style = MaterialTheme.typography.titleMedium)

            Text(
                "How it talks to you. What it says is decided before this point.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ToneChips(selected = tone, onSelect = onToneChange)

            Text(
                tone.guidance,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ApiKeyField(maskedApiKey = maskedApiKey, onSave = onSaveApiKey)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToneChips(selected: NagTone, onSelect: (NagTone) -> Unit) {
    // Seven of these will not sit on one line of a phone, and a row that silently clips is a
    // character the user never discovers exists.
    VoiceFamily.entries.forEach { family ->
        Text(
            text = if (family == VoiceFamily.PLAIN) "Registers" else "Characters",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NagTone.of(family).forEach { tone ->
                FilterChip(
                    selected = tone == selected,
                    onClick = { onSelect(tone) },
                    label = { Text(tone.label) },
                )
            }
        }
    }
}

@Composable
private fun ApiKeyField(maskedApiKey: String?, onSave: (String) -> Unit) {
    var entry by remember { mutableStateOf("") }
    var editing by remember(maskedApiKey) { mutableStateOf(maskedApiKey == null) }

    if (!editing) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Gemini key saved", style = MaterialTheme.typography.bodyMedium)
                Text(
                    maskedApiKey.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { editing = true }) { Text("Replace") }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = entry,
            onValueChange = { entry = it },
            label = { Text("Gemini API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            // The honest version, because "optional" undersells how little is lost without it and
            // oversells nothing about what it costs.
            "Optional. Without one you still get nagged, just in stock wording. Calls go to " +
                "Google on your key and your bill; only the verdict is sent, never your activity.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    onSave(entry)
                    entry = ""
                    editing = false
                },
                enabled = entry.isNotBlank(),
            ) { Text("Save") }

            if (maskedApiKey != null) {
                TextButton(
                    onClick = {
                        onSave("")
                        entry = ""
                        editing = false
                    },
                ) { Text("Remove") }
            }
        }
    }
}
