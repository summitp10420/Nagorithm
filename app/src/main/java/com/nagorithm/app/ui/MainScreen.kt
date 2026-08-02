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
import com.nagorithm.app.data.Goal
import com.nagorithm.app.data.GoalDimension
import com.nagorithm.app.data.Sex
import com.nagorithm.app.data.StoredActivity
import com.nagorithm.app.effort.EffortSnapshot
import com.nagorithm.app.health.SportCategory
import com.nagorithm.app.verdict.CheckIn
import com.nagorithm.app.verdict.NagTone

/**
 * Composition root. The check-in card is the product; everything below it is either the goal that
 * gives the card something to judge against, or a verification harness for the numbers it quotes.
 */
@Composable
fun MainScreen(
    state: HealthConnectUiState,
    syncState: SyncUiState,
    storedActivities: List<StoredActivity>,
    profileState: ProfileUiState,
    effort: EffortSnapshot?,
    goal: Goal?,
    checkIn: CheckIn?,
    maskedApiKey: String?,
    requiredPermissions: Set<String>,
    requestablePermissions: Set<String>,
    onCheck: () -> Unit,
    onSync: () -> Unit,
    onSaveProfile: (Int, Sex, Int?, Int?, Int?) -> Unit,
    onSetGoal: (String, GoalDimension, SportCategory?, Double?, Long?) -> Unit,
    onToneChange: (NagTone) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onAcknowledge: () -> Unit,
    onNagNow: () -> Unit,
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
                text = "Stage 4c — voice and nagging",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            CheckInSection(checkIn = checkIn, onAcknowledge = onAcknowledge)

            GoalSection(
                goal = goal,
                progress = checkIn?.verdict?.goalProgress,
                onSetGoal = onSetGoal,
            )

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

            EffortSection(snapshot = effort, profile = profile)

            // Only once there is a profile, since tone is stored on it and there is nothing to
            // write to before onboarding.
            if (profile != null) {
                VoiceSection(
                    tone = profile.tone,
                    maskedApiKey = maskedApiKey,
                    onToneChange = onToneChange,
                    onSaveApiKey = onSaveApiKey,
                )
            }

            ProfileSection(state = profileState, onSave = onSaveProfile)

            DiagnosticsSection(
                state = state,
                syncState = syncState,
                storedActivities = storedActivities,
                requiredPermissions = requestablePermissions,
                onCheck = onCheck,
                onNagNow = onNagNow,
                onRecaptureBaseline = onRecaptureBaseline,
            )
        }
    }
}
