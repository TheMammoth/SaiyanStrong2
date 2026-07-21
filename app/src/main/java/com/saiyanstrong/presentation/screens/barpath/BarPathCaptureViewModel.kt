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
import com.saiyanstrong.domain.util.RepSegmenter
import com.saiyanstrong.domain.util.GyroTimeline
import com.saiyanstrong.domain.util.LiftPhase
import com.saiyanstrong.domain.util.LockOnTracker
import com.saiyanstrong.domain.util.ReticleState
import com.saiyanstrong.util.SessionShareImageSaver
import com.saiyanstrong.util.barpath.BarPathFrameTracker
import com.saiyanstrong.util.barpath.BarPathVideoImporter
import com.saiyanstrong.util.barpath.LiveFrameResult
import com.saiyanstrong.util.barpath.MarkerColorProfile
import com.saiyanstrong.util.barpath.VitBarTrackerSupport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

/**
 * RECORDING → PLAYER (video plays immediately; scrub + tap the bar to mark it, marker tracks live
 * from that point as playback loops) → optionally SCALE (tap plate edges + weight) → PROCESSING
 * (analyze the already-tracked samples) → RESULTS. Tracking streams in the background from the mark
 * time; those samples are reused by the analysis, so getting numbers never re-tracks.
 */
enum class CaptureStep { RECORDING, PLAYER, PROCESSING, SCALE, RESULTS, ERROR }

data class TapPoint(val xPx: Float, val yPx: Float)

/** The one-tap plate selection preview (centre + apparent diameter, full-res video px). */
data class PlateSelectionUi(val centerXVideo: Float, val centerYVideo: Float, val diameterVideo: Float)

/** One of the two plate marks (bottom / top): where it was tapped, at what playback time, and the
 * selection outline it produced. */
data class PlateMark(val tap: TapPoint, val atMs: Long, val selection: PlateSelectionUi)

/** One rep of a set: its 1-based number + the analysis and per-frame path over that rep's concentric. */
data class RepResult(val index: Int, val analysis: BarPathAnalysis, val trackedFrames: List<TrackedFrame>)

/** Per-rep concentric windows from the tracked reps: each rep's (bottom, top) marks → (minMs, maxMs),
 * so tap order within a rep doesn't matter. Pure/unit-tested. */
internal fun repWindowsFromCompleted(reps: List<CompletedRep>): List<Pair<Long, Long>> =
    reps.map { minOf(it.bottomMark.atMs, it.topMark.atMs) to maxOf(it.bottomMark.atMs, it.topMark.atMs) }

