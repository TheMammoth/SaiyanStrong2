package com.saiyanstrong.presentation.screens.home

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.BuildConfig
import com.saiyanstrong.domain.model.AppUpdate
import com.saiyanstrong.domain.model.PowerLevel
import com.saiyanstrong.domain.repository.SessionRepository
import com.saiyanstrong.domain.usecase.CheckForUpdateUseCase
import com.saiyanstrong.domain.usecase.GetEvolutionStageUseCase
import com.saiyanstrong.util.UpdateInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

data class WeekBar(val label: String, val count: Int)

sealed class UpdateDownloadState {
    data object Idle : UpdateDownloadState()
    data object Downloading : UpdateDownloadState()
    data class Ready(val uri: Uri) : UpdateDownloadState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    getEvolutionStageUseCase: GetEvolutionStageUseCase,
    sessionRepository: SessionRepository,
    private val checkForUpdateUseCase: CheckForUpdateUseCase,
    private val updateInstaller: UpdateInstaller
) : ViewModel() {

    val powerLevel: StateFlow<PowerLevel?> = getEvolutionStageUseCase.execute()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val weeklyBars: StateFlow<List<WeekBar>> = sessionRepository.getAllSessions()
        .map { sessions -> buildWeekBars(sessions.map { it.dateMs }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _updateAvailable = MutableStateFlow<AppUpdate?>(null)
    val updateAvailable: StateFlow<AppUpdate?> = _updateAvailable.asStateFlow()

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    init {
        checkForUpdate()
    }

    fun canInstallPackages(): Boolean = updateInstaller.canInstallPackages()

    fun onDownloadUpdate() {
        val update = _updateAvailable.value ?: return
        if (_downloadState.value is UpdateDownloadState.Downloading) return
        _downloadState.value = UpdateDownloadState.Downloading
        viewModelScope.launch {
            val downloadId = updateInstaller.startDownload(update)
            pollUntilDone(downloadId)
        }
    }

    fun onDismissUpdate() { _updateAvailable.value = null }

    fun onInstallConsumed() { _downloadState.value = UpdateDownloadState.Idle }

    private fun checkForUpdate() {
        viewModelScope.launch {
            _updateAvailable.value = checkForUpdateUseCase.execute(BuildConfig.VERSION_NAME)
        }
    }

    private suspend fun pollUntilDone(downloadId: Long) = withContext(Dispatchers.IO) {
        while (true) {
            delay(500)
            when (updateInstaller.queryStatus(downloadId)) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val uri = updateInstaller.getDownloadedUri(downloadId)
                    _downloadState.value =
                        if (uri != null) UpdateDownloadState.Ready(uri)
                        else UpdateDownloadState.Idle
                    break
                }
                DownloadManager.STATUS_FAILED, -1 -> {
                    _downloadState.value = UpdateDownloadState.Idle
                    break
                }
            }
        }
    }

    private fun buildWeekBars(sessionDates: List<Long>): List<WeekBar> =
        (7 downTo 0).map { weeksAgo ->
            val weekStart = Calendar.getInstance().apply {
                add(Calendar.WEEK_OF_YEAR, -weeksAgo)
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val weekEnd = weekStart.timeInMillis + 7L * 24 * 60 * 60 * 1000
            val count = sessionDates.count { it >= weekStart.timeInMillis && it < weekEnd }
            val label = "${weekStart.get(Calendar.MONTH) + 1}/${weekStart.get(Calendar.DAY_OF_MONTH)}"
            WeekBar(label, count)
        }
}
