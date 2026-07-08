package com.saiyanstrong.presentation.screens.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.BuildConfig
import com.saiyanstrong.domain.model.AppUpdate
import com.saiyanstrong.domain.model.BodyWeightLog
import com.saiyanstrong.domain.model.PowerLevel
import com.saiyanstrong.domain.model.WorkoutSession
import com.saiyanstrong.domain.repository.SessionRepository
import com.saiyanstrong.domain.repository.UserRepository
import com.saiyanstrong.domain.usecase.CheckForUpdateUseCase
import com.saiyanstrong.domain.usecase.EstimateOneRepMaxUseCase
import com.saiyanstrong.domain.usecase.GetEvolutionStageUseCase
import com.saiyanstrong.util.UpdateInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class LiftStat(
    val label: String,       // SQ / BP / DL
    val bestE1RmKg: Double,
    val spark: List<Double>  // per-session best e1RM, oldest → newest
)

data class DashboardStats(
    val streakWeeks: Int = 0,
    val bigThree: List<LiftStat> = emptyList(),
    val heat: List<Int> = emptyList()  // sessions per week, oldest → newest (12 weeks)
)

// DOTS polynomial coefficients (denominator = a + b·x + c·x² + d·x³ + e·x⁴, x = bodyweight kg)
private val DOTS_MALE   = doubleArrayOf(-307.75076, 24.0900756, -0.1918759221, 0.0007391293, -0.000001093)
private val DOTS_FEMALE = doubleArrayOf(-57.96288, 13.6175032, -0.1126655495, 0.0005158568, -0.0000010706)

data class WeekStats(
    val sessions: Int = 0,
    val volumeKg: Double = 0.0,
    val topLiftKg: Double = 0.0,
    val topLiftName: String = ""
)

