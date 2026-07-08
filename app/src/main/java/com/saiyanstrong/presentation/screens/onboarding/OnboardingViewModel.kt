package com.saiyanstrong.presentation.screens.onboarding

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.repository.UserRepository
import com.saiyanstrong.domain.usecase.SignInWithGoogleUseCase
import com.saiyanstrong.util.GoogleSignInHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class OnboardingGateState {
    data object Loading : OnboardingGateState()
    data object ShowOnboarding : OnboardingGateState()
    data object SkipToHome : OnboardingGateState()
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val googleSignInHelper: GoogleSignInHelper,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase
) : ViewModel() {

    val isReplay: Boolean = savedStateHandle.get<Boolean>("replay") ?: false

    val gateState: StateFlow<OnboardingGateState> = if (isReplay) {
        MutableStateFlow(OnboardingGateState.ShowOnboarding)
    } else {
        userRepository.getOnboardingComplete().map { complete ->
            if (complete) OnboardingGateState.SkipToHome else OnboardingGateState.ShowOnboarding
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingGateState.Loading)

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _signInError = MutableStateFlow<String?>(null)
    val signInError: StateFlow<String?> = _signInError.asStateFlow()

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    fun onSignInClick(activityContext: Context) {
        if (_isSigningIn.value) return
        viewModelScope.launch {
            _isSigningIn.value = true
            _signInError.value = null
            googleSignInHelper.signIn(activityContext)
                .mapCatching { tokens -> signInWithGoogleUseCase.execute(tokens.idToken, tokens.rawNonce).getOrThrow() }
                .onSuccess { _signedIn.value = true }
                .onFailure { e -> _signInError.value = e.message ?: "Sign-in failed" }
            _isSigningIn.value = false
        }
    }

    fun onFinished() {
        if (!isReplay) {
            viewModelScope.launch { userRepository.setOnboardingComplete(true) }
        }
    }
}
