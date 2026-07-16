package com.saiyanstrong.util.barpath

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/** A detected region in normalized [0,1] frame coordinates — the UI scales it onto the preview box. */
data class CalibrationRegion(val nx: Float, val ny: Float, val nw: Float, val nh: Float)

/**
 * One live calibration frame. [profile] is the current candidate (null until the user has tapped
 * their marker at least once). [markerRegion] is where the marker is currently detected (drawn
 * green); [clashRegions] are other large same-color regions elsewhere in frame (drawn red) when
 * [clash] is true. [locked] is true once the marker has been a stable detection for enough
 * consecutive frames — the UI gates START RECORDING on this.
 */
data class CalibrationFrameResult(
    val profile: MarkerColorProfile?,
    val markerRegion: CalibrationRegion?,
    val clashRegions: List<CalibrationRegion>,
    val clash: Boolean,
    val locked: Boolean
)

/**
 * The pre-record "train the marker" loop (SPEC.md). Runs as a CameraX [ImageAnalysis.Analyzer] on
 * a live Preview+Analysis session (never with VideoCapture — that 3-stream bind is the fragile one
 * we avoid). Per frame it detects the calibrated color, reports where it's matching so the UI can
 * overlay it, warns on a background clash, and reports a stable-lock signal.
 *
 * Multi-sample color range: the first tap seeds an accumulated HSV sample set; for a short window
 * after, samples around the tracked marker are folded in too, so the resulting [MarkerColorProfile]
 * spans the marker's real lighting variation rather than one instant. Reuses the exact same blob
 * detection ([findBlobs]/[chooseTrackedBlob]) as the offline tracker, so what calibration "sees" is
 * what recording will track.
 *
 * The tap→analysis-buffer coordinate mapping shares the caveat already flagged for the offline
 * tap-to-sample: it assumes Preview and ImageAnalysis are bound to the same crop region (a known
 * CameraX gotcha when their aspect ratios differ), unverified without a device.
 */
class MarkerCalibrationAnalyzer(
    private val onResult: (CalibrationFrameResult) -> Unit
) : ImageAnalysis.Analyzer {

    /** Set by the UI thread on a tap (normalized meter-point coords); consumed by the next frame. */
    @Volatile
    var pendingTap: PendingColorSample? = null

    private val analysisStep = 2
    private val accumulated = ArrayList<Triple<Double, Double, Double>>()
    @Volatile
    private var profile: MarkerColorProfile? = null
    private var trackedPoint: Pair<Double, Double>? = null
    private var postTapFramesLeft = 0
    private var consecutiveDetections = 0

    fun reset() {
        accumulated.clear()
        profile = null
        trackedPoint = null
        postTapFramesLeft = 0
        consecutiveDetections = 0
        pendingTap = null
    }

    override fun analyze(image: ImageProxy) {
        try {
            val frame = imageProxyToDownsampledPixels(image, analysisStep) ?: return
            val pixels = frame.data
            val w = frame.width
            val h = frame.height

            consumePendingTap(image, pixels, w, h)

            val current = profile
            if (current == null) {
                onResult(CalibrationFrameResult(null, null, emptyList(), clash = false, locked = false))
                return
            }

            // Same match mask + blob detection the offline tracker uses.
            val mask = BooleanArray(pixels.size)
            val weights = DoubleArray(pixels.size)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                if (current.matches(r, g, b)) {
                    mask[i] = true
                    weights[i] = current.matchScore(r, g, b).coerceAtLeast(0.01)
                }
            }
            val blobs = findBlobs(mask, weights, w, h).filter { it.size >= MIN_BLOB_PIXELS }
            val marker = chooseTrackedBlob(blobs, trackedPoint)

            // Multi-sample: keep folding in samples around the tracked marker for a short window so
            // the range spans real lighting variation across frames, not just the first patch.
            if (marker != null && postTapFramesLeft > 0) {
                val more = collectPatchSamples(
                    pixels, w, h, marker.centroidX.toInt(), marker.centroidY.toInt(), patchRadius = 4
                )
                if (more.isNotEmpty()) {
                    addSamples(more)
                    profile = MarkerColorRangeBuilder.build(accumulated) ?: current
                }
                postTapFramesLeft--
            }
            if (marker != null) trackedPoint = marker.centroidX to marker.centroidY

            val clashVerdict = BackgroundClashDetector.classify(
                blobs, marker?.let { it.centroidX to it.centroidY }, w, h
            )
            val clash = clashVerdict == ClashVerdict.CLASH

            consecutiveDetections = if (marker != null) consecutiveDetections + 1 else 0
            val locked = consecutiveDetections >= LOCK_FRAMES

            val markerRegion = marker?.let { regionOf(it, w, h) }
            val clashRegions = if (clash) {
                blobs.filter { it !== marker }.map { regionOf(it, w, h) }
            } else {
                emptyList()
            }

            onResult(CalibrationFrameResult(profile, markerRegion, clashRegions, clash, locked))
        } catch (_: Throwable) {
            // A single bad frame must never crash the camera pipeline.
        } finally {
            image.close()
        }
    }

    private fun consumePendingTap(image: ImageProxy, pixels: IntArray, w: Int, h: Int) {
        val pending = pendingTap ?: return
        pendingTap = null
        val bufX = (pending.normX * image.width).toInt() / analysisStep
        val bufY = (pending.normY * image.height).toInt() / analysisStep
        val samples = collectPatchSamples(pixels, w, h, bufX, bufY)
        if (samples.isEmpty()) return
        addSamples(samples)
        profile = MarkerColorRangeBuilder.build(accumulated)
        trackedPoint = bufX.toDouble() to bufY.toDouble()
        postTapFramesLeft = POST_TAP_FRAMES
        consecutiveDetections = 0
    }

    /** Append, capping the accumulator so a long calibration doesn't grow unbounded (drops oldest). */
    private fun addSamples(more: List<Triple<Double, Double, Double>>) {
        accumulated.addAll(more)
        val excess = accumulated.size - MAX_ACCUM
        if (excess > 0) repeat(excess) { accumulated.removeAt(0) }
    }

    private fun regionOf(blob: Blob, w: Int, h: Int): CalibrationRegion = CalibrationRegion(
        nx = blob.minX.toFloat() / w,
        ny = blob.minY.toFloat() / h,
        nw = (blob.maxX - blob.minX + 1).toFloat() / w,
        nh = (blob.maxY - blob.minY + 1).toFloat() / h
    )

    private companion object {
        const val MIN_BLOB_PIXELS = 10
        const val POST_TAP_FRAMES = 20   // ~1-2 s of extra samples after the tap (multi-sample range)
        const val LOCK_FRAMES = 8        // consecutive detections before START RECORDING enables
        const val MAX_ACCUM = 4000       // cap accumulated samples (memory + build cost)
    }
}
