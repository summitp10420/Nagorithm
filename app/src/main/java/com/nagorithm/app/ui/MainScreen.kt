package com.nagorithm.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nagorithm.app.HealthConnectUiState
import com.nagorithm.app.ProfileUiState
import com.nagorithm.app.SyncUiState
import com.nagorithm.app.data.Sex
import com.nagorithm.app.data.StoredActivity
import com.nagorithm.app.effort.EffortSnapshot

/**
 * Composition root for the single screen that exists so far. Everything below the effort readout
 * is a verification harness rather than product, and lives behind the diagnostics section.
 */
@Composable
fun MainScreen(
    state: HealthConnectUiState,
    syncState: SyncUiState,
    storedActivities: List<StoredActivity>,
    profileState: ProfileUiState,
    effort: EffortSnapshot?,
    requiredPermissions: Set<String>,
    onCheck: () -> Unit,
    onSync: () -> Unit,
    onSaveProfile: (Int, Sex, Int?, Int?, Int?) -> Unit,
    onRecaptureBaseline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = (profileState as? ProfileUiState.Loaded)?.profile
    val canSync = state.grantedPermissions.containsAll(requiredPermissions)

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
                text = "Stage 4a — local effort scoring",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ProfileSection(state = profileState, onSave = onSaveProfile)

            EffortSection(snapshot = effort, profile = profile)

            if (canSync) {
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
            }

            DiagnosticsSection(
                state = state,
                syncState = syncState,
                storedActivities = storedActivities,
                requiredPermissions = requiredPermissions,
                onCheck = onCheck,
                onRecaptureBaseline = onRecaptureBaseline,
            )
        }
    }
}
