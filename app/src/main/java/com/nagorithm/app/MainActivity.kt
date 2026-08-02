package com.nagorithm.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nagorithm.app.ui.MainScreen
import com.nagorithm.app.ui.theme.NagorithmTheme
import com.nagorithm.app.work.NagNotifier
import com.nagorithm.app.work.NagWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // KEEP means this is idempotent, so it can run on every launch without postponing the
        // next nag each time the app is opened.
        NagWorker.schedule(this)

        setContent {
            NagorithmTheme {
                val viewModel: MainViewModel = viewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val syncState by viewModel.syncState.collectAsStateWithLifecycle()
                val storedActivities by viewModel.storedActivities.collectAsStateWithLifecycle()
                val profileState by viewModel.profileState.collectAsStateWithLifecycle()
                val effort by viewModel.effort.collectAsStateWithLifecycle()
                val goal by viewModel.activeGoal.collectAsStateWithLifecycle()
                val checkIn by viewModel.checkIn.collectAsStateWithLifecycle()
                val maskedApiKey by viewModel.maskedApiKey.collectAsStateWithLifecycle()

                RequestNotificationPermission()

                MainScreen(
                    state = state,
                    syncState = syncState,
                    storedActivities = storedActivities,
                    profileState = profileState,
                    effort = effort,
                    goal = goal,
                    checkIn = checkIn,
                    maskedApiKey = maskedApiKey,
                    requiredPermissions = viewModel.requiredPermissions,
                    requestablePermissions = viewModel.allPermissions,
                    onCheck = viewModel::checkHealthConnect,
                    onSync = viewModel::sync,
                    onSaveProfile = viewModel::saveProfile,
                    onSetGoal = viewModel::setGoal,
                    onToneChange = viewModel::setTone,
                    onSaveApiKey = viewModel::saveApiKey,
                    onAcknowledge = viewModel::acknowledgeCheckIn,
                    onNagNow = { NagWorker.runNow(this) },
                    onRecaptureBaseline = viewModel::recaptureBaseline,
                )
            }
        }
    }
}

/**
 * Asked for on launch rather than behind a button, because an app whose entire purpose is to
 * interrupt you is useless without it, and the system only offers the prompt once.
 */
@androidx.compose.runtime.Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (!NagNotifier.canPost(context)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
