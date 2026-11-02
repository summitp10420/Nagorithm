package com.nagorithm.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nagorithm.app.data.ActivityRepository
import com.nagorithm.app.data.Goal
import com.nagorithm.app.data.GoalDimension
import com.nagorithm.app.data.NagorithmDatabase
import com.nagorithm.app.data.Note
import com.nagorithm.app.data.Profile
import com.nagorithm.app.data.Sex
import com.nagorithm.app.data.StoredActivity
import com.nagorithm.app.data.SyncResult
import com.nagorithm.app.effort.EffortSnapshot
import com.nagorithm.app.effort.RampSignal
import com.nagorithm.app.effort.TrainingBalance
import com.nagorithm.app.effort.acwrReading
import com.nagorithm.app.effort.trainingBalance
import com.nagorithm.app.health.HealthConnectAvailability
import com.nagorithm.app.health.HealthConnectManager
import com.nagorithm.app.health.ImportWatermark
import com.nagorithm.app.health.SportCategory
import com.nagorithm.app.health.importWindowStart
import com.nagorithm.app.llm.ApiKeyStore
import com.nagorithm.app.llm.GeminiClient
import com.nagorithm.app.llm.NagWriter
import com.nagorithm.app.llm.RecentPicture
import com.nagorithm.app.verdict.CheckIn
import com.nagorithm.app.verdict.Nag
import com.nagorithm.app.verdict.NagTemplates
import com.nagorithm.app.verdict.NagTone
import com.nagorithm.app.verdict.VerdictEngine
import com.nagorithm.app.verdict.VerdictState
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

/** One side of the bake-off: the verdict a signal reached, and the words that came out of it. */
data class SignalCandidate(
    val signal: RampSignal,
    val state: VerdictState,
    val nag: Nag,
    /** The one number behind it, so a disagreement can be understood rather than just observed. */
    val detail: String,
    val note: String,
)

sealed interface ComparisonUiState {
    data object Idle : ComparisonUiState

    data object Running : ComparisonUiState

    data class Failed(val message: String) : ComparisonUiState