sealed class UpdateDownloadState {
    data object Idle : UpdateDownloadState()
    data class Downloading(val percent: Int) : UpdateDownloadState()
    data class Ready(val uri: Uri) : UpdateDownloadState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    getEvolutionStageUseCase: GetEvolutionStageUseCase,
    sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val checkForUpdateUseCase: CheckForUpdateUseCase,
    private val updateInstaller: UpdateInstaller,
    private val estimateOneRepMaxUseCase: EstimateOneRepMaxUseCase
) : ViewModel() {

    val powerLevel: StateFlow<PowerLevel?> = getEvolutionStageUseCase.execute()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val allSessions: StateFlow<List<WorkoutSession>> = sessionRepository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dashboardStats: StateFlow<DashboardStats> = allSessions
        .map { sessions -> computeDashboard(sessions) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardStats())

    val thisWeekStats: StateFlow<WeekStats> = allSessions
        .map { sessions -> computeWeekStats(sessions) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekStats())

    // Newest first, as stored
    val bodyWeightLogs: StateFlow<List<BodyWeightLog>> = userRepository.getBodyWeightLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dotsScore: StateFlow<Double?> = combine(
        allSessions,
        bodyWeightLogs,
        userRepository.getUseFemaleDotsFormula()
    ) { sessions, weights, useFemale ->
        computeDots(sessions, weights.firstOrNull()?.weightKg, useFemale)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onLogBodyWeight(weightKg: Double) {
        if (weightKg <= 0.0) return
        viewModelScope.launch { userRepository.logBodyWeight(weightKg) }
    }

    private fun computeDots(
        sessions: List<WorkoutSession>,
        bodyWeightKg: Double?,
        useFemale: Boolean
    ): Double? {
        if (bodyWeightKg == null || bodyWeightKg < 20.0) return null

        val total = LIFT_LABELS.sumOf { label -> bestE1RmForLift(sessions, label) }
        if (total <= 0.0) return null

        val c = if (useFemale) DOTS_FEMALE else DOTS_MALE
        val x = bodyWeightKg
        val denominator = c[0] + c[1] * x + c[2] * x * x + c[3] * x * x * x + c[4] * x * x * x * x
        if (denominator <= 0.0) return null
        return total * 500.0 / denominator
    }

    private val _updateAvailable = MutableStateFlow<AppUpdate?>(null)
    val updateAvailable: StateFlow<AppUpdate?> = _updateAvailable.asStateFlow()

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private val _updateStatus = MutableStateFlow("checking…")
    val updateStatus: StateFlow<String> = _updateStatus.asStateFlow()

    init {
        checkForUpdate()
    }

    fun canInstallPackages(): Boolean = updateInstaller.canInstallPackages()

    fun retryUpdateCheck() { checkForUpdate() }

    fun onDownloadUpdate() {
        val update = _updateAvailable.value ?: return
        if (_downloadState.value is UpdateDownloadState.Downloading) return
        _downloadState.value = UpdateDownloadState.Downloading(0)
        viewModelScope.launch {
            val uri = updateInstaller.downloadToCache(update) { percent ->
                _downloadState.value = UpdateDownloadState.Downloading(percent)
            }
            if (uri != null) {
                _downloadState.value = UpdateDownloadState.Ready(uri)
            } else {
                _downloadState.value = UpdateDownloadState.Idle
                _updateStatus.value = "download failed — tap to retry"
            }
        }
    }

    fun onDismissUpdate() {
        viewModelScope.launch {
            _updateAvailable.value?.let { userRepository.saveDismissedUpdateVersion(it.tagName) }
            _updateAvailable.value = null
        }
    }

    fun onInstallConsumed() { _downloadState.value = UpdateDownloadState.Idle }

    private fun checkForUpdate() {
        // Play Store policy forbids self-updating apps — this whole surface stays dark
        // on that flavor and Play's own update mechanism takes over instead.
        if (BuildConfig.DISTRIBUTION != "github") return
        viewModelScope.launch {
            _updateStatus.value = "checking…"
            val dismissed = userRepository.getLastDismissedUpdateVersion().first()
            val retryDelaysMs = listOf(0L, 5_000L, 15_000L)
            var lastError: String? = null
            for ((attempt, delayMs) in retryDelaysMs.withIndex()) {
                if (delayMs > 0L) delay(delayMs)
                _updateStatus.value = "try ${attempt + 1}/3…"
                try {
                    val result = checkForUpdateUseCase.execute(BuildConfig.VERSION_NAME)
                    if (result != null) {
                        if (result.tagName != dismissed) {
                            _updateAvailable.value = result
                            _updateStatus.value = "update ready: ${result.tagName}"
                        } else {
                            _updateStatus.value = "up to date (v${BuildConfig.VERSION_NAME})"
                        }
                        return@launch
                    }
                    _updateStatus.value = "up to date (v${BuildConfig.VERSION_NAME})"
                    return@launch
                } catch (e: Exception) {
                    lastError = e.message
                }
            }
            _updateStatus.value = "check failed: $lastError"
        }
    }

    private fun computeWeekStats(sessions: List<WorkoutSession>): WeekStats {
        val weekStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val thisSessions = sessions.filter { it.dateMs >= weekStart }
        if (thisSessions.isEmpty()) return WeekStats()

        var topLiftKg = 0.0
        var topLiftName = ""
        thisSessions.forEach { session ->
            session.exerciseLogs.forEach { log ->
                val best = log.sets.maxOfOrNull { it.weightKg } ?: 0.0
                if (best > topLiftKg) { topLiftKg = best; topLiftName = log.exercise.name }
            }
        }
        return WeekStats(
            sessions = thisSessions.size,
            volumeKg = thisSessions.sumOf { it.totalVolumeKg },
            topLiftKg = topLiftKg,
            topLiftName = topLiftName
        )
    }

    private fun weekStartMs(weeksAgo: Int): Long = Calendar.getInstance().apply {
        add(Calendar.WEEK_OF_YEAR, -weeksAgo)
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun sessionsInWeek(sessionDates: List<Long>, weeksAgo: Int): Int {
        val start = weekStartMs(weeksAgo)
        val end = start + 7L * 24 * 60 * 60 * 1000
        return sessionDates.count { it >= start && it < end }
    }

    private fun computeDashboard(sessions: List<WorkoutSession>): DashboardStats {
        val dates = sessions.map { it.dateMs }

        val heat = (11 downTo 0).map { weeksAgo -> sessionsInWeek(dates, weeksAgo) }

        // Streak: consecutive weeks with ≥1 session; an empty current week doesn't break it yet
        var weeksAgo = if (sessionsInWeek(dates, 0) == 0) 1 else 0
        var streak = 0
        while (weeksAgo < 104 && sessionsInWeek(dates, weeksAgo) > 0) {
            streak++
            weeksAgo++
        }

        val bigThree = LIFT_LABELS.map { label ->
            val perSessionBests = sessions
                .sortedBy { it.dateMs }
                .mapNotNull { session ->
                    session.exerciseLogs
                        .filter { matchesLift(label, it.exercise.name) }
                        .flatMap { it.sets }
                        .maxOfOrNull { estimateOneRepMaxUseCase.execute(it.weightKg, it.reps) }
                }
            LiftStat(
                label = label,
                bestE1RmKg = perSessionBests.maxOrNull() ?: 0.0,
                spark = perSessionBests.takeLast(10)
            )
        }

        return DashboardStats(streakWeeks = streak, bigThree = bigThree, heat = heat)
    }

    private fun bestE1RmForLift(sessions: List<WorkoutSession>, label: String): Double =
        sessions.maxOfOrNull { session ->
            session.exerciseLogs
                .filter { matchesLift(label, it.exercise.name) }
                .flatMap { it.sets }
                .maxOfOrNull { estimateOneRepMaxUseCase.execute(it.weightKg, it.reps) } ?: 0.0
        } ?: 0.0

    private fun matchesLift(label: String, name: String): Boolean = when (label) {
        "SQ" -> name.contains("Squat", ignoreCase = true) && name.contains("Barbell", ignoreCase = true)
        "BP" -> name.contains("Bench Press", ignoreCase = true) && name.contains("Barbell", ignoreCase = true)
        else -> name.contains("Deadlift", ignoreCase = true) &&
            !name.contains("Romanian", ignoreCase = true) &&
            !name.contains("Stiff", ignoreCase = true)
    }

    companion object {
        private val LIFT_LABELS = listOf("SQ", "BP", "DL")
    }
}
