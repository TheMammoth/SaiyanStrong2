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
import javax.inject.Inject

data class SessionCompleteUiState(
    val session: WorkoutSession? = null,
    val powerLevel: PowerLevel? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class SessionCompleteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    sessionRepository: SessionRepository,
    getEvolutionStageUseCase: GetEvolutionStageUseCase
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedStateHandle["sessionId"])

    private val _uiState = MutableStateFlow(SessionCompleteUiState())
    val uiState: StateFlow<SessionCompleteUiState> = _uiState.asStateFlow()

    init {
        sessionRepository.getSessionById(sessionId)
            .combine(getEvolutionStageUseCase.execute()) { session, powerLevel ->
                SessionCompleteUiState(session = session, powerLevel = powerLevel, isLoading = false)
            }
            .onEach { state -> _uiState.value = state }
            .launchIn(viewModelScope)
    }
}
