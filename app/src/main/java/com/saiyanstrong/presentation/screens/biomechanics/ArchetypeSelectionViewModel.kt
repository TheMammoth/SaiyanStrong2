package com.saiyanstrong.presentation.screens.biomechanics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.Archetype
import com.saiyanstrong.domain.model.ArchetypeInfo
import com.saiyanstrong.domain.model.BiomechanicsPhase
import com.saiyanstrong.domain.model.LiftType
import com.saiyanstrong.domain.model.NodePosition
import com.saiyanstrong.domain.repository.UserRepository
import com.saiyanstrong.domain.usecase.GetArchetypeAnimationUseCase
import com.saiyanstrong.domain.usecase.GetArchetypeInfoListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [UserRepository] is called directly for the two simple pass-through prefs (selected
 * archetype, disclaimer-shown) rather than through a dedicated use case each — same precedent
 * as [com.saiyanstrong.presentation.screens.session_complete.SessionCompleteViewModel]'s
 * onTitleChange/updateTitle. */
@HiltViewModel
class ArchetypeSelectionViewModel @Inject constructor(
    private val getArchetypeInfoListUseCase: GetArchetypeInfoListUseCase,
    private val getArchetypeAnimationUseCase: GetArchetypeAnimationUseCase,
    private val userRepository: UserRepository
) : ViewModel() {

    data class UiState(
        val archetypes: List<ArchetypeInfo> = emptyList(),
        val standingNodes: Map<Archetype, List<NodePosition>> = emptyMap(),
        val selectedArchetype: Archetype = Archetype.PROPORTIONAL,
        val isLoading: Boolean = true,
        val showDisclaimer: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val infos = getArchetypeInfoListUseCase.execute()
            val standing = Archetype.entries.associateWith { archetype ->
                val animation = getArchetypeAnimationUseCase.execute(archetype, LiftType.SQUAT)
                animation.keyframes.first { it.phase == BiomechanicsPhase.STANDING }.nodes
            }
            _uiState.update { it.copy(archetypes = infos, standingNodes = standing, isLoading = false) }
        }
        viewModelScope.launch {
            userRepository.getSelectedArchetype().collect { archetype ->
                _uiState.update { it.copy(selectedArchetype = archetype) }
            }
        }
        viewModelScope.launch {
            val alreadyShown = userRepository.getBiomechanicsDisclaimerShown().first()
            if (!alreadyShown) _uiState.update { it.copy(showDisclaimer = true) }
        }
    }

    fun onArchetypeSelected(archetype: Archetype) {
        viewModelScope.launch { userRepository.setSelectedArchetype(archetype) }
    }

    fun onDisclaimerDismissed() {
        viewModelScope.launch { userRepository.setBiomechanicsDisclaimerShown(true) }
        _uiState.update { it.copy(showDisclaimer = false) }
    }
}
