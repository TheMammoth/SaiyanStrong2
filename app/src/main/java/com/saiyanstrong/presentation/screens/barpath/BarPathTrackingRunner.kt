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
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Everything needed to run — and later restore — a per-rep set tracking pass. [marks] is a flat
 * list of taps, paired into reps: (bottom, top), (bottom, top), … */
data class TrackRequest(
    val videoPath: String,
    val videoWidthPx: Int,
    val videoHeightPx: Int,
    val marks: List<PlateMark>
)

/** State of the (app-scoped) tracking pass; survives the capture ViewModel being cleared. */
sealed interface TrackState {
    data object Idle : TrackState
    data class Working(val request: TrackRequest, val progress: Float, val samples: List<BarPathSample>) : TrackState
    data class Complete(val request: TrackRequest, val samples: List<BarPathSample>) : TrackState
    data class Failed(val request: TrackRequest, val message: String) : TrackState
}

/**
 * Runs the whole-clip plate tracking on an APPLICATION-scoped coroutine so it is NOT cancelled when
 * the user leaves the capture screen (which clears the ViewModel and its scope). The ViewModel just
 * observes [state] and mirrors/restores it, so navigating away and back — or backgrounding — no
 * longer interrupts a long multi-rep analysis. (It does not survive the OS killing the whole app
 * process; that would need a foreground service.)
 */
@Singleton
class BarPathTrackingRunner @Inject constructor(
    private val barPathFrameTracker: BarPathFrameTracker
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _state = MutableStateFlow<TrackState>(TrackState.Idle)
    val state: StateFlow<TrackState> = _state.asStateFlow()

    /** Start (or restart) tracking [request]. Progress + streamed samples are published to [state]. */
    fun start(request: TrackRequest) {
        job?.cancel()
        _state.value = TrackState.Working(request, 0f, emptyList())
        job = scope.launch {
            val acc = ArrayList<BarPathSample>()
            var progress = 0f
            val reps = request.marks.chunked(2).filter { it.size == 2 }.map { (bottom, top) ->
                BarPathFrameTracker.RepMarks(
                    bottom.tap.xPx.toDouble(), bottom.tap.yPx.toDouble(), bottom.atMs,
                    top.tap.xPx.toDouble(), top.tap.yPx.toDouble(), top.atMs
                )
            }
            val samples = runCatching {
                barPathFrameTracker.trackPlateReps(
                    videoPath = request.videoPath,
                    reps = reps,
                    onProgress = { p -> progress = p; _state.value = TrackState.Working(request, p, acc.toList()) },
                    onSample = { s -> acc.add(s); _state.value = TrackState.Working(request, progress, acc.toList()) }
                )
            }.getOrElse { emptyList() }
            _state.value = if (samples.size >= 2) {
                TrackState.Complete(request, samples)
            } else {
                TrackState.Failed(request, "Couldn't track the plate. Re-tap a clearly coloured plate (the rim), not the grey middle.")
            }
        }
    }

    /** Discard the current/last result (new recording, re-mark, or reset). */
    fun clear() {
        job?.cancel()
        _state.value = TrackState.Idle
    }
}
