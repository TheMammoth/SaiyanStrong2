package com.saiyanstrong.presentation.screens.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.CoachLink
import com.saiyanstrong.domain.repository.CoachRepository
import com.saiyanstrong.domain.usecase.GenerateInviteCodeUseCase
import com.saiyanstrong.domain.usecase.IsCoachUseCase
import com.saiyanstrong.domain.usecase.RedeemInviteCodeUseCase
import com.saiyanstrong.domain.usecase.RevokeCoachLinkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoachSettingsUiState(
    val isLoadingStatus: Boolean = true,
    val isCoach: Boolean = false,
    val isGeneratingCode: Boolean = false,
    val generatedCode: String? = null,
    val redeemCodeInput: String = "",
    val isRedeeming: Boolean = false,
    val showConsentDialog: Boolean = false,
    val linkedCoaches: List<CoachLink> = emptyList(),
    val isLoadingLinkedCoaches: Boolean = false
)

@HiltViewModel
class CoachSettingsViewModel @Inject constructor(
    private val isCoachUseCase: IsCoachUseCase,
    private val generateInviteCodeUseCase: GenerateInviteCodeUseCase,
    private val redeemInviteCodeUseCase: RedeemInviteCodeUseCase,
    private val revokeCoachLinkUseCase: RevokeCoachLinkUseCase,
    private val coachRepository: CoachRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachSettingsUiState())
    val uiState: StateFlow<CoachSettingsUiState> = _uiState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            val isCoach = isCoachUseCase.execute()
            _uiState.update { it.copy(isLoadingStatus = false, isCoach = isCoach) }
        }
        refreshLinkedCoaches()
    }

    fun onGenerateInviteCode() {
        if (_uiState.value.isGeneratingCode) return
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingCode = true) }
            generateInviteCodeUseCase.execute()
                .onSuccess { code -> _uiState.update { it.copy(generatedCode = code) } }
                .onFailure { e -> _snackbarEvents.emit(e.message ?: "Couldn't generate a code") }
            _uiState.update { it.copy(isGeneratingCode = false) }
        }
    }

    fun onRedeemCodeInputChange(value: String) {
        _uiState.update { it.copy(redeemCodeInput = value.uppercase().trim()) }
    }

    fun onRequestRedeem() {
        if (_uiState.value.redeemCodeInput.isBlank()) return
        _uiState.update { it.copy(showConsentDialog = true) }
    }

    fun onDismissConsent() {
        _uiState.update { it.copy(showConsentDialog = false) }
    }

    fun onConfirmRedeem() {
        val code = _uiState.value.redeemCodeInput
        viewModelScope.launch {
            _uiState.update { it.copy(isRedeeming = true, showConsentDialog = false) }
            redeemInviteCodeUseCase.execute(code)
                .onSuccess {
                    _snackbarEvents.emit("Linked! Your coach can now see your training history.")
                    _uiState.update { it.copy(redeemCodeInput = "") }
                    refreshLinkedCoaches()
                }
                .onFailure { e -> _snackbarEvents.emit(e.message ?: "Couldn't redeem that code") }
            _uiState.update { it.copy(isRedeeming = false) }
        }
    }

    fun onRevokeLink(linkId: String) {
        viewModelScope.launch {
            revokeCoachLinkUseCase.execute(linkId)
                .onSuccess {
                    _snackbarEvents.emit("Coach access revoked")
                    refreshLinkedCoaches()
                }
                .onFailure { e -> _snackbarEvents.emit(e.message ?: "Couldn't revoke") }
        }
    }

    private fun refreshLinkedCoaches() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLinkedCoaches = true) }
            coachRepository.getLinkedCoaches()
                .onSuccess { list -> _uiState.update { it.copy(linkedCoaches = list) } }
            _uiState.update { it.copy(isLoadingLinkedCoaches = false) }
        }
    }
}
