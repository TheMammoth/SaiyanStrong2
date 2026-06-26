package com.saiyanstrong.presentation.screens.session_complete

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.PowerLevel
import com.saiyanstrong.domain.model.WorkoutSession
import com.saiyanstrong.domain.repository.SessionRepository
import com.saiyanstrong.domain.usecase.GetEvolutionStageUseCase
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
    val isDeleted: Boolean = false
)

data class ExerciseRow(
    val name: String,
    val bestWeightKg: Double,
    val estOneRmKg: Double,
    val totalReps: Int,
    val totalSets: Int
)

@HiltViewModel
class SessionCompleteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    getEvolutionStageUseCase: GetEvolutionStageUseCase
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedStateHandle["sessionId"])

    private val _uiState = MutableStateFlow(SessionCompleteUiState())
    val uiState: StateFlow<SessionCompleteUiState> = _uiState.asStateFlow()

    private val allSessions = sessionRepository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weeklyBars: StateFlow<List<Pair<String, Int>>> = allSessions
        .map { sessions -> buildWeekBars(sessions.map { it.dateMs }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val strengthProgressPct: StateFlow<Float> = weeklyBars
        .map { bars ->
            if (bars.size < 2) 0f
            else {
                val last = bars.last().second.toFloat()
                val prev = bars[bars.size - 2].second.toFloat()
                if (prev == 0f) 0f else (last - prev) / prev * 100f
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

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
                    totalSets = log.sets.size
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

    fun onDeleteSession() {
        viewModelScope.launch {
            sessionRepository.deleteSession(sessionId)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }

    private fun buildWeekBars(sessionDates: List<Long>): List<Pair<String, Int>> =
        (7 downTo 0).map { weeksAgo ->
            val cal = Calendar.getInstance().apply {
                add(Calendar.WEEK_OF_YEAR, -weeksAgo)
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val weekEnd = cal.timeInMillis + 7L * 24 * 60 * 60 * 1000
            val count = sessionDates.count { it >= cal.timeInMillis && it < weekEnd }
            val label = "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}"
            Pair(label, count)
        }
}
