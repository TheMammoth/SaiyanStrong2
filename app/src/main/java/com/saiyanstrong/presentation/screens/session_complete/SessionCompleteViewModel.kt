package com.saiyanstrong.presentation.screens.session_complete

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.PowerLevel
import com.saiyanstrong.domain.model.SetLog
import com.saiyanstrong.domain.model.WorkoutSession
import com.saiyanstrong.domain.repository.SessionRepository
import com.saiyanstrong.domain.repository.TemplateRepository
import com.saiyanstrong.domain.usecase.DeleteSessionUseCase
import com.saiyanstrong.domain.usecase.GetEvolutionStageUseCase
import com.saiyanstrong.util.SessionShareImageSaver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class SessionCompleteUiState(
    val session: WorkoutSession? = null,
    val powerLevel: PowerLevel? = null,
    val isLoading: Boolean = true,
    val titleInput: String = "",
    val isDone: Boolean = false,
    val isDeleted: Boolean = false,
    val isTemplateSaved: Boolean = false
)

data class ExerciseRow(
    val name: String,
    val bestWeightKg: Double,
    val estOneRmKg: Double,
    val totalReps: Int,
    val totalSets: Int,
    val sets: List<SetLog> = emptyList()
)

@HiltViewModel
class SessionCompleteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val templateRepository: TemplateRepository,
    private val sessionShareImageSaver: SessionShareImageSaver,
    private val deleteSessionUseCase: DeleteSessionUseCase,
    getEvolutionStageUseCase: GetEvolutionStageUseCase
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedStateHandle["sessionId"])

    private val _uiState = MutableStateFlow(SessionCompleteUiState())
    val uiState: StateFlow<SessionCompleteUiState> = _uiState.asStateFlow()

    val exerciseRows: StateFlow<List<ExerciseRow>> = _uiState
        .map { state ->
            state.session?.exerciseLogs?.map { log ->
                val best = log.sets.maxByOrNull { it.weightKg }
                val oneRm = best?.let {
                    if (it.reps == 1) it.weightKg else it.weightKg * (1.0 + it.reps / 30.0)
                } ?: 0.0
                ExerciseRow(
                    name = log.exercise.name,
                    bestWeightKg = best?.weightKg ?: 0.0,
                    estOneRmKg = oneRm,
                    totalReps = log.sets.sumOf { it.reps },
                    totalSets = log.sets.size,
                    sets = log.sets
                )
            } ?: emptyList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        sessionRepository.getSessionById(sessionId)
            .combine(getEvolutionStageUseCase.execute()) { session, powerLevel ->
                val current = _uiState.value
                current.copy(
                    session = session,
                    powerLevel = powerLevel,
                    isLoading = session == null,
                    titleInput = if (current.isLoading) session?.title ?: "" else current.titleInput
                )
            }
            .onEach { state -> _uiState.value = state }
            .launchIn(viewModelScope)
    }

    fun onTitleChange(title: String) { _uiState.update { it.copy(titleInput = title) } }

    fun onDone() {
        viewModelScope.launch {
            sessionRepository.updateTitle(sessionId, _uiState.value.titleInput.trim())
            _uiState.update { it.copy(isDone = true) }
        }
    }

    fun onSaveAsTemplate() {
        val session = _uiState.value.session ?: return
        if (_uiState.value.isTemplateSaved) return
        viewModelScope.launch {
            val name = _uiState.value.titleInput.trim().ifBlank {
                val cal = Calendar.getInstance().apply { timeInMillis = session.dateMs }
                "Workout ${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}"
            }
            templateRepository.saveTemplate(
                name = name,
                exerciseIds = session.exerciseLogs
                    .sortedBy { it.orderIndex }
                    .map { it.exercise.id }
            )
            _uiState.update { it.copy(isTemplateSaved = true) }
        }
    }

    fun onShare(bitmap: Bitmap) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            sessionShareImageSaver.share(bitmap, fileName = "saiyanstrong-session-${session.id}.png")
        }
    }

    fun onDeleteSession() {
        viewModelScope.launch {
            deleteSessionUseCase.execute(sessionId)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }

    fun onEditSet(setLogId: Long, weightKg: Double, reps: Int, isFailure: Boolean) {
        viewModelScope.launch {
            sessionRepository.updateSet(sessionId, setLogId, weightKg, reps, isFailure)
        }
    }

    fun onDeleteSet(setLogId: Long) {
        viewModelScope.launch {
            sessionRepository.deleteSet(sessionId, setLogId)
        }
    }
}
