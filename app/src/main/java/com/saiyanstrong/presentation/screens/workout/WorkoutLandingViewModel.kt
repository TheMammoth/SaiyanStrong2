package com.saiyanstrong.presentation.screens.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.PushedTemplate
import com.saiyanstrong.domain.model.WorkoutTemplate
import com.saiyanstrong.domain.repository.SessionRepository
import com.saiyanstrong.domain.repository.TemplateRepository
import com.saiyanstrong.domain.usecase.AcceptPushedTemplateUseCase
import com.saiyanstrong.domain.usecase.GetPendingPushedTemplatesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LastSessionPreview(
    val title: String,
    val exerciseNames: List<String>
)

@HiltViewModel
class WorkoutLandingViewModel @Inject constructor(
    private val templateRepository: TemplateRepository,
    private val getPendingPushedTemplatesUseCase: GetPendingPushedTemplatesUseCase,
    private val acceptPushedTemplateUseCase: AcceptPushedTemplateUseCase,
    sessionRepository: SessionRepository
) : ViewModel() {

    val templates: StateFlow<List<WorkoutTemplate>> = templateRepository.getAllTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lastSession: StateFlow<LastSessionPreview?> = sessionRepository.getAllSessions()
        .map { sessions ->
            sessions.firstOrNull()?.let { session ->
                LastSessionPreview(
                    title = session.title.ifBlank { "Last workout" },
                    exerciseNames = session.exerciseLogs
                        .sortedBy { it.orderIndex }
                        .map { it.exercise.name }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _pendingPushedTemplates = MutableStateFlow<List<PushedTemplate>>(emptyList())
    val pendingPushedTemplates: StateFlow<List<PushedTemplate>> = _pendingPushedTemplates.asStateFlow()

    private val _isAcceptingTemplateId = MutableStateFlow<String?>(null)
    val isAcceptingTemplateId: StateFlow<String?> = _isAcceptingTemplateId.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    init { refreshPendingPushedTemplates() }

    private fun refreshPendingPushedTemplates() {
        viewModelScope.launch {
            // Non-critical, non-blocking: no signed-in user or a network failure just means
            // no banner, not an error state for the rest of this screen.
            getPendingPushedTemplatesUseCase.execute()
                .onSuccess { pending -> _pendingPushedTemplates.value = pending }
        }
    }

    fun onDeleteTemplate(templateId: Long) {
        viewModelScope.launch { templateRepository.deleteTemplate(templateId) }
    }

    fun onAcceptPushedTemplate(pushedTemplate: PushedTemplate) {
        if (_isAcceptingTemplateId.value != null) return
        viewModelScope.launch {
            _isAcceptingTemplateId.value = pushedTemplate.id
            acceptPushedTemplateUseCase.execute(pushedTemplate)
                .onSuccess {
                    _snackbarEvents.emit("\"${pushedTemplate.name}\" added to your templates")
                    _pendingPushedTemplates.update { list -> list.filterNot { it.id == pushedTemplate.id } }
                }
                .onFailure { e -> _snackbarEvents.emit(e.message ?: "Couldn't accept template") }
            _isAcceptingTemplateId.value = null
        }
    }
}
