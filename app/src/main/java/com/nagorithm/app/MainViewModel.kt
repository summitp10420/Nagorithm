package com.nagorithm.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nagorithm.app.data.ActivityRepository
import com.nagorithm.app.data.NagorithmDatabase
import com.nagorithm.app.data.StoredActivity
import com.nagorithm.app.data.SyncResult
import com.nagorithm.app.health.HealthConnectAvailability
import com.nagorithm.app.health.HealthConnectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

data class HealthConnectUiState(
    val hasChecked: Boolean = false,
    val isChecking: Boolean = false,
    val availability: HealthConnectAvailability? = null,
    val grantedPermissions: Set<String> = emptySet(),
    val errorMessage: String? = null,
)

sealed interface SyncUiState {
    data object Idle : SyncUiState

    data object Syncing : SyncUiState

    data class Completed(val result: SyncResult, val windowDays: Long) : SyncUiState

    data class Failed(val message: String) : SyncUiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val healthConnect = HealthConnectManager(application)

    private val repository = ActivityRepository(
        healthConnect = healthConnect,
        activityDao = NagorithmDatabase.get(application).activityDao(),
    )

    val requiredPermissions: Set<String> = healthConnect.requiredPermissions

    private val _uiState = MutableStateFlow(HealthConnectUiState())
    val uiState: StateFlow<HealthConnectUiState> = _uiState.asStateFlow()

    private val _syncState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    val storedActivities: StateFlow<List<StoredActivity>> = repository.observeActivities()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun checkHealthConnect() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isChecking = true, errorMessage = null)

            val availability = healthConnect.availability()
            val result = runCatching { healthConnect.grantedPermissions() }

            _uiState.value = HealthConnectUiState(
                hasChecked = true,
                isChecking = false,
                availability = availability,
                grantedPermissions = result.getOrDefault(emptySet()),
                errorMessage = result.exceptionOrNull()?.let { it.message ?: it::class.java.simpleName },
            )
        }
    }

    fun sync() {
        viewModelScope.launch {
            _syncState.value = SyncUiState.Syncing

            val to = Instant.now()
            val from = to.minus(FETCH_WINDOW_DAYS, ChronoUnit.DAYS)

            _syncState.value = runCatching { repository.syncFromHealthConnect(from, to) }.fold(
                onSuccess = { SyncUiState.Completed(it, FETCH_WINDOW_DAYS) },
                onFailure = { SyncUiState.Failed(it.message ?: it::class.java.simpleName) },
            )
        }
    }

    private companion object {
        const val FETCH_WINDOW_DAYS = 30L
    }
}
