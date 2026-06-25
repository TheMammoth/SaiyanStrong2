package com.saiyanstrong.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.PowerLevel
import com.saiyanstrong.domain.usecase.GetEvolutionStageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    getEvolutionStageUseCase: GetEvolutionStageUseCase
) : ViewModel() {

    val powerLevel: StateFlow<PowerLevel?> = getEvolutionStageUseCase.execute()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
