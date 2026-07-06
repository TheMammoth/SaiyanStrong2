package com.saiyanstrong.presentation.screens.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.Exercise
import com.saiyanstrong.domain.model.ExerciseSetHistory
import com.saiyanstrong.domain.repository.ExerciseRepository
import com.saiyanstrong.domain.repository.SessionRepository
import com.saiyanstrong.domain.usecase.EstimateOneRepMaxUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class E1RmChartPoint(
    val dateMs: Long,
    val e1RmKg: Double
)

data class SessionHistoryGroup(
    val dateMs: Long,
    val sets: List<ExerciseSetHistory>
)

data class ExerciseDetailUiState(
    val exercise: Exercise? = null,
    val bestWeightKg: Double = 0.0,
    val bestE1RmKg: Double = 0.0,
    val totalSets: Int = 0,
    val totalVolumeKg: Double = 0.0,
    val chartPoints: List<E1RmChartPoint> = emptyList(),
    val sessionHistory: List<SessionHistoryGroup> = emptyList()
)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    exerciseRepository: ExerciseRepository,
    sessionRepository: SessionRepository,
    private val estimateOneRepMaxUseCase: EstimateOneRepMaxUseCase
) : ViewModel() {

    private val exerciseId: Int = checkNotNull(savedStateHandle["exerciseId"])

    val uiState: StateFlow<ExerciseDetailUiState> = combine(
        exerciseRepository.getExerciseById(exerciseId),
        sessionRepository.getExerciseHistory(exerciseId)
    ) { exercise, history ->
        buildState(exercise, history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailUiState())

    private fun buildState(exercise: Exercise?, history: List<ExerciseSetHistory>): ExerciseDetailUiState {
        if (history.isEmpty()) return ExerciseDetailUiState(exercise = exercise)

        val bySession = history.groupBy { it.sessionId }
        val chartPoints = bySession.values
            .map { sets ->
                E1RmChartPoint(
                    dateMs = sets.first().dateMs,
                    e1RmKg = sets.maxOf { estimateOneRepMaxUseCase.execute(it.weightKg, it.reps) }
                )
            }
            .sortedBy { it.dateMs }

        val sessionHistory = bySession.values
            .map { sets -> SessionHistoryGroup(dateMs = sets.first().dateMs, sets = sets) }
            .sortedByDescending { it.dateMs }

        return ExerciseDetailUiState(
            exercise = exercise,
            bestWeightKg = history.maxOf { it.weightKg },
            bestE1RmKg = history.maxOf { estimateOneRepMaxUseCase.execute(it.weightKg, it.reps) },
            totalSets = history.size,
            totalVolumeKg = history.sumOf { it.weightKg * it.reps },
            chartPoints = chartPoints,
            sessionHistory = sessionHistory
        )
    }
}
