package com.saiyanstrong.presentation.screens.biomechanics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.Archetype
import com.saiyanstrong.domain.model.LiftType
import com.saiyanstrong.domain.model.LimbRatios
import com.saiyanstrong.domain.model.NodePosition
import com.saiyanstrong.domain.model.StickmanKeyframe
import com.saiyanstrong.domain.repository.UserRepository
import com.saiyanstrong.domain.usecase.GetArchetypeAnimationUseCase
import com.saiyanstrong.domain.util.StickmanInterpolator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [UserRepository] called directly for the ratio get/set — same simple-pass-through precedent
 * as [ArchetypeSelectionViewModel]'s selected-archetype/disclaimer calls. Pose angles come from
 * the PROPORTIONAL archetype's keyframes (via [GetArchetypeAnimationUseCase], which already
 * knows how to resolve CUSTOM — but here we want the template directly, not a swapped-in ratio
 * set, so this always asks for PROPORTIONAL explicitly). */
@HiltViewModel
class CustomProportionsViewModel @Inject constructor(
    private val getArchetypeAnimationUseCase: GetArchetypeAnimationUseCase,
    private val userRepository: UserRepository
) : ViewModel() {

    data class UiState(
        val ratios: LimbRatios = DEFAULT_RATIOS,
        val nodes: List<NodePosition> = emptyList(),
        val sliderProgress: Float = INITIAL_PROGRESS,
        val isLoading: Boolean = true,
        val justSaved: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var keyframes: List<StickmanKeyframe> = emptyList()

    init {
        viewModelScope.launch {
            val template = getArchetypeAnimationUseCase.execute(Archetype.PROPORTIONAL, LiftType.SQUAT)
            keyframes = template.keyframes
            val savedRatios = userRepository.getCustomLimbRatios().first()
            _uiState.update { it.copy(ratios = savedRatios, isLoading = false) }
            recompute(savedRatios, INITIAL_PROGRESS)
        }
    }

    private fun recompute(ratios: LimbRatios, progress: Float) {
        val nodes = StickmanInterpolator.interpolate(keyframes, ratios, progress)
        _uiState.update { it.copy(ratios = ratios, nodes = nodes, sliderProgress = progress, justSaved = false) }
    }

    fun onScrubChanged(progress: Float) {
        recompute(_uiState.value.ratios, progress)
    }

    fun onThighRatioChanged(value: Float) = updateRatios { it.copy(thighRatio = value) }
    fun onShankRatioChanged(value: Float) = updateRatios { it.copy(shankRatio = value) }
    fun onTorsoRatioChanged(value: Float) = updateRatios { it.copy(torsoRatio = value) }
    fun onShoulderHalfRatioChanged(value: Float) = updateRatios { it.copy(shoulderHalfRatio = value) }
    fun onHipHalfRatioChanged(value: Float) = updateRatios { it.copy(hipHalfRatio = value) }
    fun onFootLenRatioChanged(value: Float) = updateRatios { it.copy(footLenRatio = value) }

    private inline fun updateRatios(transform: (LimbRatios) -> LimbRatios) {
        val newRatios = transform(_uiState.value.ratios)
        recompute(newRatios, _uiState.value.sliderProgress)
    }

    fun onSave() {
        viewModelScope.launch {
            userRepository.setCustomLimbRatios(_uiState.value.ratios)
            _uiState.update { it.copy(justSaved = true) }
        }
    }

    companion object {
        /** Not 0f (standing): at standing, torso lean is near-vertical and thighLean sits at
         * ~0° regardless of thighRatio's value, so dragging the thigh slider only moves the hip
         * vertically — the torso angle and bar height barely change, making the slider feel
         * inert. Starting mid/deep-descent means every ratio's effect on torso position and bar
         * height is visible immediately, without the user having to scrub first. */
        private const val INITIAL_PROGRESS = 0.65f

        val DEFAULT_RATIOS = LimbRatios(
            thighRatio = 0.230f, shankRatio = 0.270f, torsoRatio = 0.29f, headNeckRatio = 0.16f,
            footLenRatio = 0.10f, shoulderHalfRatio = 0.090f, hipHalfRatio = 0.070f,
            kneeHalfRatio = 0.050f, ankleHalfRatio = 0.045f, barRiseRatio = 0.04f, gripHalfRatio = 0.12f
        )
    }
}
