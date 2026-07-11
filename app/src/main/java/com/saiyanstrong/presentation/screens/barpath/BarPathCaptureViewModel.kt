package com.saiyanstrong.presentation.screens.barpath

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.BarPathAnalysis
import com.saiyanstrong.domain.model.BarPathSample
import com.saiyanstrong.domain.model.TrackedFrame
import com.saiyanstrong.domain.repository.BarPathRepository
import com.saiyanstrong.domain.repository.ExerciseRepository
import com.saiyanstrong.domain.repository.UserRepository
import com.saiyanstrong.domain.usecase.AnalyzeBarPathUseCase
import com.saiyanstrong.domain.util.GyroTimeline
import com.saiyanstrong.domain.util.LiftPhase
import com.saiyanstrong.util.SessionShareImageSaver
import com.saiyanstrong.util.barpath.BarPathFrameTracker
import com.saiyanstrong.util.barpath.BarPathVideoImporter
import com.saiyanstrong.util.barpath.LiveFrameResult
import com.saiyanstrong.util.barpath.MarkerColorProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
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
    /** True (the recommended default): two-marker calibration, per-frame depth-drift
     * correction. False: the original manual tap-two-known-distance-points calibration. */
    val useDualMarkerMode: Boolean = true,
    val markerSamplePoint: TapPoint? = null,
    val colorProfile: MarkerColorProfile? = null,
    /** Dual-marker mode only — the reference marker, a known real-world distance from A. */
    val markerBSamplePoint: TapPoint? = null,
    val colorProfileB: MarkerColorProfile? = null,
    val referenceDistanceCm: String = "130",
    val calibrationPoint1: TapPoint? = null,
    val calibrationPoint2: TapPoint? = null,
    val referenceLengthCm: String = "45",
    val weightKgInput: String = "",
    val errorMessage: String? = null,
    val analysis: BarPathAnalysis? = null,
    val trackedSamples: List<BarPathSample> = emptyList(),
    // Per-frame path + video dimensions for the (ephemeral, in-session) replay overlay.
    val trackedFrames: List<TrackedFrame> = emptyList(),
    val videoWidthPx: Int = 0,
    val videoHeightPx: Int = 0,
    val showReplay: Boolean = false,
    val isSaved: Boolean = false,
    /** True while importing/reading the video before the calibration frame is ready — the
     * recording/picker UI otherwise gives no feedback during this gap. */
    val isPreparingVideo: Boolean = false,
    val gyroTimeline: GyroTimeline? = null,
    val focalMm: Double = 0.0,
    val sensorWidthMm: Double = 0.0,
    val videoStartUptimeNs: Long = 0L
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
    private val userRepository: UserRepository,
    private val exerciseRepository: ExerciseRepository,
    private val sessionShareImageSaver: SessionShareImageSaver
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

    /** Null = user hasn't explicitly chosen — the screen defaults this to "on if supported." */
    val highSpeedModeEnabled: StateFlow<Boolean?> = userRepository.getHighSpeedModeEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onHighSpeedModeChanged(enabled: Boolean) {
        viewModelScope.launch { userRepository.setHighSpeedModeEnabled(enabled) }
    }

    // ── Live analysis loop (slice 1) ────────────────────────────────────────────
    // Fed per-frame from BarPathLiveAnalyzer during recording. The velocity is uncalibrated
    // (no pixels-per-meter until post-record calibration) — a relative speed, not true m/s.
    private val _liveTracking = MutableStateFlow(false)
    val liveTracking: StateFlow<Boolean> = _liveTracking.asStateFlow()

    private val _liveVelocity = MutableStateFlow(0f)
    val liveVelocity: StateFlow<Float> = _liveVelocity.asStateFlow()

    private val _livePhase = MutableStateFlow(LiftPhase.IDLE)
    val livePhase: StateFlow<LiftPhase> = _livePhase.asStateFlow()

    private val _liveColorProfile = MutableStateFlow<MarkerColorProfile?>(null)
    val liveColorProfile: StateFlow<MarkerColorProfile?> = _liveColorProfile.asStateFlow()

    /** True once a live tap-to-sample has locked onto a marker color; drives the RE-TAP button
     * and gates further taps (avoids an accidental re-sample mid-recording). */
    private val _liveColorLockedOn = MutableStateFlow(false)
    val liveColorLockedOn: StateFlow<Boolean> = _liveColorLockedOn.asStateFlow()

    /** The gyroscope reading at the moment of the tap that produced [liveColorProfile] — kept for
     * the capture session so "was the phone stable when this was sampled?" can be correlated with
     * tracking quality afterward. NOTE: in-memory only, cleared on RE-TAP/process death — this is
     * NOT written to Room. True cross-session correlation would need a persisted column; out of
     * scope here (the color profile itself isn't persisted either — it only configures the live
     * tracker at runtime, so persisting just the stability reading without it would be a half
     * measure). Also logged via Log.i("BarPathColorSample", ...) in BarPathLiveAnalyzer for
     * immediate logcat inspection. */
    private val _liveColorSampleAngularVelocity = MutableStateFlow<Float?>(null)
    val liveColorSampleAngularVelocity: StateFlow<Float?> = _liveColorSampleAngularVelocity.asStateFlow()

    /** Called on the analyzer's background thread — MutableStateFlow.value is safe cross-thread. */
    fun onLiveResult(result: LiveFrameResult) {
        _liveTracking.value = result.markerDetected
        _liveVelocity.value = result.smoothedVelocityMps
        _livePhase.value = result.phase
        result.sampledColorProfile?.let {
            _liveColorProfile.value = it
            _liveColorLockedOn.value = true
            _liveColorSampleAngularVelocity.value = result.sampledAngularVelocityMagnitude
        }
    }

    /** RE-TAP pressed — re-arms the tap-catcher so the next tap samples a new color. */
    fun onRetapColor() {
        _liveColorLockedOn.value = false
        _liveColorSampleAngularVelocity.value = null
    }

    fun onRecordingFinished(path: String?, gyroTimeline: GyroTimeline?, focalMm: Double, sensorWidthMm: Double, videoStartUptimeNs: Long) {
        if (path == null) {
            _uiState.update { it.copy(step = CaptureStep.ERROR, errorMessage = "Recording failed — try again.") }
            return
        }
        _uiState.update { it.copy(
            gyroTimeline = gyroTimeline, 
            focalMm = focalMm, 
            sensorWidthMm = sensorWidthMm, 
            videoStartUptimeNs = videoStartUptimeNs
        ) }
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
                state.useDualMarkerMode -> {
                    // Second (or re-)tap always sets marker B in dual-marker mode -- there's
                    // nothing else to tap once both markers are placed.
                    val profile = sampleMarkerColor(state.calibrationFrame, point)
                    state.copy(markerBSamplePoint = point, colorProfileB = profile)
                }
                state.calibrationPoint1 == null -> state.copy(calibrationPoint1 = point)
                state.calibrationPoint2 == null -> state.copy(calibrationPoint2 = point)
                else -> state.copy(calibrationPoint1 = point, calibrationPoint2 = null)
            }
        }
    }

    /** Switching modes changes what every tap means, so all existing taps are cleared. */
    fun onDualMarkerModeChanged(enabled: Boolean) {
        _uiState.update {
            it.copy(
                useDualMarkerMode = enabled,
                markerSamplePoint = null, colorProfile = null,
                markerBSamplePoint = null, colorProfileB = null,
                calibrationPoint1 = null, calibrationPoint2 = null,
                errorMessage = null
            )
        }
    }

    fun onReferenceDistanceChanged(cm: String) {
        _uiState.update { it.copy(referenceDistanceCm = cm) }
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
            it.copy(
                markerSamplePoint = null, colorProfile = null,
                markerBSamplePoint = null, colorProfileB = null,
                calibrationPoint1 = null, calibrationPoint2 = null
            )
        }
    }

    fun onReferenceLengthChanged(cm: String) {
        _uiState.update { it.copy(referenceLengthCm = cm) }
    }

    fun onConfirmCalibration() {
        val state = _uiState.value
        if (state.useDualMarkerMode) onConfirmDualMarkerCalibration(state) else onConfirmManualCalibration(state)
    }

    private fun onConfirmManualCalibration(state: BarPathCaptureUiState) {
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
            val samples = barPathFrameTracker.trackMarker(
                videoPath = videoPath,
                colorProfile = colorProfile,
                gyroTimeline = state.gyroTimeline,
                focalMm = state.focalMm,
                sensorWidthMm = state.sensorWidthMm,
                videoStartUptimeNs = state.videoStartUptimeNs
            )
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
            val frames = analyzeBarPathUseCase.trackFrames(samples, pixelsPerMeter, samples.first().timestampMs, samples.last().timestampMs)
            val (vw, vh) = barPathFrameTracker.videoDimensions(videoPath)
            _uiState.update {
                it.copy(
                    step = CaptureStep.RESULTS, analysis = analysis, trackedSamples = samples,
                    trackedFrames = frames, videoWidthPx = vw, videoHeightPx = vh
                )
            }
        }
    }

    private fun onConfirmDualMarkerCalibration(state: BarPathCaptureUiState) {
        val colorProfileA = state.colorProfile
        val colorProfileB = state.colorProfileB
        val referenceCm = state.referenceDistanceCm.toDoubleOrNull()
        val videoPath = state.videoPath
        val massKg = if (isStandalone) state.weightKgInput.toDoubleOrNull() else knownWeightKg

        if (colorProfileA == null) {
            _uiState.update { it.copy(errorMessage = "Tap the primary marker on the bar in this frame first.") }
            return
        }
        if (colorProfileB == null) {
            _uiState.update { it.copy(errorMessage = "Tap the second reference marker on the bar.") }
            return
        }
        if (referenceCm == null || referenceCm <= 0.0) {
            _uiState.update { it.copy(errorMessage = "Enter a valid distance between the two markers in cm.") }
            return
        }
        if (isStandalone && (massKg == null || massKg <= 0.0)) {
            _uiState.update { it.copy(errorMessage = "Enter the weight lifted in this video (kg).") }
            return
        }
        if (videoPath == null) return

        val referenceDistanceMeters = referenceCm / 100.0

        _uiState.update { it.copy(step = CaptureStep.PROCESSING, errorMessage = null) }
        viewModelScope.launch(Dispatchers.Default) {
            val samples = barPathFrameTracker.trackMarkerPair(
                videoPath = videoPath,
                primaryColorProfile = colorProfileA,
                referenceColorProfile = colorProfileB,
                referenceDistanceMeters = referenceDistanceMeters,
                gyroTimeline = state.gyroTimeline,
                focalMm = state.focalMm,
                sensorWidthMm = state.sensorWidthMm,
                videoStartUptimeNs = state.videoStartUptimeNs
            )
            if (samples.size < 2) {
                _uiState.update {
                    it.copy(
                        step = CaptureStep.ERROR,
                        errorMessage = "Couldn't track both markers across enough frames — check they're " +
                            "visible and brightly lit against the background, then try again."
                    )
                }
                return@launch
            }
            // Safe fallback if perFramePixelsPerMeter somehow never populated (e.g. the reference
            // marker was never found even once) — AnalyzeBarPathUseCase's own pixelsPerMeter<=0.0
            // guard handles total failure by returning a zeroed result rather than crashing.
            val fallbackPixelsPerMeter = samples.firstNotNullOfOrNull { it.perFramePixelsPerMeter } ?: 0.0
            val analysis = analyzeBarPathUseCase.execute(
                samples = samples,
                pixelsPerMeter = fallbackPixelsPerMeter,
                massKg = massKg ?: 0.0,
                concentricStartMs = samples.first().timestampMs,
                concentricEndMs = samples.last().timestampMs
            )
            val frames = analyzeBarPathUseCase.trackFrames(samples, fallbackPixelsPerMeter, samples.first().timestampMs, samples.last().timestampMs)
            val (vw, vh) = barPathFrameTracker.videoDimensions(videoPath)
            _uiState.update {
                it.copy(
                    step = CaptureStep.RESULTS, analysis = analysis, trackedSamples = samples,
                    trackedFrames = frames, videoWidthPx = vw, videoHeightPx = vh
                )
            }
        }
    }

    fun onShowReplay() { _uiState.update { it.copy(showReplay = true) } }
    fun onHideReplay() { _uiState.update { it.copy(showReplay = false) } }

    /** Generate the shareable rep card (Canvas drawing on Dispatchers.Default) and open the share
     * sheet via the existing image-share util (cache PNG + FileProvider + ACTION_SEND). */
    fun onShareRep() {
        val state = _uiState.value
        val analysis = state.analysis ?: return
        val frames = state.trackedFrames
        if (frames.isEmpty()) return
        val weightKg = if (isStandalone) state.weightKgInput.toDoubleOrNull() ?: 0.0 else knownWeightKg
        viewModelScope.launch(Dispatchers.Default) {
            val exerciseName = runCatching {
                exerciseRepository.getExerciseById(exerciseId).firstOrNull()?.name
            }.getOrNull() ?: ""
            val tutSeconds = (frames.last().timestampMs - frames.first().timestampMs) / 1000.0
            val bitmap = RepCardGenerator.generateRepCard(
                RepCardData(
                    frames = frames,
                    exerciseName = exerciseName,
                    weightKg = weightKg,
                    meanVelocityMps = analysis.meanConcentricVelocityMs,
                    peakVelocityMps = analysis.peakVelocityMs,
                    romMeters = analysis.rangeOfMotionCm / 100.0,
                    tutSeconds = tutSeconds,
                    dateMs = System.currentTimeMillis()
                )
            )
            sessionShareImageSaver.share(bitmap, "saiyanstrong-rep.png")
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