    data class Ready(
        val acwr: SignalCandidate,
        val form: SignalCandidate,
        val caveats: List<String>,
    ) : ComparisonUiState {
        /** The only case worth reading closely. Agreement means the choice does not matter today. */
        val disagree: Boolean get() = acwr.state != form.state
    }
}

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

    private val watermark = ImportWatermark(application)

    private val repository = NagorithmDatabase.get(application).let { db ->
        ActivityRepository(
            healthConnect = healthConnect,
            activityDao = db.activityDao(),
            profileDao = db.profileDao(),
            goalDao = db.goalDao(),
            spokenNagDao = db.spokenNagDao(),
            noteDao = db.noteDao(),
        )
    }

    val notes: StateFlow<List<Note>> = repository.observeNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val apiKeyStore = ApiKeyStore(application)

    /**
     * Only ever the masked form. The key goes in once and is never read back out to the screen,
     * so there is nothing to shoulder-read and nothing to catch in a screenshot.
     */
    private val _maskedApiKey = MutableStateFlow(apiKeyStore.maskedKey())
    val maskedApiKey: StateFlow<String?> = _maskedApiKey.asStateFlow()

    val requiredPermissions: Set<String> = healthConnect.requiredPermissions

    /** Includes background reading, which is optional for syncing but essential for nagging. */
    val allPermissions: Set<String> = healthConnect.allPermissions

    private val _uiState = MutableStateFlow(HealthConnectUiState())
    val uiState: StateFlow<HealthConnectUiState> = _uiState.asStateFlow()

    private val _comparison = MutableStateFlow<ComparisonUiState>(ComparisonUiState.Idle)
    val comparison: StateFlow<ComparisonUiState> = _comparison.asStateFlow()

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
        repository.observeLatestSpokenNag(),
        _feedAlive,
    ) { activities, profile, goal, spoken, feedAlive ->
        val snapshot = EffortSnapshot.from(activities)
        val verdict = VerdictEngine.evaluate(
            activities = activities,
            snapshot = snapshot,
            goal = goal,
            lastCheckInEpochMillis = profile?.lastCheckInEpochMillis,
            feedAlive = feedAlive,
        )

        // Opening the app after a notification has to show the message that arrived, not a second
        // wording of the same verdict. Two versions of one judgment reads as the app changing its
        // mind, and it would mean paying for a model call whose output the user never sees.
        val stillStands = spoken != null &&
            spoken.state == verdict.state &&
            verdict.news.none { it.startTimeEpochMillis > spoken.spokenAtEpochMillis }

        if (stillStands) {
            CheckIn(
                verdict = verdict,
                nag = Nag(spoken!!.headline, spoken.body, spoken.source),
                spokenReasons = spoken.reasonLines,
            )
        } else {
            CheckIn(verdict = verdict, nag = NagTemplates.render(verdict))
        }
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
            val from = importWindowStart(watermark.importedFrom, to, healthConnect.canReadHistory())

            _syncState.value = runCatching { repository.syncFromHealthConnect(from, to) }.fold(
                onSuccess = {
                    // Only after it worked. A crash halfway through a year of samples must not
                    // leave the app believing the deep read is done.
                    watermark.record(from)
                    _feedAlive.value = it.feedAlive
                    SyncUiState.Completed(it, ChronoUnit.DAYS.between(from, to))
                },
                onFailure = { SyncUiState.Failed(it.message ?: it::class.java.simpleName) },
            )
        }
    }

    /**
     * Goes back and gets everything again.
     *
     * The history permission is usually granted after the first sync has already run and set the
     * watermark, so without this the app would sit on a month of data with the means to read a
     * year sitting unused.
     */
    fun reimportHistory() {
        watermark.forget()
        sync()
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

    /** Blank clears it, which is how the user goes back to template nags. */
    fun saveApiKey(key: String) {
        apiKeyStore.geminiKey = key
        _maskedApiKey.value = apiKeyStore.maskedKey()
    }

    fun setTone(tone: NagTone) {
        viewModelScope.launch { repository.setTone(tone) }
    }

    /**
     * Runs the whole pipeline twice, changing one thing.
     *
     * Nothing here is recorded. The escalation counter and the schedule both read the spoken-nag
     * history, so writing experiments into it would have the app believing it had already said
     * things it never said.
     */
    fun compareSignals() {
        _comparison.value = ComparisonUiState.Running

        viewModelScope.launch {
            val result = runCatching {
                val now = Instant.now()
                val activities = repository.allActivities()
                val snapshot = EffortSnapshot.from(activities, now)
                val goal = repository.activeGoal()
                val profile = repository.currentProfile()
                val balance = trainingBalance(activities, now)

                val picture = RecentPicture.build(activities, now)
                val recentNotes =
                    repository.recentNotes(now.minus(RecentPicture.NOTE_WINDOW_DAYS, ChronoUnit.DAYS))
                val notes = RecentPicture.notes(recentNotes, now)
                val noteTexts = RecentPicture.noteTexts(recentNotes)

                val writer = NagWriter(apiKeyStore.geminiKey?.let(::GeminiClient))
                val tone = profile?.tone ?: NagTone.SNARKY

                val candidates = RampSignal.entries.map { signal ->
                    val verdict = VerdictEngine.evaluate(
                        activities = activities,
                        snapshot = snapshot,
                        goal = goal,
                        lastCheckInEpochMillis = profile?.lastCheckInEpochMillis,
                        feedAlive = _feedAlive.value,
                        now = now,
                        rampSignal = signal,
                        balance = balance,
                    )
                    val written = writer.write(verdict, tone, picture, notes, noteTexts)

                    SignalCandidate(
                        signal = signal,
                        state = verdict.state,
                        nag = written.nag,
                        detail = detail(signal, snapshot.acwr, balance),
                        note = written.note,
                    )
                }.associateBy { it.signal }

                ComparisonUiState.Ready(
                    acwr = candidates.getValue(RampSignal.ACWR),
                    form = candidates.getValue(RampSignal.FORM),
                    caveats = caveats(balance),
                )
            }

            _comparison.value = result.getOrElse {
                ComparisonUiState.Failed(it.message ?: it::class.java.simpleName)
            }
        }
    }

    private fun detail(signal: RampSignal, acwr: Double?, balance: TrainingBalance?): String =
        when (signal) {
            RampSignal.ACWR -> acwr
                ?.let { "ACWR %.2f — %s".format(it, acwrReading(it).label) }
                ?: "ACWR unavailable — not enough history to divide by."

            RampSignal.FORM -> when {
                balance == null -> "Form unavailable — nothing scored yet."

                balance.formPercent == null ->
                    "Form withheld — %d day(s) of history and a fitness of %.0f. Not a number yet."
                        .format(balance.daysOfHistory, balance.fitness)

                else -> "Form %+.0f%% of fitness — %s (fitness %.0f, fatigue %.0f)".format(
                    balance.formPercent,
                    balance.reading?.label,
                    balance.fitness,
                    balance.fatigue,
                )
            }
        }

    private fun caveats(balance: TrainingBalance?): List<String> = buildList {
        if (balance == null) return@buildList

        if (!balance.isSettled) {
            add(
                "Fitness has only ${balance.daysOfHistory} day(s) behind it, and a 42-day average " +
                    "needs about ${TrainingBalance.SETTLING_DAYS} before it stops being mostly " +
                    "the zero it started from, so form is being withheld rather than guessed at. " +
                    "Granting history access and re-importing is what fixes this, not waiting.",
            )
        }
        if (balance.estimatedShare > 0.3) {
            add(
                "${"%.0f".format(balance.estimatedShare * 100)}% of the last month's load was " +
                    "estimated from duration rather than measured. Form is a difference between " +
                    "two large numbers, so it amplifies that rather than averaging it out.",
            )
        }
        add(
            "Neither signal can see lifting, which never reaches the database. Both will read " +
                "fresher than you are in a week with heavy strength work.",
        )
    }

    fun addNote(text: String) {
        viewModelScope.launch { repository.addNote(text) }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch { repository.deleteNote(id) }
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
}
