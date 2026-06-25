package com.saiyanstrong.presentation.screens.visualizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.Exercise
import com.saiyanstrong.domain.model.SetLog
import com.saiyanstrong.domain.usecase.CalculatePowerLevelUseCase
import com.saiyanstrong.domain.usecase.EstimateOneRepMaxUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val POSE_FRAME_DELAY_MS = 600L
private const val POSE_COUNT = 3

@HiltViewModel
class VisualizerViewModel @Inject constructor(
    private val calculatePowerLevelUseCase: CalculatePowerLevelUseCase,
    private val estimateOneRepMaxUseCase: EstimateOneRepMaxUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<VisualizerState>(VisualizerState.Idle)
    val state: StateFlow<VisualizerState> = _state.asStateFlow()

    private var poseAnimationJob: Job? = null

    fun onExerciseSelected(exercise: Exercise) {
        poseAnimationJob?.cancel()
        _state.value = VisualizerState.Static(
            exercise = exercise,
            highlightedMuscles = exercise.primaryMuscles + exercise.secondaryMuscles
        )
    }

    fun onBeginSet() {
        val current = _state.value
        if (current !is VisualizerState.Static) return
        _state.value = VisualizerState.DynamicTransition(exercise = current.exercise)
        poseAnimationJob?.cancel()
        poseAnimationJob = viewModelScope.launch {
            var poseIndex = 0
            while (isActive) {
                delay(POSE_FRAME_DELAY_MS)
                val transitionState = _state.value as? VisualizerState.DynamicTransition ?: break
                poseIndex = (poseIndex + 1) % POSE_COUNT
                _state.value = transitionState.copy(poseIndex = poseIndex)
            }
        }
    }

    fun onSetLogged(weightKg: Double, reps: Int) {
        val current = _state.value
        if (current !is VisualizerState.DynamicTransition) return
        poseAnimationJob?.cancel()
        val setLog = SetLog(setNumber = 1, weightKg = weightKg, reps = reps)
        _state.value = VisualizerState.FullActivation(
            exercise = current.exercise,
            powerLevelGained = calculatePowerLevelUseCase.sessionPowerGained(listOf(setLog)),
            estimatedOneRmKg = estimateOneRepMaxUseCase.execute(weightKg, reps)
        )
    }

    fun onNextSet() {
        val current = _state.value
        if (current !is VisualizerState.FullActivation) return
        _state.value = VisualizerState.Static(
            exercise = current.exercise,
            highlightedMuscles = current.exercise.primaryMuscles + current.exercise.secondaryMuscles
        )
    }
}
