package com.saiyanstrong.presentation.screens.home

import android.app.DownloadManager
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
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

data class WeekBar(val label: String, val count: Int)

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
    data object Downloading : UpdateDownloadState()
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

    val weeklyBars: StateFlow<List<WeekBar>> = allSessions
        .map { sessions -> buildWeekBars(sessions.map { it.dateMs }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

        var bestSquat = 0.0
        var bestBench = 0.0
        var bestDeadlift = 0.0
        sessions.forEach { session ->
            session.exerciseLogs.forEach { log ->
                val name = log.exercise.name
                val bestE1Rm = log.sets.maxOfOrNull {
                    estimateOneRepMaxUseCase.execute(it.weightKg, it.reps)
                } ?: 0.0
                when {
                    name.contains("Squat", ignoreCase = true) && name.contains("Barbell", ignoreCase = true) ->
                        if (bestE1Rm > bestSquat) bestSquat = bestE1Rm
                    name.contains("Bench Press", ignoreCase = true) && name.contains("Barbell", ignoreCase = true) ->
                        if (bestE1Rm > bestBench) bestBench = bestE1Rm
                    name.contains("Deadlift", ignoreCase = true) && !name.contains("Romanian", ignoreCase = true) &&
                        !name.contains("Stiff", ignoreCase = true) ->
                        if (bestE1Rm > bestDeadlift) bestDeadlift = bestE1Rm
                }
            }
        }
        val total = bestSquat + bestBench + bestDeadlift
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
        _downloadState.value = UpdateDownloadState.Downloading
        viewModelScope.launch {
            userRepository.saveDismissedUpdateVersion(update.tagName)
            val downloadId = updateInstaller.startDownload(update)
            pollUntilDone(downloadId)
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

    private suspend fun pollUntilDone(downloadId: Long) = withContext(Dispatchers.IO) {
        while (true) {
            delay(500)
            when (updateInstaller.queryStatus(downloadId)) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val uri = updateInstaller.getDownloadedUri(downloadId)
                    _downloadState.value =
                        if (uri != null) UpdateDownloadState.Ready(uri)
                        else UpdateDownloadState.Idle
                    break
                }
                DownloadManager.STATUS_FAILED, -1 -> {
                    _downloadState.value = UpdateDownloadState.Idle
                    break
                }
            }
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

    private fun buildWeekBars(sessionDates: List<Long>): List<WeekBar> =
        (7 downTo 0).map { weeksAgo ->
            val weekStart = Calendar.getInstance().apply {
                add(Calendar.WEEK_OF_YEAR, -weeksAgo)
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val weekEnd = weekStart.timeInMillis + 7L * 24 * 60 * 60 * 1000
            val count = sessionDates.count { it >= weekStart.timeInMillis && it < weekEnd }
            val label = "${weekStart.get(Calendar.MONTH) + 1}/${weekStart.get(Calendar.DAY_OF_MONTH)}"
            WeekBar(label, count)
        }
}
