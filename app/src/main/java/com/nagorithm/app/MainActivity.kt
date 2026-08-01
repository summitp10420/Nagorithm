package com.nagorithm.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nagorithm.app.ui.MainScreen
import com.nagorithm.app.ui.theme.NagorithmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NagorithmTheme {
                val viewModel: MainViewModel = viewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val syncState by viewModel.syncState.collectAsStateWithLifecycle()
                val storedActivities by viewModel.storedActivities.collectAsStateWithLifecycle()
                val profileState by viewModel.profileState.collectAsStateWithLifecycle()
                val effort by viewModel.effort.collectAsStateWithLifecycle()

                MainScreen(
                    state = state,
                    syncState = syncState,
                    storedActivities = storedActivities,
                    profileState = profileState,
                    effort = effort,
                    requiredPermissions = viewModel.requiredPermissions,
                    onCheck = viewModel::checkHealthConnect,
                    onSync = viewModel::sync,
                    onSaveProfile = viewModel::saveProfile,
                    onRecaptureBaseline = viewModel::recaptureBaseline,
                )
            }
        }
    }
}
