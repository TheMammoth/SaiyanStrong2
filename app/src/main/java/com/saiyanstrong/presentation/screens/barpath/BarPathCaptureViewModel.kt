package com.saiyanstrong.presentation.screens.barpath

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.BarPathAnalysis
import com.saiyanstrong.domain.model.BarPathSample
import com.saiyanstrong.domain.repository.BarPathRepository
import com.saiyanstrong.domain.repository.UserRepository
import com.saiyanstrong.domain.usecase.AnalyzeBarPathUseCase
import com.saiyanstrong.util.barpath.BarPathFrameTracker
import com.saiyanstrong.util.barpath.BarPathVideoImporter
import com.saiyanstrong.util.barpath.MarkerColorProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
    val markerSamplePoint: TapPoint? = null,
    val colorProfile: MarkerColorProfile? = null,
    val calibrationPoint1: TapPoint? = null,
    val calibrationPoint2: TapPoint? = null,
    val referenceLengthCm: String = "45",
    val weightKgInput: String = "",
    val errorMessage: String? = null,
    val analysis: BarPathAnalysis? = null,
    val trackedSamples: List<BarPathSample> = emptyList(),
    val isSaved: Boolean = false,
    /** True while importing/reading the video before the calibration frame is ready — the
     * recording/picker UI otherwise gives no feedback during this gap. */
    val isPreparingVideo: Boolean = false
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
    private val barPathVideoImporter: BarPathVideoImporter,
    private val analyzeBarPathUseCase: AnalyzeBarPathUseCase,
    private val barPathRepository: BarPathRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val exerciseId: Int = checkNotNull(savedStateHandle["exerciseId"])
    private val setLogId: Long = savedStateHandle.get<Long>("setLogId") ?: -1L
    private val knownWeightKg: Double = (savedStateHandle.get<Float>("weightKg") ?: -1f).toDouble()

    /** No logged set to attach to — user picked an exercise, not a set, from the Home card. */
    val isStandalone: Boolean = setLogId <= 0L

    private val _uiState = MutableStateFlow(BarPathCaptureUiState())
    val uiState: StateFlow<BarPathCaptureUiState> = _uiState.asStateFlow()

    val tipsDismissed: StateFlow<Boolean> = userRepository.getBarPathTipsDismissed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun onDismissTips() {
        viewModelScope.launch { userRepository.setBarPathTipsDismissed(true) }
    }

    fun onRecordingFinished(path: String?) {
        if (path == null) {
            _uiState.update { it.copy(step = CaptureStep.ERROR, errorMessage = "Recording failed — try again.") }
            return
        }
        loadCalibrationFrame(path, failureMessage = "Couldn't read the recorded video.")
    }

    fun onGalleryVideoPicked(uri: Uri) {
        _uiState.update { it.copy(isPreparingVideo = true) }
        viewModelScope.launch(Dispatchers.Default) {
            val path = barPathVideoImporter.importFromGallery(uri)
            if (path == null) {
                _uiState.update {
                    it.copy(step = CaptureStep.ERROR, isPreparingVideo = false, errorMessage = "Couldn't import that video — try again.")
                }
            } else {
                loadCalibrationFrame(path, failureMessage = "Couldn't read the imported video.")
            }
        }
    }

    private fun loadCalibrationFrame(path: String, failureMessage: String) {
        _uiState.update { it.copy(isPreparingVideo = true) }
        viewModelScope.launch(Dispatchers.Default) {
            val frame = runCatching { barPathFrameTracker.extractFirstFrame(path) }.getOrNull()
            _uiState.update {
                if (frame != null) {
                    it.copy(step = CaptureStep.CALIBRATING, videoPath = path, calibrationFrame = frame, isPreparingVideo = false)
                } else {
                    it.copy(step = CaptureStep.ERROR, isPreparingVideo = false, errorMessage = failureMessage)
                }
            }
        }
    }

    fun onWeightKgChanged(kg: String) {
        _uiState.update { it.copy(weightKgInput = kg) }
    }

    fun onCalibrationTap(point: TapPoint) {
        _uiState.update { state ->
            when {
                state.markerSamplePoint == null -> {
                    val profile = sampleMarkerColor(state.calibrationFrame, point)
                    state.copy(markerSamplePoint = point, colorProfile = profile)
                }
                state.calibrationPoint1 == null -> state.copy(calibrationPoint1 = point)
                state.calibrationPoint2 == null -> state.copy(calibrationPoint2 = point)
                else -> state.copy(calibrationPoint1 = point, calibrationPoint2 = null)
            }
        }
    }

    /** Averages a small pixel neighborhood around the tap to reduce single-pixel noise. */
    private fun sampleMarkerColor(frame: Bitmap?, point: TapPoint, radius: Int = 2): MarkerColorProfile? {
        if (frame == null) return null
        val cx = point.xPx.toInt().coerceIn(0, frame.width - 1)
        val cy = point.yPx.toInt().coerceIn(0, frame.height - 1)
        var sumR = 0L; var sumG = 0L; var sumB = 0L; var count = 0
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val x = cx + dx
                val y = cy + dy
                if (x in 0 until frame.width && y in 0 until frame.height) {
                    val pixel = frame.getPixel(x, y)
                    sumR += android.graphics.Color.red(pixel)
                    sumG += android.graphics.Color.green(pixel)
                    sumB += android.graphics.Color.blue(pixel)
                    count++
                }
            }
        }
        if (count == 0) return null
        return MarkerColorProfile.sample((sumR / count).toInt(), (sumG / count).toInt(), (sumB / count).toInt())
    }

    fun onResetCalibrationPoints() {
        _uiState.update {
            it.copy(markerSamplePoint = null, colorProfile = null, calibrationPoint1 = null, calibrationPoint2 = null)
        }
    }

    fun onReferenceLengthChanged(cm: String) {
        _uiState.update { it.copy(referenceLengthCm = cm) }
    }

    fun onConfirmCalibration() {
        val state = _uiState.value
        val colorProfile = state.colorProfile
        val p1 = state.calibrationPoint1
        val p2 = state.calibrationPoint2
        val referenceCm = state.referenceLengthCm.toDoubleOrNull()
        val videoPath = state.videoPath
        val massKg = if (isStandalone) state.weightKgInput.toDoubleOrNull() else knownWeightKg

        if (colorProfile == null) {
            _uiState.update { it.copy(errorMessage = "Tap the marker on the bar in this frame first.") }
            return
        }
        if (p1 == null || p2 == null) {
            _uiState.update { it.copy(errorMessage = "Tap two points on a reference object of known length.") }
            return
        }
        if (referenceCm == null || referenceCm <= 0.0) {
            _uiState.update { it.copy(errorMessage = "Enter a valid reference length in cm.") }
            return
        }
        if (isStandalone && (massKg == null || massKg <= 0.0)) {
            _uiState.update { it.copy(errorMessage = "Enter the weight lifted in this video (kg).") }
            return
        }
        if (videoPath == null) return

        val pixelDistance = hypot((p2.xPx - p1.xPx).toDouble(), (p2.yPx - p1.yPx).toDouble())
        val pixelsPerMeter = pixelDistance / (referenceCm / 100.0)

        _uiState.update { it.copy(step = CaptureStep.PROCESSING, errorMessage = null) }
        viewModelScope.launch(Dispatchers.Default) {
            val samples = barPathFrameTracker.trackMarker(videoPath, colorProfile)
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
                massKg = massKg ?: 0.0,
                concentricStartMs = samples.first().timestampMs,
                concentricEndMs = samples.last().timestampMs
            )
            _uiState.update { it.copy(step = CaptureStep.RESULTS, analysis = analysis, trackedSamples = samples) }
        }
    }

    fun onSave() {
        val analysis = _uiState.value.analysis ?: return
        viewModelScope.launch {
            if (isStandalone) {
                barPathRepository.saveFreestandingBarPathMetrics(exerciseId, analysis)
            } else {
                barPathRepository.saveBarPathMetrics(setLogId, exerciseId, analysis)
            }
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun onRetry() {
        _uiState.value = BarPathCaptureUiState()
    }
}
