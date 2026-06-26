package com.saiyanstrong.presentation.screens.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.Exercise
import com.saiyanstrong.domain.model.ExerciseLog
import com.saiyanstrong.domain.model.SetLog
import com.saiyanstrong.domain.repository.ExerciseRepository
import com.saiyanstrong.domain.usecase.CompleteSessionUseCase
import com.saiyanstrong.domain.usecase.GetLastSessionSetsUseCase
import com.saiyanstrong.domain.usecase.LogSetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val REST_DURATION_SECONDS = 90

data class ActiveWorkoutUiState(
    val exerciseLogs: List<ExerciseLog> = emptyList(),
    val availableExercises: List<Exercise> = emptyList(),
    val exerciseUsageCounts: Map<Int, Int> = emptyMap(),
    val previousPerformance: Map<Int, List<SetLog>> = emptyMap(),
    val pendingSetCounts: Map<Int, Int> = emptyMap(),  // exerciseId → visible pending row count
    val restTimerForExerciseId: Int? = null,
    val restTimerSecondsRemaining: Int? = null,
    val isExercisePickerVisible: Boolean = false,
    val completedSessionId: Long? = null
)

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val logSetUseCase: LogSetUseCase,
    private val completeSessionUseCase: CompleteSessionUseCase,
    private val getLastSessionSetsUseCase: GetLastSessionSetsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActiveWorkoutUiState())
    val uiState: StateFlow<ActiveWorkoutUiState> = _uiState.asStateFlow()

    private val sessionStartMs = System.currentTimeMillis()
    private var restTimerJob: Job? = null

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    val restDurationSeconds: Int = REST_DURATION_SECONDS

    init {
        combine(
            exerciseRepository.getAllExercises(),
            exerciseRepository.getExerciseUsageCounts()
        ) { exercises, usageCounts ->
            _uiState.update { it.copy(availableExercises = exercises, exerciseUsageCounts = usageCounts) }
        }.launchIn(viewModelScope)

        viewModelScope.launch {
            while (true) {
                delay(1000)
                _elapsedSeconds.value = ((System.currentTimeMillis() - sessionStartMs) / 1000).toInt()
            }
        }
    }

    fun onAddExerciseClicked() {
        _uiState.update { it.copy(isExercisePickerVisible = true) }
    }

    fun onExercisePickerDismissed() {
        _uiState.update { it.copy(isExercisePickerVisible = false) }
    }

    fun onExerciseSelected(exercise: Exercise) {
        _uiState.update { state ->
            val alreadyAdded = state.exerciseLogs.any { it.exercise.id == exercise.id }
            val exerciseLogs = if (alreadyAdded) state.exerciseLogs else {
                state.exerciseLogs + ExerciseLog(
                    exercise = exercise,
                    sets = emptyList(),
                    orderIndex = state.exerciseLogs.size
                )
            }
            val pending = state.pendingSetCounts.toMutableMap()
            if (!alreadyAdded) pending[exercise.id] = 1  // start with one pending row
            state.copy(
                exerciseLogs = exerciseLogs,
                isExercisePickerVisible = false,
                pendingSetCounts = pending
            )
        }
        viewModelScope.launch {
            val prevSets = getLastSessionSetsUseCase.execute(exercise.id)
            _uiState.update { it.copy(previousPerformance = it.previousPerformance + (exercise.id to prevSets)) }
        }
    }

    fun onAddSetClicked(exerciseId: Int) {
        _uiState.update { state ->
            val count = (state.pendingSetCounts[exerciseId] ?: 0) + 1
            state.copy(pendingSetCounts = state.pendingSetCounts + (exerciseId to count))
        }
    }

    fun onLogSet(exerciseId: Int, weightKg: Double, reps: Int, rpe: Float?, isFailure: Boolean = false) {
        _uiState.update { state ->
            val exerciseLogs = state.exerciseLogs.map { log ->
                if (log.exercise.id != exerciseId) log
                else {
                    val setLog = logSetUseCase.execute(
                        setNumber = log.sets.size + 1,
                        weightKg = weightKg,
                        reps = reps,
                        rpe = rpe,
                        isFailure = isFailure
                    )
                    log.copy(sets = log.sets + setLog)
                }
            }
            val prevCount = state.pendingSetCounts[exerciseId] ?: 1
            val newCount = (prevCount - 1).coerceAtLeast(0)
            state.copy(
                exerciseLogs = exerciseLogs,
                pendingSetCounts = state.pendingSetCounts + (exerciseId to newCount),
                restTimerForExerciseId = exerciseId,
                restTimerSecondsRemaining = REST_DURATION_SECONDS
            )
        }
        startRestTimer()
    }

    fun onEditSet(exerciseId: Int, setIndex: Int, weightKg: Double, reps: Int, isFailure: Boolean) {
        _uiState.update { state ->
            val exerciseLogs = state.exerciseLogs.map { log ->
                if (log.exercise.id != exerciseId) log
                else {
                    val updated = log.sets.mapIndexed { i, s ->
                        if (i == setIndex) s.copy(weightKg = weightKg, reps = reps, isFailure = isFailure, volumeKg = weightKg * reps)
                        else s
                    }
                    log.copy(sets = updated)
                }
            }
            state.copy(exerciseLogs = exerciseLogs)
        }
    }

    fun onDeleteSet(exerciseId: Int, setIndex: Int) {
        _uiState.update { state ->
            val exerciseLogs = state.exerciseLogs.map { log ->
                if (log.exercise.id != exerciseId) log
                else {
                    val newSets = log.sets.toMutableList()
                        .also { it.removeAt(setIndex) }
                        .mapIndexed { i, s -> s.copy(setNumber = i + 1) }
                    log.copy(sets = newSets)
                }
            }
            state.copy(exerciseLogs = exerciseLogs)
        }
    }

    fun onSkipRest() {
        restTimerJob?.cancel()
        _uiState.update { it.copy(restTimerSecondsRemaining = null, restTimerForExerciseId = null) }
    }

    fun onAdjustRestTimer(deltaSec: Int) {
        val current = _uiState.value.restTimerSecondsRemaining ?: return
        startRestTimerFrom((current + deltaSec).coerceIn(10, 600))
    }

    private fun startRestTimer() = startRestTimerFrom(REST_DURATION_SECONDS)

    private fun startRestTimerFrom(seconds: Int) {
        restTimerJob?.cancel()
        restTimerJob = viewModelScope.launch {
            for (secondsLeft in seconds downTo 1) {
                _uiState.update { it.copy(restTimerSecondsRemaining = secondsLeft) }
                delay(1_000)
            }
            _uiState.update { it.copy(restTimerSecondsRemaining = null, restTimerForExerciseId = null) }
        }
    }

    fun onFinishWorkout() {
        val exerciseLogs = _uiState.value.exerciseLogs.filter { it.sets.isNotEmpty() }
        if (exerciseLogs.isEmpty()) return
        restTimerJob?.cancel()
        _uiState.update { it.copy(restTimerSecondsRemaining = null, restTimerForExerciseId = null) }
        viewModelScope.launch {
            val session = completeSessionUseCase.execute(
                dateMs = sessionStartMs,
                durationMs = System.currentTimeMillis() - sessionStartMs,
                exerciseLogs = exerciseLogs
            )
            _uiState.update { it.copy(completedSessionId = session.id) }
        }
    }
}
