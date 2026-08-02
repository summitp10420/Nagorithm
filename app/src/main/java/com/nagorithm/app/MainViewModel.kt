package com.nagorithm.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nagorithm.app.data.ActivityRepository
import com.nagorithm.app.data.Goal
import com.nagorithm.app.data.GoalDimension
import com.nagorithm.app.data.NagorithmDatabase
import com.nagorithm.app.data.Profile
import com.nagorithm.app.data.Sex
import com.nagorithm.app.data.StoredActivity
import com.nagorithm.app.data.SyncResult
import com.nagorithm.app.effort.EffortSnapshot
import com.nagorithm.app.health.HealthConnectAvailability
import com.nagorithm.app.health.HealthConnectManager
import com.nagorithm.app.health.SportCategory
import com.nagorithm.app.verdict.CheckIn
import com.nagorithm.app.verdict.NagTemplates
import com.nagorithm.app.verdict.VerdictEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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

/**
 * Distinguishes "no profile saved" from "not read yet", so first launch does not flash the
 * setup form at a user who already has one.
 */
sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    data class Loaded(val profile: Profile?) : ProfileUiState
}

sealed interface SyncUiState {
    data object Idle : SyncUiState

    data object Syncing : SyncUiState

    data class Completed(val result: SyncResult, val windowDays: Long) : SyncUiState

    data class Failed(val message: String) : SyncUiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val healthConnect = HealthConnectManager(application)

    private val repository = NagorithmDatabase.get(application).let { db ->
        ActivityRepository(
            healthConnect = healthConnect,
            activityDao = db.activityDao(),
            profileDao = db.profileDao(),
            goalDao = db.goalDao(),
        )
    }

    val requiredPermissions: Set<String> = healthConnect.requiredPermissions

    private val _uiState = MutableStateFlow(HealthConnectUiState())
    val uiState: StateFlow<HealthConnectUiState> = _uiState.asStateFlow()

    private val _syncState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    val storedActivities: StateFlow<List<StoredActivity>> = repository.observeActivities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val profileState: StateFlow<ProfileUiState> = repository.observeProfile()
        .map { ProfileUiState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState.Loading)

    val effort: StateFlow<EffortSnapshot?> = repository.observeActivities()
        .map { EffortSnapshot.from(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val activeGoal: StateFlow<Goal?> = repository.observeActiveGoal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Null until a sync has actually probed Health Connect. Unknown is deliberately not the same
     * as dead — the app must never claim a broken feed on the strength of never having looked.
     */
    private val _feedAlive = MutableStateFlow<Boolean?>(null)

    /**
     * Derived rather than generated on a button press, so an unacknowledged nag survives closing
     * the app. It stops being news only when the user says they have read it.
     */
    val checkIn: StateFlow<CheckIn?> = combine(
        repository.observeActivities(),
        repository.observeProfile(),
        repository.observeActiveGoal(),
        _feedAlive,
    ) { activities, profile, goal, feedAlive ->
        val snapshot = EffortSnapshot.from(activities)
        val verdict = VerdictEngine.evaluate(
            activities = activities,
            snapshot = snapshot,
            goal = goal,
            lastCheckInEpochMillis = profile?.lastCheckInEpochMillis,
            feedAlive = feedAlive,
        )
        CheckIn(verdict = verdict, nag = NagTemplates.render(verdict))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // The diagnostics panel that used to host this button is collapsed by default now.
        checkHealthConnect()
    }

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
                onSuccess = {
                    _feedAlive.value = it.feedAlive
                    SyncUiState.Completed(it, FETCH_WINDOW_DAYS)
                },
                onFailure = { SyncUiState.Failed(it.message ?: it::class.java.simpleName) },
            )
        }
    }

    fun setGoal(
        text: String,
        dimension: GoalDimension,
        sport: SportCategory?,
        targetDistanceMeters: Double?,
        targetDateEpochMillis: Long?,
    ) {
        viewModelScope.launch {
            repository.setGoal(
                text = text,
                dimension = dimension,
                sport = sport,
                targetDistanceMeters = targetDistanceMeters,
                targetDateEpochMillis = targetDateEpochMillis,
            )
        }
    }

    /** Marks the current nag as read. Everything synced after this counts as news again. */
    fun acknowledgeCheckIn() {
        viewModelScope.launch { repository.markCheckedIn() }
    }

    /** Only valid while the baseline is young enough that re-taking it as of today loses nothing. */
    fun recaptureBaseline() {
        viewModelScope.launch {
            repository.clearBaseline()
            sync()
        }
    }

    fun saveProfile(
        age: Int,
        sex: Sex,
        restingHrOverride: Int?,
        maxHrOverride: Int?,
        thresholdHrOverride: Int?,
    ) {
        viewModelScope.launch {
            val existing = repository.currentProfile()
            repository.saveProfile(
                existing?.copy(
                    age = age,
                    sex = sex,
                    restingHrOverride = restingHrOverride,
                    maxHrOverride = maxHrOverride,
                    thresholdHrOverride = thresholdHrOverride,
                ) ?: Profile(
                    age = age,
                    sex = sex,
                    restingHrOverride = restingHrOverride,
                    maxHrOverride = maxHrOverride,
                    thresholdHrOverride = thresholdHrOverride,
                    onboardedAtEpochMillis = Instant.now().toEpochMilli(),
                ),
            )
            // Loads are derived from these values, so they are stale the moment they change.
            sync()
        }
    }

    private companion object {
        const val FETCH_WINDOW_DAYS = 30L
    }
}
