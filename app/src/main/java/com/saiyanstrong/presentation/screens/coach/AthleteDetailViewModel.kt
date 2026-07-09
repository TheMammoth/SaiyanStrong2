package com.saiyanstrong.presentation.screens.coach

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.WorkoutSession
import com.saiyanstrong.domain.model.WorkoutTemplate
import com.saiyanstrong.domain.repository.TemplateRepository
import com.saiyanstrong.domain.usecase.GetAthleteHistoryUseCase
import com.saiyanstrong.domain.usecase.PushTemplateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AthleteDetailUiState(
    val isLoading: Boolean = true,
    val sessions: List<WorkoutSession> = emptyList(),
    val error: String? = null,
    val showTemplatePicker: Boolean = false,
    val isPushing: Boolean = false
)

@HiltViewModel
class AthleteDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAthleteHistoryUseCase: GetAthleteHistoryUseCase,
    private val pushTemplateUseCase: PushTemplateUseCase,
    templateRepository: TemplateRepository
) : ViewModel() {

    private val athleteId: String = checkNotNull(savedStateHandle["athleteId"])

    private val _uiState = MutableStateFlow(AthleteDetailUiState())
    val uiState: StateFlow<AthleteDetailUiState> = _uiState.asStateFlow()

    val myTemplates: StateFlow<List<WorkoutTemplate>> = templateRepository.getAllTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            getAthleteHistoryUseCase.execute(athleteId)
                .onSuccess { sessions -> _uiState.update { it.copy(isLoading = false, sessions = sessions) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load history") } }
        }
    }

    fun onShowTemplatePicker() { _uiState.update { it.copy(showTemplatePicker = true) } }
    fun onDismissTemplatePicker() { _uiState.update { it.copy(showTemplatePicker = false) } }

    fun onPushTemplate(template: WorkoutTemplate) {
        if (_uiState.value.isPushing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isPushing = true, showTemplatePicker = false) }
            pushTemplateUseCase.execute(athleteId, template.name, template.exerciseIds)
                .onSuccess { _snackbarEvents.emit("\"${template.name}\" pushed to this athlete") }
                .onFailure { e -> _snackbarEvents.emit(e.message ?: "Couldn't push template") }
            _uiState.update { it.copy(isPushing = false) }
        }
    }
}
