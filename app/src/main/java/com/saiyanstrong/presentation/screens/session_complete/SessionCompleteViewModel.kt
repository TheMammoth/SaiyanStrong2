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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionCompleteUiState(
    val session: WorkoutSession? = null,
    val powerLevel: PowerLevel? = null,
    val isLoading: Boolean = true,
    val titleInput: String = "",
    val isDone: Boolean = false,
    val isDeleted: Boolean = false
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

    fun onTitleChange(title: String) {
        _uiState.update { it.copy(titleInput = title) }
    }

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
}
