package com.saiyanstrong.presentation.screens.biomechanics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.Archetype
import com.saiyanstrong.domain.model.LiftType
import com.saiyanstrong.domain.model.LimbRatios
import com.saiyanstrong.domain.model.NodePosition
import com.saiyanstrong.domain.model.StickmanKeyframe
import com.saiyanstrong.domain.usecase.GetArchetypeAnimationUseCase
import com.saiyanstrong.domain.util.StickmanInterpolator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BiomechanicsVisualizerViewModel @Inject constructor(
    private val getArchetypeAnimationUseCase: GetArchetypeAnimationUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** A labelled marker under the rep-timeline slider. [fraction] is 0..1 along the track. */
    data class PhaseTick(val fraction: Float, val label: String)

    data class UiState(
        val nodes: List<NodePosition> = emptyList(),
        val archetypeName: String = "",
        val liftName: String = "",
        val mechanicalFacts: List<String> = emptyList(),
        val stanceCue: String = "",
        val irrelevantCue: String = "",
        val isLoading: Boolean = true,
        val sliderProgress: Float = 0f,
        val phaseTicks: List<PhaseTick> = emptyList()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val archetype = Archetype.valueOf(checkNotNull(savedStateHandle.get<String>("archetype")))
    private val lift = LiftType.valueOf(checkNotNull(savedStateHandle.get<String>("lift")))
    private var keyframes = emptyList<StickmanKeyframe>()
    private var ratios: LimbRatios? = null

    init {
        viewModelScope.launch {
            val animation = getArchetypeAnimationUseCase.execute(archetype, lift)
            keyframes = animation.keyframes
            ratios = animation.limbRatios
            _uiState.update {
                it.copy(
                    archetypeName = archetype.displayName(),
                    liftName = lift.displayName(),
                    mechanicalFacts = animation.mechanicalFacts,
                    stanceCue = animation.stanceCue,
                    irrelevantCue = animation.irrelevantCue,
                    phaseTicks = buildPhaseTicks(animation.keyframes),
                    isLoading = false
                )
            }
            onSliderChanged(0f)
        }
    }

    fun onSliderChanged(progress: Float) {
        val currentRatios = ratios ?: return
        val nodes = StickmanInterpolator.interpolate(keyframes, currentRatios, progress)
        _uiState.update { it.copy(nodes = nodes, sliderProgress = progress) }
    }

    private fun buildPhaseTicks(keyframes: List<StickmanKeyframe>): List<PhaseTick> {
        if (keyframes.size < 2) return emptyList()
        val lastIndex = keyframes.size - 1
        return keyframes.mapIndexedNotNull { index, keyframe ->
            phaseLabel(keyframe.phase)?.let {
                PhaseTick(fraction = index.toFloat() / lastIndex, label = it)
            }
        }
    }

    /** Short label per phase; unlabelled phases produce no tick (keeps the track uncrowded). */
    private fun phaseLabel(phase: com.saiyanstrong.domain.model.BiomechanicsPhase): String? = when (phase) {
        com.saiyanstrong.domain.model.BiomechanicsPhase.STANDING -> "STAND"
        com.saiyanstrong.domain.model.BiomechanicsPhase.PARALLEL -> "PARALLEL"
        com.saiyanstrong.domain.model.BiomechanicsPhase.BOTTOM -> "BOTTOM"
        com.saiyanstrong.domain.model.BiomechanicsPhase.ASCENT_STICK -> "GRIND"
        else -> null
    }
}

/** Placeholder display strings — Archetype/LiftType stay plain enums (JSON-serializable,
 * no UI concerns baked in); [com.saiyanstrong.domain.model.ArchetypeInfo.name] is the real
 * source of truth for archetype display names elsewhere (card labels), but the visualizer's
 * SavedStateHandle only carries the enum, so this small mapping covers the title bar. */
fun Archetype.displayName(): String = when (this) {
    Archetype.LONG_FEMUR -> "Long Femur"
    Archetype.SHORT_FEMUR -> "Short Femur"
    Archetype.PROPORTIONAL -> "Proportional"
    Archetype.WIDE_HIP -> "Wide Hip"
    Archetype.CUSTOM -> "Custom"
}

fun LiftType.displayName(): String = when (this) {
    LiftType.SQUAT -> "Squat"
    LiftType.DEADLIFT -> "Deadlift"
}
