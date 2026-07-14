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
import com.saiyanstrong.domain.util.ConcentricDetector
import com.saiyanstrong.domain.util.GyroTimeline
import com.saiyanstrong.domain.util.LiftPhase
import com.saiyanstrong.domain.util.LockOnTracker
import com.saiyanstrong.domain.util.ReticleState
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
    val markerSamplePoint: TapPoint? = null,
    val colorProfile: MarkerColorProfile? = null,
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

    // ── Lock-on reticle (post-tap, pre-lift) ────────────────────────────────────
    // Distinct from LiftPhase: this is a detection-QUALITY axis ("is the blob real and stable?"),
    // driven by LockOnTracker, not the rep-TIMING axis LiftPhaseDetector already owns. Only
    // meaningful once liveColorLockedOn is true — before a tap there's no profile to detect
    // against, so lockOnTracker isn't fed at all pre-tap.
    private val lockOnTracker = LockOnTracker()

    private val _reticleState = MutableStateFlow(ReticleState.SEARCHING)
    val reticleState: StateFlow<ReticleState> = _reticleState.asStateFlow()

    private val _reticleConfidence = MutableStateFlow(0f)
    val reticleConfidence: StateFlow<Float> = _reticleConfidence.asStateFlow()

    /** The live marker's position in analysis-frame pixel space, plus that frame's own
     * dimensions — the UI turns this into a 0..1 fraction before scaling onto the preview box.
     * Null whenever the marker isn't currently detected (SEARCHING, or a within-tolerance miss
     * while LOCKED — the UI keeps drawing at the last known position rather than snapping to 0,0). */
    data class LiveMarkerFrame(
        val xPx: Float,
        val yPx: Float,
        val diameterPx: Float,
        val frameWidthPx: Int,
        val frameHeightPx: Int
    )

    private val _liveMarkerFrame = MutableStateFlow<LiveMarkerFrame?>(null)
    val liveMarkerFrame: StateFlow<LiveMarkerFrame?> = _liveMarkerFrame.asStateFlow()

    /** One-shot signal: lock was held, then lost for 10+ consecutive frames. Sticky until the
     * user acknowledges it via RE-TAP (which also calls [onRetapColor], clearing this). */
    private val _lockLost = MutableStateFlow(false)
    val lockLost: StateFlow<Boolean> = _lockLost.asStateFlow()

    /** Called on the analyzer's background thread — MutableStateFlow.value is safe cross-thread. */
    fun onLiveResult(result: LiveFrameResult) {
        _liveTracking.value = result.markerDetected
        _liveVelocity.value = result.smoothedVelocityMps
        _livePhase.value = result.phase
        result.sampledColorProfile?.let {
            _liveColorProfile.value = it
            _liveColorLockedOn.value = true
            _liveColorSampleAngularVelocity.value = result.sampledAngularVelocityMagnitude
            lockOnTracker.reset()
            _reticleState.value = ReticleState.SEARCHING
            _reticleConfidence.value = 0f
            _lockLost.value = false
        }
        if (_liveColorLockedOn.value) {
            val update = lockOnTracker.update(result.markerDetected, result.blobDiameterPx?.toDouble())
            _reticleState.value = update.state
            _reticleConfidence.value = update.confidence
            if (update.justLostLock) _lockLost.value = true
            if (result.markerDetected) {
                _liveMarkerFrame.value = LiveMarkerFrame(
                    result.xPx, result.yPx, result.blobDiameterPx ?: 0f,
                    result.frameWidthPx, result.frameHeightPx
                )
            }
            accumulateLiveRepFrame(result)
            if (result.repJustCompleted) {
                _currentRepSummary.value = summarizeCurrentRep()
                clearCurrentRepAccumulation()
            }
        }
    }

    /** RE-TAP pressed — re-arms the tap-catcher so the next tap samples a new color. */
    fun onRetapColor() {
        _liveColorLockedOn.value = false
        _liveColorSampleAngularVelocity.value = null
        _liveMarkerFrame.value = null
        _lockLost.value = false
        lockOnTracker.reset()
        _reticleState.value = ReticleState.SEARCHING
        _reticleConfidence.value = 0f
        clearCurrentRepAccumulation()
        _currentRepSummary.value = null
        _liveTrailPoints.value = emptyList()
    }

    // ── Continuous live rep session ──────────────────────────────────────────────
    // Auto-detects each subsequent rep once locked on, without requiring another tap — the
    // ColorProfile persists in BarPathLiveAnalyzer for the whole session already (Sprint 39/44);
    // LiftPhaseDetector already cycles COMPLETE->READY on its own (Sprint 40), so "next rep, no
    // retap" falls out of infrastructure that already existed. What's genuinely new here is
    // accumulating + summarizing each rep's live frames.
    //
    // DELIBERATE SCOPE (per explicit choice over the alternative of adding inline scale
    // calibration or a full segmented-recording pipeline): this is UNCALIBRATED. The live
    // analyzer has never had a real pixels-per-meter scale — that only ever comes from post-hoc
    // calibration on a completed recording. So a live rep's "velocity" here is a RELATIVE number,
    // not real m/s, and is deliberately NEVER written into BarPathAnalysis/bar_path_metrics
    // (Room) — that table's fields (peakVelocityMs, etc.) are real physical units consumed
    // elsewhere as real physics (ExerciseDetailScreen's "BAR SPEED" chart, RepCardGenerator's
    // share card). Writing fake numbers into real-labeled fields would silently corrupt those.
    // Instead, live-session reps are kept in-memory only, for this screen's lifetime.
    data class LiveRepSummary(
        val meanVelocityRel: Double,
        val peakVelocityRel: Double,
        val romPx: Double,
        val frames: List<TrackedFrame>
    )

    private val currentRepFrames = mutableListOf<TrackedFrame>()

    private val _currentRepSummary = MutableStateFlow<LiveRepSummary?>(null)
    val currentRepSummary: StateFlow<LiveRepSummary?> = _currentRepSummary.asStateFlow()

    /** In-memory only (see the scope note above) — cleared if the screen is left. */
    private val _liveSessionReps = MutableStateFlow<List<LiveRepSummary>>(emptyList())
    val liveSessionReps: StateFlow<List<LiveRepSummary>> = _liveSessionReps.asStateFlow()

    /** Screen-space trail points accumulated during MOVING, for [LiveTrailOverlay] — frozen (not
     * cleared) once the rep completes, per spec; cleared only on Save/Discard/RE-TAP. */
    data class TrailPoint(val xPx: Float, val yPx: Float, val velocityMps: Float)
    private val _liveTrailPoints = MutableStateFlow<List<TrailPoint>>(emptyList())
    val liveTrailPoints: StateFlow<List<TrailPoint>> = _liveTrailPoints.asStateFlow()

    private fun clearCurrentRepAccumulation() {
        currentRepFrames.clear()
    }

    /** Called from [onLiveResult] on every frame while MOVING, and once more on completion. */
    private fun accumulateLiveRepFrame(result: LiveFrameResult) {
        if (result.phase != LiftPhase.MOVING) return
        currentRepFrames.add(
            TrackedFrame(System.currentTimeMillis(), result.xPx.toDouble(), result.yPx.toDouble(), result.smoothedVelocityMps.toDouble())
        )
        _liveTrailPoints.update {
            it + TrailPoint(result.xPx, result.yPx, result.smoothedVelocityMps)
        }
    }

    /** Mean here matches the offline pipeline's convention (total displacement / total time, not
     * an average of instantaneous readings) — same formula shape as
     * [com.saiyanstrong.domain.usecase.AnalyzeBarPathUseCase], just unscaled (pixels, not meters,
     * since there's no live calibration — see the scope note above). */
    private fun summarizeCurrentRep(): LiveRepSummary? {
        val frames = currentRepFrames
        if (frames.size < 2) return null
        val first = frames.first(); val last = frames.last()
        val totalDisplacementPx = hypot(last.xPx - first.xPx, last.yPx - first.yPx)
        val totalTimeS = (last.timestampMs - first.timestampMs) / 1000.0
        val meanVelocityRel = if (totalTimeS > 0.0) totalDisplacementPx / totalTimeS else 0.0
        val peakVelocityRel = frames.maxOf { it.velocityMps } // velocityMps field reused for the relative reading
        val yValues = frames.map { it.yPx }
        val romPx = (yValues.maxOrNull() ?: 0.0) - (yValues.minOrNull() ?: 0.0)
        return LiveRepSummary(meanVelocityRel, peakVelocityRel, romPx, frames.toList())
    }

    /** User pressed SAVE on the rep summary card — keeps it in the in-memory session list (bumps
     * the rep counter) and clears the trail/summary so the next rep can begin. */
    fun onSaveRep() {
        _currentRepSummary.value?.let { summary -> _liveSessionReps.update { it + summary } }
        _currentRepSummary.value = null
        clearCurrentRepAccumulation()
        _liveTrailPoints.value = emptyList()
    }

    /** DISCARD — same cleanup as save, minus the append (rep doesn't count). */
    fun onDiscardRep() {
        _currentRepSummary.value = null
        clearCurrentRepAccumulation()
        _liveTrailPoints.value = emptyList()
    }

    /** SHARE on a live (uncalibrated) rep card is intentionally not wired to the real
     * RepCardGenerator/share pipeline — that path is built around real m/s numbers, and sharing a
     * card that visually claims real velocity for an uncalibrated relative reading would be
     * actively misleading. Surfaces an explanation instead of a silent no-op. */
    fun onShareLiveRep(onExplain: (String) -> Unit) {
        onExplain("Share is available after a full calibrated analysis (record + calibrate scale).")
    }

    /** "End Session" — stops the continuous live loop and clears session-scoped counters. Does
     * NOT navigate away (no such callback is wired into this screen today) or touch the separate
     * manual RECORD/STOP + offline-analysis flow, which remains fully independent of this mode. */
    fun onEndLiveSession() {
        onRetapColor()
        _liveSessionReps.value = emptyList()
    }

    fun onRecordingFinished(path: String?, gyroTimeline: GyroTimeline?, focalMm: Double, sensorWidthMm: Double, videoStartUptimeNs: Long, errorDetail: String? = null) {
        if (path == null) {
            val detail = errorDetail?.let { "\n($it)" } ?: ""
            _uiState.update {
                it.copy(
                    step = CaptureStep.ERROR,
                    errorMessage = "Recording failed — try again.$detail"
                )
            }
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

    /**
     * One linear single-marker calibration: first tap samples the marker's color; the next two
     * taps place the ends of a known-length reference (a plate is ~45 cm across). A tap after both
     * reference points restarts the reference pair.
     */
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
            it.copy(
                markerSamplePoint = null, colorProfile = null,
                calibrationPoint1 = null, calibrationPoint2 = null
            )
        }
    }

    fun onReferenceLengthChanged(cm: String) {
        _uiState.update { it.copy(referenceLengthCm = cm) }
    }

    /**
     * Single-marker calibration → track → analyze. Every native/IO boundary (frame tracking,
     * analysis, video-dimension read) is wrapped so a failure surfaces as a clear ERROR step
     * instead of crashing. The analysis window is the auto-detected concentric (ascent) phase —
     * see [ConcentricDetector]; without this, a full descend-then-ascend clip nets ~0 vertical
     * displacement and reports a ~0 mean velocity.
     */
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
            _uiState.update { it.copy(errorMessage = "Tap each end of a known-length reference (a plate is ~45 cm across).") }
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
            val samples = runCatching {
                barPathFrameTracker.trackMarker(
                    videoPath = videoPath,
                    colorProfile = colorProfile,
                    gyroTimeline = state.gyroTimeline,
                    focalMm = state.focalMm,
                    sensorWidthMm = state.sensorWidthMm,
                    videoStartUptimeNs = state.videoStartUptimeNs
                )
            }.getOrElse { emptyList() }

            if (samples.size < 2) {
                _uiState.update {
                    it.copy(
                        step = CaptureStep.ERROR,
                        errorMessage = "Couldn't track the marker across enough frames — check it's " +
                            "bright and well-lit against the background, then try again."
                    )
                }
                return@launch
            }

            // Restrict the analysis to the ascent; fall back to the whole clip if no clear
            // concentric is found (ConcentricDetector already handles that internally).
            val (concentricStartMs, concentricEndMs) = ConcentricDetector.detect(samples)
                ?: (samples.first().timestampMs to samples.last().timestampMs)

            val result = runCatching {
                val analysis = analyzeBarPathUseCase.execute(
                    samples = samples,
                    pixelsPerMeter = pixelsPerMeter,
                    massKg = massKg ?: 0.0,
                    concentricStartMs = concentricStartMs,
                    concentricEndMs = concentricEndMs
                )
                val frames = analyzeBarPathUseCase.trackFrames(samples, pixelsPerMeter, concentricStartMs, concentricEndMs)
                val (vw, vh) = barPathFrameTracker.videoDimensions(videoPath)
                Triple(analysis, frames, vw to vh)
            }.getOrNull()

            if (result == null) {
                _uiState.update {
                    it.copy(step = CaptureStep.ERROR, errorMessage = "Couldn't analyze the recording — try again.")
                }
                return@launch
            }

            val (analysis, frames, dims) = result
            _uiState.update {
                it.copy(
                    step = CaptureStep.RESULTS, analysis = analysis, trackedSamples = samples,
                    trackedFrames = frames, videoWidthPx = dims.first, videoHeightPx = dims.second
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
