package com.saiyanstrong.presentation.screens.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.Exercise
import com.saiyanstrong.domain.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ExerciseBrowserViewModel @Inject constructor(
    exerciseRepository: ExerciseRepository
) : ViewModel() {

    val exercises: StateFlow<List<Exercise>> = exerciseRepository.getAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val usageCounts: StateFlow<Map<Int, Int>> = exerciseRepository.getExerciseUsageCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}
