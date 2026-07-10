package com.saiyanstrong.presentation.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.BuildConfig
import com.saiyanstrong.domain.model.AppUpdate
import com.saiyanstrong.domain.model.AuthUser
import com.saiyanstrong.domain.model.BackupInfo
import com.saiyanstrong.domain.repository.AuthRepository
import com.saiyanstrong.domain.repository.BackupRepository
import com.saiyanstrong.domain.repository.CoachRepository
import com.saiyanstrong.domain.repository.UserRepository
import com.saiyanstrong.domain.usecase.BackupNowUseCase
import com.saiyanstrong.domain.usecase.CheckForUpdateUseCase
import com.saiyanstrong.domain.usecase.IsCoachUseCase
import com.saiyanstrong.domain.usecase.RestoreBackupUseCase
import com.saiyanstrong.domain.usecase.SignInWithGoogleUseCase
import com.saiyanstrong.domain.usecase.SignOutUseCase
import com.saiyanstrong.util.GoogleSignInHelper
import com.saiyanstrong.util.UpdateInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoachStatusUiState(
    val isLoading: Boolean = false,
    val isCoach: Boolean = false,
    val entitlementExpiresAtMs: Long? = null
)

sealed class UpdateCheckState {
    data object Idle : UpdateCheckState()
    data object Checking : UpdateCheckState()
    data class UpToDate(val version: String) : UpdateCheckState()
    data class UpdateAvailable(val update: AppUpdate) : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}

sealed class DownloadState {
    data object Idle : DownloadState()
    data class InProgress(val percent: Int) : DownloadState()
    data class Ready(val uri: Uri) : DownloadState()
    data class Failed(val reason: String) : DownloadState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val checkForUpdateUseCase: CheckForUpdateUseCase,
    private val updateInstaller: UpdateInstaller,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val backupRepository: BackupRepository,
    private val googleSignInHelper: GoogleSignInHelper,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val backupNowUseCase: BackupNowUseCase,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val coachRepository: CoachRepository,
    private val isCoachUseCase: IsCoachUseCase
) : ViewModel() {

    val authUser: StateFlow<AuthUser?> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _coachStatus = MutableStateFlow(CoachStatusUiState())
    val coachStatus: StateFlow<CoachStatusUiState> = _coachStatus.asStateFlow()

    init {
        // Re-check coach status whenever sign-in state changes (including sign-out, which
        // resets to "not a coach" since there's no signed-in user to check anymore).
        authUser
            .map { it?.id }
            .distinctUntilChanged()
            .onEach { userId -> if (userId != null) refreshCoachStatus() else _coachStatus.value = CoachStatusUiState() }
            .launchIn(viewModelScope)
    }

    fun refreshCoachStatus() {
        viewModelScope.launch {
            _coachStatus.update { it.copy(isLoading = true) }
            val isCoach = isCoachUseCase.execute()
            val expiresAt = if (isCoach) coachRepository.getProfile().getOrNull()?.coachEntitlementExpiresAtMs else null
            _coachStatus.value = CoachStatusUiState(isLoading = false, isCoach = isCoach, entitlementExpiresAtMs = expiresAt)
        }
    }

    val backupInfo: StateFlow<BackupInfo?> = backupRepository.lastBackupInfo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    fun onSignInClick(activityContext: Context) {
        if (_isSigningIn.value) return
        viewModelScope.launch {
            _isSigningIn.value = true
            val result = googleSignInHelper.signIn(activityContext)
                .mapCatching { tokens -> signInWithGoogleUseCase.execute(tokens.idToken, tokens.rawNonce).getOrThrow() }
            result.onFailure { e -> _snackbarEvents.emit(e.message ?: "Sign-in failed") }
            _isSigningIn.value = false
        }
    }

    fun onSignOut() {
        viewModelScope.launch { signOutUseCase.execute() }
    }

    fun onBackupNow() {
        if (_isBackingUp.value) return
        viewModelScope.launch {
            _isBackingUp.value = true
            backupNowUseCase.execute()
                .onFailure { e -> _snackbarEvents.emit(e.message ?: "Backup failed") }
                .onSuccess { _snackbarEvents.emit("Backup complete") }
            _isBackingUp.value = false
        }
    }

    fun onRestoreBackup() {
        if (_isRestoring.value) return
        viewModelScope.launch {
            _isRestoring.value = true
            restoreBackupUseCase.execute()
                .onFailure { e -> _snackbarEvents.emit(e.message ?: "Restore failed") }
                .onSuccess { _snackbarEvents.emit("Restore complete") }
            _isRestoring.value = false
        }
    }

    val currentVersion: String = BuildConfig.VERSION_NAME
    val currentVersionCode: Int = BuildConfig.VERSION_CODE

    val useFemaleDotsFormula: StateFlow<Boolean> = userRepository.getUseFemaleDotsFormula()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onToggleDotsFormula(useFemale: Boolean) {
        viewModelScope.launch { userRepository.setUseFemaleDotsFormula(useFemale) }
    }

    val defaultRestSeconds: StateFlow<Int> = userRepository.getDefaultRestSeconds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 90)

    fun onAdjustDefaultRest(deltaSeconds: Int) {
        viewModelScope.launch {
            userRepository.setDefaultRestSeconds(defaultRestSeconds.value + deltaSeconds)
        }
    }

    val restTimerSoundsEnabled: StateFlow<Boolean> = userRepository.getRestTimerSoundsEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun onToggleRestTimerSounds(enabled: Boolean) {
        viewModelScope.launch { userRepository.setRestTimerSoundsEnabled(enabled) }
    }

    private val _checkState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val checkState: StateFlow<UpdateCheckState> = _checkState.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    fun checkForUpdate() {
        if (_checkState.value is UpdateCheckState.Checking) return
        viewModelScope.launch {
            _checkState.value = UpdateCheckState.Checking
            try {
                val result = checkForUpdateUseCase.execute(currentVersion)
                _checkState.value = if (result != null)
                    UpdateCheckState.UpdateAvailable(result)
                else
                    UpdateCheckState.UpToDate(currentVersion)
            } catch (e: Exception) {
                _checkState.value = UpdateCheckState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun downloadUpdate(update: AppUpdate) {
        if (_downloadState.value is DownloadState.InProgress) return
        _downloadState.value = DownloadState.InProgress(0)
        viewModelScope.launch {
            val uri = updateInstaller.downloadToCache(update) { percent ->
                _downloadState.value = DownloadState.InProgress(percent)
            }
            _downloadState.value =
                if (uri != null) DownloadState.Ready(uri)
                else DownloadState.Failed("Download failed — check connection and retry")
        }
    }

    fun canInstallPackages(): Boolean = updateInstaller.canInstallPackages()

    fun consumeInstall() { _downloadState.value = DownloadState.Idle }
}
