package com.nagorithm.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.nagorithm.app.HealthConnectUiState
import com.nagorithm.app.SyncUiState
import com.nagorithm.app.data.StoredActivity
import com.nagorithm.app.health.ExerciseScanDiagnostics
import com.nagorithm.app.health.HealthConnectAvailability
import com.nagorithm.app.health.HealthConnectManager

/**
 * Connection plumbing and the raw stored rows. None of this is product — it exists to prove the
 * numbers above it came from somewhere real, and it collapses so it stops dominating the screen.
 */
@Composable
fun DiagnosticsSection(
    state: HealthConnectUiState,
    syncState: SyncUiState,
    storedActivities: List<StoredActivity>,
    requiredPermissions: Set<String>,
    onCheck: () -> Unit,
    onNagNow: () -> Unit,
    onRecaptureBaseline: () -> Unit,
) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { onCheck() }

    SyncStatus(syncState)

    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (expanded) {
                "Hide diagnostics"
            } else {
                "Show diagnostics (${storedActivities.size} stored)"
            },
        )
    }

    if (!expanded) return

    OutlinedButton(
        onClick = onCheck,
        enabled = !state.isChecking,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (state.isChecking) "Checking…" else "Check Health Connect")
    }

    // Runs the entire unattended path — background sync, verdict, notification — so the thing
    // that normally happens invisibly every few hours can be watched happening once.
    OutlinedButton(
        onClick = {
            onNagNow()
            Toast.makeText(context, "Running a check-in in the background…", Toast.LENGTH_SHORT)
                .show()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Nag me now")
    }

    if (state.hasChecked) {
        StatusCard(state.availability)

        when (state.availability) {
            HealthConnectAvailability.NOT_SUPPORTED ->
                ProviderButton("Install Health Connect", context)

            HealthConnectAvailability.UPDATE_REQUIRED ->
                ProviderButton("Update Health Connect", context)

            HealthConnectAvailability.AVAILABLE -> {
                PermissionsCard(requiredPermissions, state.grantedPermissions)
                if (!state.grantedPermissions.containsAll(requiredPermissions)) {
                    OutlinedButton(
                        onClick = { permissionLauncher.launch(requiredPermissions) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Request permissions")
                    }
                }
            }

            null -> Unit
        }
    }

    state.errorMessage?.let { message ->
        Text(
            text = "Error: $message",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }

    OutlinedButton(onClick = onRecaptureBaseline, modifier = Modifier.fillMaxWidth()) {
        Text("Recapture baseline from today")
    }
    Text(
        text = "Discards the onboarding snapshot and re-takes it as of now. Only sensible while " +
            "the baseline is new — later it is the reference every long-term signal depends on.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    (syncState as? SyncUiState.Completed)?.let {
        ScanDiagnosticsCard(it.result.diagnostics, it.windowDays)
    }

    ActivityListSection(storedActivities)
}

@Composable
private fun SyncStatus(syncState: SyncUiState) {
    when (syncState) {
        SyncUiState.Idle -> Unit

        SyncUiState.Syncing -> Text(
            text = "Reading Health Connect…",
            style = MaterialTheme.typography.bodyMedium,
        )

        is SyncUiState.Failed -> Text(
            text = "Sync failed: ${syncState.message}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )

        is SyncUiState.Completed -> {
            val r = syncState.result
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${r.aerobicFound} aerobic session(s) in ${syncState.windowDays} days · " +
                        "${r.newlyStored} new",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${r.scoredFromHeartRate} scored from heart rate, " +
                        "${r.scoredFromEstimate} estimated · detected resting " +
                        "${r.detectedRestingHr?.let { "$it bpm" } ?: "none in Health Connect"}, " +
                        "peak ${r.detectedMaxHr ?: "—"} bpm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProviderButton(label: String, context: Context) {
    OutlinedButton(
        onClick = { launchProviderInstall(context) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
}

@Composable
private fun StatusCard(availability: HealthConnectAvailability?) {
    val (title, detail) = when (availability) {
        HealthConnectAvailability.NOT_SUPPORTED ->
            "Not installed" to "Health Connect is not available on this device."

        HealthConnectAvailability.UPDATE_REQUIRED ->
            "Needs update" to "The installed Health Connect provider is too old for this app."

        HealthConnectAvailability.AVAILABLE ->
            "Available" to "Health Connect is installed and ready."

        null -> "Unknown" to "No status yet."
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("SDK status", style = MaterialTheme.typography.labelMedium)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PermissionsCard(requiredPermissions: Set<String>, grantedPermissions: Set<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Permissions", style = MaterialTheme.typography.labelMedium)
            Text(
                text = "${grantedPermissions.count { it in requiredPermissions }} of " +
                    "${requiredPermissions.size} granted",
                style = MaterialTheme.typography.titleMedium,
            )
            requiredPermissions.sorted().forEach { permission ->
                val granted = permission in grantedPermissions
                Text(
                    text = "${if (granted) "granted" else "denied "}  " +
                        permission.substringAfterLast('.'),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ScanDiagnosticsCard(diagnostics: ExerciseScanDiagnostics, windowDays: Long) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Debug scan", style = MaterialTheme.typography.labelMedium)
            Text(
                text = "${diagnostics.aerobicSessions} aerobic of " +
                    "${diagnostics.sessionsInWindow} session(s)",
                style = MaterialTheme.typography.titleMedium,
            )

            if (diagnostics.typeCounts.isEmpty()) {
                Text(
                    text = "Health Connect returned nothing at all. No app is writing exercise " +
                        "sessions to it, or nothing was recorded in this window.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                diagnostics.typeCounts.entries
                    .sortedByDescending { it.value }
                    .forEach { (type, count) ->
                        DetailRow(type, count.toString(), labelWidth = 180.dp)
                    }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                Text("Written by", style = MaterialTheme.typography.labelMedium)
                diagnostics.writerPackages.sorted().forEach { pkg ->
                    Text(text = pkg, style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(
                text = "Scan covers $windowDays days. Health Connect will not serve data from " +
                    "before permissions were granted minus 30 days, so older sessions are " +
                    "invisible without the history permission.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun launchProviderInstall(context: Context) {
    try {
        context.startActivity(HealthConnectManager.providerInstallIntent(context.packageName))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Play Store is not available on this device", Toast.LENGTH_LONG)
            .show()
    }
}
