package com.nagorithm.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.nagorithm.app.HealthConnectUiState
import com.nagorithm.app.SyncUiState
import com.nagorithm.app.data.StoredActivity
import com.nagorithm.app.data.formattedFirstSeen
import com.nagorithm.app.data.formattedStart
import com.nagorithm.app.health.ExerciseScanDiagnostics
import com.nagorithm.app.health.HealthConnectAvailability
import com.nagorithm.app.health.HealthConnectManager
import com.nagorithm.app.health.formatDistance
import com.nagorithm.app.health.formatDuration
import com.nagorithm.app.health.formatHeartRate

@Composable
fun HealthConnectScreen(
    state: HealthConnectUiState,
    syncState: SyncUiState,
    storedActivities: List<StoredActivity>,
    requiredPermissions: Set<String>,
    onCheck: () -> Unit,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { onCheck() }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Nagorithm", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "Stage 3 — store cycling activities locally",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onCheck,
                enabled = !state.isChecking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isChecking) "Checking…" else "Check Health Connect")
            }

            if (!state.hasChecked) {
                Text(
                    text = "Tap the button to read the current Health Connect SDK status.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                StoredActivitiesSection(storedActivities)
                return@Column
            }

            StatusCard(availability = state.availability)

            when (state.availability) {
                HealthConnectAvailability.NOT_SUPPORTED -> ProviderButton(
                    label = "Install Health Connect",
                    context = context,
                )

                HealthConnectAvailability.UPDATE_REQUIRED -> ProviderButton(
                    label = "Update Health Connect",
                    context = context,
                )

                HealthConnectAvailability.AVAILABLE -> {
                    PermissionsCard(
                        requiredPermissions = requiredPermissions,
                        grantedPermissions = state.grantedPermissions,
                    )

                    if (state.grantedPermissions.containsAll(requiredPermissions)) {
                        Button(
                            onClick = onSync,
                            enabled = syncState !is SyncUiState.Syncing,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (syncState is SyncUiState.Syncing) {
                                    "Syncing…"
                                } else {
                                    "Sync from Health Connect"
                                },
                            )
                        }
                        SyncStatus(syncState)
                    } else {
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

            StoredActivitiesSection(storedActivities)

            (syncState as? SyncUiState.Completed)?.let { completed ->
                ScanDiagnosticsCard(completed.result.diagnostics, completed.windowDays)
            }

            state.errorMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Error: $message",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
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
private fun PermissionsCard(
    requiredPermissions: Set<String>,
    grantedPermissions: Set<String>,
) {
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
                    text = "${if (granted) "granted" else "denied "}  ${shortName(permission)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
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
            Text(
                text = "Found ${r.cyclingFound} ride(s) in ${syncState.windowDays} days · " +
                    "${r.newlyStored} new · ${r.alreadyStored} already stored",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun StoredActivitiesSection(activities: List<StoredActivity>) {
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
    } else {
        activities.forEach { activity -> StoredActivityCard(activity) }
    }
}

@Composable
private fun StoredActivityCard(activity: StoredActivity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = activity.title ?: if (activity.isStationary) "Indoor ride" else "Ride",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = activity.formattedStart(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            DetailRow("Elapsed", formatDuration(activity.elapsedDuration))
            activity.activeDuration?.let { DetailRow("Active", formatDuration(it)) }
            DetailRow("Distance", formatDistance(activity.distanceMeters))
            DetailRow("Avg HR", formatHeartRate(activity.averageHeartRateBpm))

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            DetailRow("First seen", activity.formattedFirstSeen())
            DetailRow("Source", activity.sourcePackage)
            DetailRow("Record ID", activity.recordId)
        }
    }
}

@Composable
private fun ScanDiagnosticsCard(
    diagnostics: ExerciseScanDiagnostics,
    windowDays: Long,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Debug scan", style = MaterialTheme.typography.labelMedium)
            Text(
                text = "${diagnostics.sessionsInWindow} exercise session(s) of any type",
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
                    "before permissions were granted minus 30 days, so older rides are " +
                    "invisible without the history permission.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, labelWidth: Dp = 96.dp) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(labelWidth),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun shortName(permission: String): String = permission.substringAfterLast('.')

private fun launchProviderInstall(context: Context) {
    try {
        context.startActivity(HealthConnectManager.providerInstallIntent(context.packageName))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Play Store is not available on this device", Toast.LENGTH_LONG)
            .show()
    }
}
