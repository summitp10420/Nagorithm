package com.nagorithm.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.nagorithm.app.data.Note
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Anything the numbers cannot see: sleep, food, an aching shoulder, feeling strong.
 *
 * There is no prompt to fill this in and nothing chases it, because the moment the app starts
 * asking how the sugar is going it has become a habit tracker. Empty is a perfectly good state.
 */
@Composable
fun NotesSection(
    notes: List<Note>,
    onAddNote: (String) -> Unit,
    onDeleteNote: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var entry by remember { mutableStateOf("") }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Notes", style = MaterialTheme.typography.titleMedium)

            Text(
                "Anything the watch cannot tell. Cutting sugar, sleeping badly, feeling strong, " +
                    "shoulder playing up. Only when you feel like it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = entry,
                onValueChange = { entry = it },
                label = { Text("What is going on?") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            TextButton(
                onClick = {
                    onAddNote(entry)
                    entry = ""
                },
                enabled = entry.isNotBlank(),
            ) { Text("Add note") }

            if (notes.isNotEmpty()) {
                HorizontalDivider()
                notes.forEach { note ->
                    NoteRow(note = note, onDelete = { onDeleteNote(note.id) })
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: Note, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(note.text, style = MaterialTheme.typography.bodyMedium)
            Text(
                age(note.writtenAtEpochMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDelete) { Text("Remove") }
    }
}

private fun age(writtenAtEpochMillis: Long): String =
    when (val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(writtenAtEpochMillis), Instant.now())) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> "$days days ago"
    }
