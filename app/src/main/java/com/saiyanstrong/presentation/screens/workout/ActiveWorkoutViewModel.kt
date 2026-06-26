package com.saiyanstrong.presentation.screens.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.Exercise
import com.saiyanstrong.domain.model.ExerciseLog
import com.saiyanstrong.domain.repository.ExerciseRepository
import com.saiyanstrong.domain.usecase.CompleteSessionUseCase
import com.saiyanstrong.domain.usecase.LogSetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val REST_DURATION_SECONDS = 90

data class ActiveWorkoutUiState(
    val exerciseLogs: List<ExerciseLog> = emptyList(),
    val availableExercises: List<Exercise> = emptyList(),
    val isExercisePickerVisible: Boolean = false,
    val activeExerciseId: Int? = null,
    val restTimerSecondsRemaining: Int? = null,
    val completedSessionId: Long? = null
)

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val logSetUseCase: LogSetUseCase,
    private val completeSessionUseCase: CompleteSessionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActiveWorkoutUiState())
    val uiState: StateFlow<ActiveWorkoutUiState> = _uiState.asStateFlow()

    private val sessionStartMs = System.currentTimeMillis()
    private var restTimerJob: Job? = null

    init {
        exerciseRepository.getAllExercises()
            .onEach { exercises -> _uiState.update { it.copy(availableExercises = exercises) } }
            .launchIn(viewModelScope)
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
            state.copy(
                exerciseLogs = exerciseLogs,
                isExercisePickerVisible = false,
                activeExerciseId = exercise.id
            )
        }
    }

    fun onLogSet(weightKg: Double, reps: Int, rpe: Float?) {
        val exerciseId = _uiState.value.activeExerciseId ?: return
        _uiState.update { state ->
            val exerciseLogs = state.exerciseLogs.map { log ->
                if (log.exercise.id != exerciseId) log
                else {
                    val setLog = logSetUseCase.execute(
                        setNumber = log.sets.size + 1,
                        weightKg = weightKg,
                        reps = reps,
                        rpe = rpe
                    )
                    log.copy(sets = log.sets + setLog)
                }
            }
            state.copy(exerciseLogs = exerciseLogs, restTimerSecondsRemaining = REST_DURATION_SECONDS)
        }
        startRestTimer()
    }

    fun onSkipRest() {
        restTimerJob?.cancel()
        _uiState.update { it.copy(restTimerSecondsRemaining = null) }
    }

    private fun startRestTimer() {
        restTimerJob?.cancel()
        restTimerJob = viewModelScope.launch {
            for (secondsLeft in REST_DURATION_SECONDS downTo 1) {
                _uiState.update { it.copy(restTimerSecondsRemaining = secondsLeft) }
                delay(1_000)
            }
            _uiState.update { it.copy(restTimerSecondsRemaining = null) }
        }
    }

    fun onFinishWorkout() {
        val exerciseLogs = _uiState.value.exerciseLogs.filter { it.sets.isNotEmpty() }
        if (exerciseLogs.isEmpty()) return
        restTimerJob?.cancel()
        _uiState.update { it.copy(restTimerSecondsRemaining = null) }
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
