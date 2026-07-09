package com.saiyanstrong.presentation.screens.coach

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.WorkoutSession
import com.saiyanstrong.domain.usecase.GetAthleteHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AthleteDetailUiState(
    val isLoading: Boolean = true,
    val sessions: List<WorkoutSession> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class AthleteDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAthleteHistoryUseCase: GetAthleteHistoryUseCase
) : ViewModel() {

    private val athleteId: String = checkNotNull(savedStateHandle["athleteId"])

    private val _uiState = MutableStateFlow(AthleteDetailUiState())
    val uiState: StateFlow<AthleteDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getAthleteHistoryUseCase.execute(athleteId)
                .onSuccess { sessions -> _uiState.update { it.copy(isLoading = false, sessions = sessions) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load history") } }
        }
    }
}
