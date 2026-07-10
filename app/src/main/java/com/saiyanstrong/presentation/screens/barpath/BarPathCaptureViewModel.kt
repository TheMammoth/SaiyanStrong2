package com.saiyanstrong.presentation.screens.barpath

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.BarPathAnalysis
import com.saiyanstrong.domain.repository.BarPathRepository
import com.saiyanstrong.domain.usecase.AnalyzeBarPathUseCase
import com.saiyanstrong.util.barpath.BarPathFrameTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.hypot

enum class CaptureStep { RECORDING, CALIBRATING, PROCESSING, RESULTS, ERROR }

data class TapPoint(val xPx: Float, val yPx: Float)

data class BarPathCaptureUiState(
    val step: CaptureStep = CaptureStep.RECORDING,
    val videoPath: String? = null,
    val calibrationFrame: Bitmap? = null,
    val calibrationPoint1: TapPoint? = null,
    val calibrationPoint2: TapPoint? = null,
    val referenceLengthCm: String = "45",
    val errorMessage: String? = null,
    val analysis: BarPathAnalysis? = null,
    val isSaved: Boolean = false
)

/**
 * Orchestrates the whole record → calibrate → track → analyze → save flow. MVP scope: one rep
 * per recording (the whole clip is treated as a single concentric phase) — automatic multi-rep
 * segmentation is a documented future enhancement, not built here (see SPEC.md §8).
 */
@HiltViewModel
class BarPathCaptureViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val barPathFrameTracker: BarPathFrameTracker,
    private val analyzeBarPathUseCase: AnalyzeBarPathUseCase,
    private val barPathRepository: BarPathRepository
) : ViewModel() {

    private val setLogId: Long = checkNotNull(savedStateHandle["setLogId"])
    private val weightKg: Double = (savedStateHandle.get<Float>("weightKg") ?: 0f).toDouble()

    private val _uiState = MutableStateFlow(BarPathCaptureUiState())
    val uiState: StateFlow<BarPathCaptureUiState> = _uiState.asStateFlow()

    fun onRecordingFinished(path: String?) {
        if (path == null) {
            _uiState.update { it.copy(step = CaptureStep.ERROR, errorMessage = "Recording failed — try again.") }
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            val frame = runCatching { barPathFrameTracker.extractFirstFrame(path) }.getOrNull()
            _uiState.update {
                if (frame != null) {
                    it.copy(step = CaptureStep.CALIBRATING, videoPath = path, calibrationFrame = frame)
                } else {
                    it.copy(step = CaptureStep.ERROR, errorMessage = "Couldn't read the recorded video.")
                }
            }
        }
    }

    fun onCalibrationTap(point: TapPoint) {
        _uiState.update { state ->
            when {
                state.calibrationPoint1 == null -> state.copy(calibrationPoint1 = point)
                state.calibrationPoint2 == null -> state.copy(calibrationPoint2 = point)
                else -> state.copy(calibrationPoint1 = point, calibrationPoint2 = null)
            }
        }
    }

    fun onResetCalibrationPoints() {
        _uiState.update { it.copy(calibrationPoint1 = null, calibrationPoint2 = null) }
    }

    fun onReferenceLengthChanged(cm: String) {
        _uiState.update { it.copy(referenceLengthCm = cm) }
    }

    fun onConfirmCalibration() {
        val state = _uiState.value
        val p1 = state.calibrationPoint1
        val p2 = state.calibrationPoint2
        val referenceCm = state.referenceLengthCm.toDoubleOrNull()
        val videoPath = state.videoPath

        if (p1 == null || p2 == null) {
            _uiState.update { it.copy(errorMessage = "Tap two points on a reference object of known length first.") }
            return
        }
        if (referenceCm == null || referenceCm <= 0.0) {
            _uiState.update { it.copy(errorMessage = "Enter a valid reference length in cm.") }
            return
        }
        if (videoPath == null) return

        val pixelDistance = hypot((p2.xPx - p1.xPx).toDouble(), (p2.yPx - p1.yPx).toDouble())
        val pixelsPerMeter = pixelDistance / (referenceCm / 100.0)

        _uiState.update { it.copy(step = CaptureStep.PROCESSING, errorMessage = null) }
        viewModelScope.launch(Dispatchers.Default) {
            val samples = barPathFrameTracker.trackMarker(videoPath)
            if (samples.size < 2) {
                _uiState.update {
                    it.copy(
                        step = CaptureStep.ERROR,
                        errorMessage = "Couldn't track the marker across enough frames — check it's " +
                            "visible and brightly lit against the background, then try again."
                    )
                }
                return@launch
            }
            val analysis = analyzeBarPathUseCase.execute(
                samples = samples,
                pixelsPerMeter = pixelsPerMeter,
                massKg = weightKg,
                concentricStartMs = samples.first().timestampMs,
                concentricEndMs = samples.last().timestampMs
            )
            _uiState.update { it.copy(step = CaptureStep.RESULTS, analysis = analysis) }
        }
    }

    fun onSave() {
        val analysis = _uiState.value.analysis ?: return
        viewModelScope.launch {
            barPathRepository.saveBarPathMetrics(setLogId, analysis)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun onRetry() {
        _uiState.value = BarPathCaptureUiState()
    }
}