data class BarPathCaptureUiState(
    val step: CaptureStep = CaptureStep.RECORDING,
    val videoPath: String? = null,
    val calibrationFrame: Bitmap? = null,
    /** The paused frame under the tap, for the placement magnifier loupe. */
    val placementFrame: Bitmap? = null,
    /** The rep currently being placed (0–2 marks: bottom, then top). When it reaches 2 the rep
     * auto-tracks and this clears. Completed reps live in the app-scoped runner. */
    val currentMarks: List<PlateMark> = emptyList(),
    /** Number of reps tracked so far this set (mirrored from the runner). */
    val repCount: Int = 0,
    val markerSamplePoint: TapPoint? = null,
    val colorProfile: MarkerColorProfile? = null,
    /** Playback time (ms) of the first (bottom) mark — used to extract the scale-step still. */
    val markMs: Long? = null,
    val calibrationPoint1: TapPoint? = null,
    val calibrationPoint2: TapPoint? = null,
    val referenceLengthCm: String = "45",
    val weightKgInput: String = "",
    val errorMessage: String? = null,
    val analysis: BarPathAnalysis? = null,
    /** Per-rep results for a multi-rep set (empty for a single-rep clip, where [analysis] is used). */
    val repResults: List<RepResult> = emptyList(),
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
    /** True while the full tracking pass runs after the user confirms the start box — the player
     * pauses and shows a determinate progress bar, then plays the complete path (no replay-to-
     * catch-up). */
    val isTracking: Boolean = false,
    /** 0f..1f progress of the marker-tracking pass (frame extraction is slow — without this the
     * PROCESSING screen looks frozen). Only meaningful during the track step. */
    val trackingProgress: Float = 0f,
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
    private val sessionShareImageSaver: SessionShareImageSaver,
    private val trackingRunner: BarPathTrackingRunner
) : ViewModel() {

    init {
        // Mirror/restore the app-scoped tracking pass, so leaving the screen and coming back (or
        // backgrounding) doesn't interrupt a long multi-rep analysis — a fresh ViewModel restores
        // the player from whatever the runner is currently doing.
        viewModelScope.launch { trackingRunner.state.collect { applyTrackState(it) } }
    }

    private fun applyTrackState(st: SetTrackState) {
        // The player's trail shows the rep being tracked (partial) or the last completed rep.
        _liveSamples.value = if (st.trackingRep) st.partialSamples
            else st.completedReps.lastOrNull()?.samples ?: emptyList()
        _uiState.update { base ->
            val restored = base.restoredFrom(st).copy(
                repCount = st.completedReps.size,
                isTracking = st.trackingRep,
                trackingProgress = st.progress
            )
            if (st.failedMessage != null) restored.copy(errorMessage = st.failedMessage) else restored
        }
    }

    /** Fill in the player state from the set runner when this ViewModel is fresh (navigated back into
     * a set in progress); keeps the ViewModel's own values when it already has them. */
    private fun BarPathCaptureUiState.restoredFrom(st: SetTrackState): BarPathCaptureUiState {
        if (st.videoPath == null) return this
        return copy(
            step = if (step == CaptureStep.RECORDING) CaptureStep.PLAYER else step,
            videoPath = videoPath ?: st.videoPath,
            videoWidthPx = if (videoWidthPx > 0) videoWidthPx else st.videoWidthPx,
            videoHeightPx = if (videoHeightPx > 0) videoHeightPx else st.videoHeightPx,
            markMs = markMs ?: st.completedReps.firstOrNull()?.bottomMark?.atMs
        )
    }

    private val exerciseId: Int = checkNotNull(savedStateHandle["exerciseId"])
    private val setLogId: Long = savedStateHandle.get<Long>("setLogId") ?: -1L
    private val knownWeightKg: Double = (savedStateHandle.get<Float>("weightKg") ?: -1f).toDouble()

    /** No logged set to attach to — user picked an exercise, not a set, from the Home card. */
    val isStandalone: Boolean = setLogId <= 0L

    private val _uiState = MutableStateFlow(BarPathCaptureUiState())
    val uiState: StateFlow<BarPathCaptureUiState> = _uiState.asStateFlow()

    /** Marker positions streamed in as background tracking finds them — the live player's dot/trail
     * reads this so the dot follows the bar as tracking progresses (fully live once it's caught up
     * on the video's loop). Reset on each (re)mark. */
    private val _liveSamples = MutableStateFlow<List<BarPathSample>>(emptyList())
    val liveSamples: StateFlow<List<BarPathSample>> = _liveSamples.asStateFlow()

    /** The marker color profile "trained" during the pre-record calibration step (SPEC.md). Kept
     * separate from the ephemeral per-video [BarPathCaptureUiState.colorProfile] so it survives the
     * record→player transition (loadVideo resets that). Null for the gallery-import path (no live
     * camera to calibrate) — that path falls back to sampling color from the tapped frame. */
    @Volatile
    private var calibratedProfile: MarkerColorProfile? = null

    /** Called when the pre-record calibration locks a marker color under the user's real lighting. */
    fun onMarkerCalibrated(profile: MarkerColorProfile) {
        calibratedProfile = profile
    }

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
        loadVideo(path, failureMessage = "Couldn't read the recorded video.")
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
                loadVideo(path, failureMessage = "Couldn't read the imported video.")
            }
        }
    }

    /** Go straight to the live player — read only the video dimensions (a fast metadata read, needed
     * to map taps and draw the overlay); no blocking frame extraction, so playback starts at once. */
    private fun loadVideo(path: String, failureMessage: String) {
        trackingRunner.clear() // a new video discards any previous (possibly restored) tracking pass
        _uiState.update { it.copy(isPreparingVideo = true) }
        viewModelScope.launch(Dispatchers.Default) {
            val (vw, vh) = runCatching { barPathFrameTracker.videoDimensions(path) }.getOrDefault(0 to 0)
            _uiState.update {
                if (vw > 0 && vh > 0) {
                    it.copy(
                        step = CaptureStep.PLAYER, videoPath = path,
                        videoWidthPx = vw, videoHeightPx = vh, isPreparingVideo = false,
                        markMs = null, colorProfile = null, markerSamplePoint = null,
                        currentMarks = emptyList(), repCount = 0
                    )
                } else {
                    it.copy(step = CaptureStep.ERROR, isPreparingVideo = false, errorMessage = failureMessage)
                }
            }
            _liveSamples.value = emptyList()
        }
    }

    fun onWeightKgChanged(kg: String) {
        _uiState.update { it.copy(weightKgInput = kg) }
    }

    /**
     * PLAYER — the user tapped the plate at ([videoX],[videoY]) in full-res video px at playback time
     * [atMs]. Segments the plate ("magic wand") and appends it to the CURRENT rep (bottom, then top).
     * On the 2nd tap the rep AUTO-TRACKS (via the app-scoped runner) and the current marks clear.
     */
    fun onSegmentTap(videoX: Float, videoY: Float, atMs: Long) {
        val videoPath = _uiState.value.videoPath ?: return
        _uiState.update { it.copy(markerSamplePoint = TapPoint(videoX, videoY), errorMessage = null) }
        viewModelScope.launch(Dispatchers.Default) {
            val frame = runCatching { barPathFrameTracker.extractFrameAt(videoPath, atMs) }.getOrNull()
            val hit = frame?.let {
                runCatching { barPathFrameTracker.segmentPlate(it, videoX.toDouble(), videoY.toDouble()) }.getOrNull()
            }
            if (hit == null) {
                _uiState.update { it.copy(placementFrame = frame, errorMessage = "Couldn't select a plate there — tap a clearly coloured part of the plate (the rim).") }
                return@launch
            }
            val sel = PlateSelectionUi(hit.centerX.toFloat(), hit.centerY.toFloat(), hit.diameterPx.toFloat())
            val mark = PlateMark(TapPoint(videoX, videoY), atMs, sel)
            val state = _uiState.value
            val newCurrent = state.currentMarks + mark
            if (newCurrent.size >= 2) {
                // Second tap of the rep — auto-track it and clear the current marks.
                trackingRunner.startRep(videoPath, state.videoWidthPx, state.videoHeightPx, newCurrent[0], newCurrent[1])
                _uiState.update { it.copy(placementFrame = frame, currentMarks = emptyList(), markMs = it.markMs ?: newCurrent[0].atMs, errorMessage = null) }
            } else {
                _uiState.update { it.copy(placementFrame = frame, currentMarks = newCurrent, markMs = it.markMs ?: atMs, errorMessage = null) }
            }
        }
    }

    /** UNDO — remove the last un-tracked mark of the current rep (a mis-tap on the bottom). */
    fun onUndoMark() {
        _uiState.update { it.copy(currentMarks = it.currentMarks.dropLast(1), errorMessage = null) }
    }

    /** REDO LAST REP — drop the most recent tracked rep so the user can re-mark it. */
    fun onRedoLastRep() {
        trackingRunner.redoLast()
        _uiState.update { it.copy(currentMarks = emptyList(), errorMessage = null) }
    }

    /** RE-MARK — clear the whole set so the user can start over. */
    fun onReMark() {
        trackingRunner.clear()
        _liveSamples.value = emptyList()
        _uiState.update {
            it.copy(
                markMs = null, colorProfile = null, markerSamplePoint = null, trackedSamples = emptyList(),
                placementFrame = null, currentMarks = emptyList(), repCount = 0,
                isTracking = false, trackingProgress = 0f, errorMessage = null
            )
        }
    }

    /** SCALE step — taps place the two ends of a known-length reference (a plate is ~45 cm). A tap
     * after both are placed restarts the pair. */
    fun onScaleTap(point: TapPoint) {
        _uiState.update { state ->
            when {
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

    fun onResetScalePoints() {
        _uiState.update { it.copy(calibrationPoint1 = null, calibrationPoint2 = null) }
    }

    fun onReferenceLengthChanged(cm: String) {
        _uiState.update { it.copy(referenceLengthCm = cm) }
    }

    /** PLAYER → SCALE: user wants real velocity numbers. Extract a still frame at the mark point to
     * tap the plate on, then collect the plate-scale + weight. */
    fun onGetVelocityNumbers() {
        val state = _uiState.value
        val videoPath = state.videoPath ?: return
        val atMs = state.markMs ?: 0L
        // Keep the runner's completed reps — onConfirmScale reads them for per-rep analysis.
        _uiState.update { it.copy(isPreparingVideo = true) }
        viewModelScope.launch(Dispatchers.Default) {
            val frame = runCatching { barPathFrameTracker.extractFrameAt(videoPath, atMs) }.getOrNull()
            _uiState.update {
                if (frame != null) {
                    it.copy(
                        step = CaptureStep.SCALE, calibrationFrame = frame, isPreparingVideo = false,
                        calibrationPoint1 = null, calibrationPoint2 = null, errorMessage = null
                    )
                } else {
                    it.copy(step = CaptureStep.ERROR, isPreparingVideo = false, errorMessage = "Couldn't read a frame for scaling.")
                }
            }
        }
    }

    /**
     * SCALE → analyze. Reuses the samples already tracked in [onTrackAndPlay] (no re-track); scales
     * pixels→meters from the two plate-edge taps; analyzes over the auto-detected concentric
     * (ascent) window ([ConcentricDetector]) so a full descend-then-ascend clip doesn't net ~0.
     */
    fun onConfirmScale() {
        val state = _uiState.value
        val p1 = state.calibrationPoint1
        val p2 = state.calibrationPoint2
        val referenceCm = state.referenceLengthCm.toDoubleOrNull()
        val videoPath = state.videoPath
        val completedReps = trackingRunner.state.value.completedReps
        val samples = completedReps.flatMap { it.samples } // all reps' samples, time-ordered
        val massKg = if (isStandalone) state.weightKgInput.toDoubleOrNull() else knownWeightKg

        if (p1 == null || p2 == null) {
            _uiState.update { it.copy(errorMessage = "Tap each edge of a plate (~45 cm across) to set the scale.") }
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
        if (videoPath == null || samples.size < 2) return

        val pixelDistance = hypot((p2.xPx - p1.xPx).toDouble(), (p2.yPx - p1.yPx).toDouble())
        val pixelsPerMeter = pixelDistance / (referenceCm / 100.0)

        // Per-rep windows come straight from the tracked reps' marks — no guessing.
        val markWindows = repWindowsFromCompleted(completedReps)

        _uiState.update { it.copy(step = CaptureStep.PROCESSING, errorMessage = null) }
        viewModelScope.launch(Dispatchers.Default) {
            val windows = markWindows.ifEmpty {
                // Fallback (e.g. a restored single window): auto-detect the concentric.
                RepSegmenter.segment(samples).ifEmpty {
                    listOf(
                        ConcentricDetector.detect(samples)
                            ?: (samples.first().timestampMs to samples.last().timestampMs)
                    )
                }
            }

            val reps = runCatching {
                windows.mapIndexedNotNull { i, (startMs, endMs) ->
                    val analysis = analyzeBarPathUseCase.execute(
                        samples = samples, pixelsPerMeter = pixelsPerMeter, massKg = massKg ?: 0.0,
                        concentricStartMs = startMs, concentricEndMs = endMs
                    )
                    val frames = analyzeBarPathUseCase.trackFrames(samples, pixelsPerMeter, startMs, endMs)
                    RepResult(i + 1, analysis, frames)
                }
            }.getOrNull()

            if (reps.isNullOrEmpty()) {
                _uiState.update {
                    it.copy(step = CaptureStep.ERROR, errorMessage = "Couldn't analyze the recording — try again.")
                }
                return@launch
            }

            // The best rep (highest mean velocity) drives the velocity replay + share card.
            val best = reps.maxBy { it.analysis.meanConcentricVelocityMs }
            _uiState.update {
                it.copy(
                    step = CaptureStep.RESULTS, repResults = reps,
                    analysis = best.analysis, trackedFrames = best.trackedFrames, trackedSamples = samples
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
        val state = _uiState.value
        val reps = state.repResults
        val analyses = if (reps.isNotEmpty()) reps.map { it.analysis } else listOfNotNull(state.analysis)
        if (analyses.isEmpty()) return
        viewModelScope.launch {
            if (isStandalone) {
                // Standalone: save every rep so the exercise's velocity chart shows the whole set.
                analyses.forEach { barPathRepository.saveFreestandingBarPathMetrics(exerciseId, it) }
            } else {
                // Set-linked: one metric per set log — save the best (highest mean velocity) rep.
                val best = reps.maxByOrNull { it.analysis.meanConcentricVelocityMs }?.analysis ?: analyses.first()
                barPathRepository.saveBarPathMetrics(setLogId, exerciseId, best)
            }
            trackingRunner.clear() // set saved — release it so a new session starts clean
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun onRetry() {
        calibratedProfile = null
        trackingRunner.clear()
        _uiState.value = BarPathCaptureUiState()
    }
}
