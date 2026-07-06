package com.saiyanstrong.presentation.screens.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.WorkoutTemplate
import com.saiyanstrong.domain.repository.SessionRepository
import com.saiyanstrong.domain.repository.TemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LastSessionPreview(
    val title: String,
    val exerciseNames: List<String>
)

@HiltViewModel
class WorkoutLandingViewModel @Inject constructor(
    private val templateRepository: TemplateRepository,
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

    fun onDeleteTemplate(templateId: Long) {
        viewModelScope.launch { templateRepository.deleteTemplate(templateId) }
    }
}
