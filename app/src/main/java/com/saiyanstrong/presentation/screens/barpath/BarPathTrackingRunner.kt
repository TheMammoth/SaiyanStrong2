package com.saiyanstrong.presentation.screens.barpath

import com.saiyanstrong.domain.model.BarPathSample
import com.saiyanstrong.util.barpath.BarPathFrameTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** One tracked rep of a set: its bottom+top marks and the samples from the bounded two-mark track. */
data class CompletedRep(val bottomMark: PlateMark, val topMark: PlateMark, val samples: List<BarPathSample>)

/**
 * The state of a set being built rep-by-rep. Survives the capture ViewModel being cleared (the runner
 * is app-scoped), so leaving/returning keeps the set. [partialSamples] is the rep currently being
 * tracked (for the live dot); [videoPath]/dims are kept for the leave-and-return restore.
 */
data class SetTrackState(
    val videoPath: String? = null,
    val videoWidthPx: Int = 0,
    val videoHeightPx: Int = 0,
    val completedReps: List<CompletedRep> = emptyList(),
    val trackingRep: Boolean = false,
    val progress: Float = 0f,
    val partialSamples: List<BarPathSample> = emptyList(),
    val failedMessage: String? = null
)

/**
 * Runs — and accumulates — a set ONE REP AT A TIME on an application-scoped coroutine, so the set
 * survives the user leaving the capture screen. Each rep uses the reliable bounded two-mark tracker
 * ([BarPathFrameTracker.trackPlateTwoMark]); a completed rep is appended to [SetTrackState.completedReps].
 */
@Singleton
class BarPathTrackingRunner @Inject constructor(
    private val barPathFrameTracker: BarPathFrameTracker
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _state = MutableStateFlow(SetTrackState())
    val state: StateFlow<SetTrackState> = _state.asStateFlow()

    /** Track one rep (bottom→top) and, on success, append it to the set. */
    fun startRep(videoPath: String, videoWidthPx: Int, videoHeightPx: Int, bottom: PlateMark, top: PlateMark) {
        job?.cancel()
        _state.update {
            it.copy(
                videoPath = videoPath, videoWidthPx = videoWidthPx, videoHeightPx = videoHeightPx,
                trackingRep = true, progress = 0f, partialSamples = emptyList(), failedMessage = null
            )
        }
        job = scope.launch {
            val acc = ArrayList<BarPathSample>()
            val samples = runCatching {
                barPathFrameTracker.trackPlateTwoMark(
                    videoPath = videoPath,
                    tapAX = bottom.tap.xPx.toDouble(), tapAY = bottom.tap.yPx.toDouble(), atAMs = bottom.atMs,
                    tapBX = top.tap.xPx.toDouble(), tapBY = top.tap.yPx.toDouble(), atBMs = top.atMs,
                    onProgress = { p -> _state.update { it.copy(progress = p, partialSamples = acc.toList()) } },
                    onSample = { s -> acc.add(s); _state.update { it.copy(partialSamples = acc.toList()) } }
                )
            }.getOrElse { emptyList() }
            _state.update {
                if (samples.size >= 2) {
                    it.copy(
                        completedReps = it.completedReps + CompletedRep(bottom, top, samples),
                        trackingRep = false, progress = 1f, partialSamples = samples
                    )
                } else {
                    it.copy(
                        trackingRep = false, progress = 0f, partialSamples = emptyList(),
                        failedMessage = "Couldn't track that rep — tap the coloured rim at the bottom and the top."
                    )
                }
            }
        }
    }

    /** Drop the most recent tracked rep (REDO LAST REP). */
    fun redoLast() {
        _state.update { it.copy(completedReps = it.completedReps.dropLast(1), partialSamples = emptyList(), failedMessage = null) }
    }

    /** Discard the whole set (new recording / re-mark / reset). */
    fun clear() {
        job?.cancel()
        _state.value = SetTrackState()
    }
}
