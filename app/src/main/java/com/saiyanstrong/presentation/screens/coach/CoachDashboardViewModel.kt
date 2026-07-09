package com.saiyanstrong.presentation.screens.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.AthleteSummary
import com.saiyanstrong.domain.usecase.GetAthleteSummariesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoachDashboardUiState(
    val isLoading: Boolean = true,
    val athletes: List<AthleteSummary> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class CoachDashboardViewModel @Inject constructor(
    private val getAthleteSummariesUseCase: GetAthleteSummariesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachDashboardUiState())
    val uiState: StateFlow<CoachDashboardUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            getAthleteSummariesUseCase.execute()
                .onSuccess { athletes ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            athletes = athletes.sortedWith(
                                compareByDescending<AthleteSummary> { a -> a.isStale }
                                    .thenByDescending { a -> a.lastSessionDateMs ?: 0L }
                            )
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load athletes") } }
        }
    }
}
