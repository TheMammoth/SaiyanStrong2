package com.saiyanstrong.presentation.screens.settings

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.BuildConfig
import com.saiyanstrong.domain.model.AppUpdate
import com.saiyanstrong.domain.usecase.CheckForUpdateUseCase
import com.saiyanstrong.util.UpdateInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class UpdateCheckState {
    data object Idle : UpdateCheckState()
    data object Checking : UpdateCheckState()
    data class UpToDate(val version: String) : UpdateCheckState()
    data class UpdateAvailable(val update: AppUpdate) : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}

sealed class DownloadState {
    data object Idle : DownloadState()
    data object InProgress : DownloadState()
    data class Ready(val uri: Uri) : DownloadState()
    data class Failed(val reason: String) : DownloadState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val checkForUpdateUseCase: CheckForUpdateUseCase,
    private val updateInstaller: UpdateInstaller
) : ViewModel() {

    val currentVersion: String = BuildConfig.VERSION_NAME
    val currentVersionCode: Int = BuildConfig.VERSION_CODE

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
        _downloadState.value = DownloadState.InProgress
        viewModelScope.launch {
            val downloadId = updateInstaller.startDownload(update)
            pollUntilDone(downloadId)
        }
    }

    fun canInstallPackages(): Boolean = updateInstaller.canInstallPackages()

    fun consumeInstall() { _downloadState.value = DownloadState.Idle }

    private suspend fun pollUntilDone(downloadId: Long) = withContext(Dispatchers.IO) {
        while (true) {
            delay(500)
            when (updateInstaller.queryStatus(downloadId)) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val uri = updateInstaller.getDownloadedUri(downloadId)
                    _downloadState.value =
                        if (uri != null) DownloadState.Ready(uri)
                        else DownloadState.Failed("Could not get download URI")
                    break
                }
                DownloadManager.STATUS_FAILED, -1 -> {
                    _downloadState.value = DownloadState.Failed("Download failed")
                    break
                }
            }
        }
    }
}
