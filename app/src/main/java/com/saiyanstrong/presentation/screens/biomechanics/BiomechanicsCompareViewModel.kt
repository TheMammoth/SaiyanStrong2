package com.saiyanstrong.presentation.screens.biomechanics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.Archetype
import com.saiyanstrong.domain.model.ArchetypeAnimation
import com.saiyanstrong.domain.model.LiftType
import com.saiyanstrong.domain.model.NodePosition
import com.saiyanstrong.domain.usecase.GetArchetypeComparisonUseCase
import com.saiyanstrong.domain.util.StickmanInterpolator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs both compare entry points (spec Screen 4) with one screen/ViewModel — a 2-up compare
 * from the visualizer, or the 4-up "Compare all four" from archetype selection — differing
 * only in how many archetypes the nav route passes in. */
@HiltViewModel
class BiomechanicsCompareViewModel @Inject constructor(
    private val getArchetypeComparisonUseCase: GetArchetypeComparisonUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    data class CompareEntry(
        val archetype: Archetype,
        val nodes: List<NodePosition>
    )

    data class UiState(
        val entries: List<CompareEntry> = emptyList(),
        val isLoading: Boolean = true,
        val sliderProgress: Float = 0f
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val archetypes = checkNotNull(savedStateHandle.get<String>("archetypes"))
        .split(",").map { Archetype.valueOf(it) }
    private val lift = LiftType.valueOf(checkNotNull(savedStateHandle.get<String>("lift")))
    private var animations = emptyList<ArchetypeAnimation>()

    init {
        viewModelScope.launch {
            animations = getArchetypeComparisonUseCase.execute(archetypes, lift)
            _uiState.update { it.copy(isLoading = false) }
            onSliderChanged(0f)
        }
    }

    fun onSliderChanged(progress: Float) {
        val entries = animations.map { animation ->
            CompareEntry(animation.archetype, StickmanInterpolator.interpolate(animation.keyframes, progress))
        }
        _uiState.update { it.copy(entries = entries, sliderProgress = progress) }
    }
}
