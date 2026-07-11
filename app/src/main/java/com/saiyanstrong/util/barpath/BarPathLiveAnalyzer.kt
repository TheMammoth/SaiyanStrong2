package com.saiyanstrong.util.barpath

import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.saiyanstrong.domain.util.KalmanTracker2D
import com.saiyanstrong.domain.util.LiftPhase
import com.saiyanstrong.domain.util.LiftPhaseDetector
import com.saiyanstrong.domain.util.Point2D

/** One live-analyzed frame: whether the marker was found, its (Kalman-smoothed) position in the
 * downsampled analysis-image space, the smoothed velocity (only meaningful in MOVING), and the
 * current lift phase. */
data class LiveFrameResult(
    val markerDetected: Boolean,
    val xPx: Float,
    val yPx: Float,
    val smoothedVelocityMps: Float,
    val phase: LiftPhase = LiftPhase.IDLE,
    val repJustCompleted: Boolean = false
)

/**
 * The live analysis loop — the piece that did NOT exist before (the rest of the VBT pipeline
 * records to a file and analyzes it afterward). Runs as a CameraX [ImageAnalysis.Analyzer] during
 * recording: per frame it detects the marker centroid (reusing the same blob logic as the offline
 * tracker), feeds it through a [KalmanTracker2D] (this class is the filter's first real consumer),
 * and emits a [LiveFrameResult] for the UI to show a live readout / motion trail.
 *
 * UNVERIFIED on a real device this session, and slice 1 of a larger feature — deliberate limits:
 *  - Detection uses [MarkerColorProfile.default] (magenta/pink) unless [colorProfile] is set,
 *    because the user samples their marker's real color only AFTER recording today. Arbitrary
 *    marker colors need a pre-record color-sampling step (a calibration-ordering rework, follow-up).
 *  - The velocity is UNCALIBRATED: true m/s needs a pixels-per-meter scale, which also comes from
 *    post-record calibration. [KalmanTracker2D.pixelsPerMeter] is left at its placeholder default,
 *    so smoothedVelocityMps is a relative speed, not real m/s, until that scale is known pre-record.
 *  - Centroid coordinates are in the downsampled analysis-image space, not mapped onto the preview
 *    (that CameraX transform — rotation/crop/mirror — is the positioned-overlay step, not this one).
 */
class BarPathLiveAnalyzer(
    private val onResult: (LiveFrameResult) -> Unit
) : ImageAnalysis.Analyzer {

    @Volatile
    var colorProfile: MarkerColorProfile = MarkerColorProfile.default()

    private val kalman = KalmanTracker2D()
    private val phaseDetector = LiftPhaseDetector()
    private var previousCentroid: Pair<Double, Double>? = null
    private var previousPhase = LiftPhase.IDLE
    private var lastPhaseTimestampMs = 0L

    /** Full reset to IDLE — the phase machine starts fresh, awaiting [startRep]. */
    fun reset() {
        previousCentroid = null
        previousPhase = LiftPhase.IDLE
        lastPhaseTimestampMs = 0L
        phaseDetector.reset()
    }

    /** User tapped "Start rep" — begins the settling window (see [LiftPhaseDetector]). */
    fun startRep() = phaseDetector.startRep()

    override fun analyze(image: ImageProxy) {
        try {
            val pixels = imageProxyToDownsampledPixels(image) ?: return
            val detected = detectMarkerCentroidInPixels(
                pixels.data, pixels.width, pixels.height, colorProfile, previousCentroid
            )
            val timestampMs = image.imageInfo.timestamp / 1_000_000L
            val centroidPoint = detected?.let { Point2D(it.first, it.second) }
            if (detected != null) previousCentroid = detected.first to detected.second

            // Every centroid goes through the phase machine before the Kalman: it gates velocity to
            // the actual lift (MOVING) so camera-shake jitter during the stationary phase produces
            // no phantom movement.
            val phaseUpdate = phaseDetector.update(centroidPoint, timestampMs)
            // Retune the filter's noise to the phase (no-op unless the phase changed). The prompt
            // called for this call "from the ViewModel", but the Kalman lives here in the analyzer
            // alongside the phase detector — this is its real, single call site.
            kalman.setPhase(phaseUpdate.phase)

            var velocity = 0f
            var px = centroidPoint?.x?.toFloat() ?: 0f
            var py = centroidPoint?.y?.toFloat() ?: 0f
            if (phaseUpdate.phase == LiftPhase.MOVING && centroidPoint != null) {
                // Reset the filter at movement onset — otherwise the READY→MOVING jump (from the
                // near-zero baseline-subtracted position to the raw centroid) would spike velocity.
                if (previousPhase != LiftPhase.MOVING) {
                    kalman.reset(centroidPoint.x, centroidPoint.y)
                } else {
                    val dt = (timestampMs - lastPhaseTimestampMs) / 1000.0
                    if (dt > 0.0) kalman.predict(dt)
                    kalman.update(centroidPoint.x, centroidPoint.y)
                }
                val pos = kalman.smoothedPosition
                px = pos.x.toFloat(); py = pos.y.toFloat()
                velocity = kalman.smoothedVelocityMps
            }
            previousPhase = phaseUpdate.phase
            lastPhaseTimestampMs = timestampMs

            onResult(
                LiveFrameResult(
                    markerDetected = detected != null,
                    xPx = px, yPx = py,
                    smoothedVelocityMps = velocity,
                    phase = phaseUpdate.phase,
                    repJustCompleted = phaseUpdate.repJustCompleted
                )
            )
        } catch (_: Throwable) {
            // A single bad frame must never crash the analyzer / camera pipeline (incl. OOM on a
            // large frame — better a dropped frame than a downed camera).
        } finally {
            image.close()
        }
    }

    private class DownsampledFrame(val data: IntArray, val width: Int, val height: Int)

    /**
     * YUV_420_888 [ImageProxy] → a downsampled ARGB IntArray (every [step]th pixel), reusing the
     * tested [yuvToRgb] conversion. Downsampling keeps per-frame cost low enough for a live loop —
     * exact pixel precision isn't needed for a centroid.
     */
    private fun imageProxyToDownsampledPixels(image: ImageProxy, step: Int = 2): DownsampledFrame? {
        if (image.format != ImageFormat.YUV_420_888) return null
        val outWidth = image.width / step
        val outHeight = image.height / step
        if (outWidth <= 0 || outHeight <= 0) return null

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val yRowStride = yPlane.rowStride; val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride; val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride; val vPixelStride = vPlane.pixelStride

        val out = IntArray(outWidth * outHeight)
        for (oy in 0 until outHeight) {
            val sy = oy * step
            val uvRow = (sy / 2)
            for (ox in 0 until outWidth) {
                val sx = ox * step
                val uvCol = (sx / 2)
                val y = yBuffer.get(sy * yRowStride + sx * yPixelStride).toInt() and 0xFF
                val u = uBuffer.get(uvRow * uRowStride + uvCol * uPixelStride).toInt() and 0xFF
                val v = vBuffer.get(uvRow * vRowStride + uvCol * vPixelStride).toInt() and 0xFF
                out[oy * outWidth + ox] = yuvToRgb(y, u, v)
            }
        }
        return DownsampledFrame(out, outWidth, outHeight)
    }
}

/**
 * Pure marker-centroid detection over an ARGB pixel grid — the same connected-component + nearest-
 * neighbor logic as the offline [BarPathFrameTracker], but operating on an already-decoded pixel
 * array so it's unit-testable without any camera/Bitmap. Returns (xPx, yPx, diameterPx) or null.
 */
internal fun detectMarkerCentroidInPixels(
    pixels: IntArray,
    width: Int,
    height: Int,
    profile: MarkerColorProfile,
    previousCentroid: Pair<Double, Double>?,
    minMarkerPixels: Int = 6
): Triple<Double, Double, Double>? {
    val mask = BooleanArray(pixels.size)
    val weights = DoubleArray(pixels.size)
    for (i in pixels.indices) {
        val p = pixels[i]
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        if (profile.matches(r, g, b)) {
            mask[i] = true
            weights[i] = profile.matchScore(r, g, b).coerceAtLeast(0.01)
        }
    }
    val blobs = findBlobs(mask, weights, width, height).filter { it.size >= minMarkerPixels }
    val chosen = chooseTrackedBlob(blobs, previousCentroid) ?: return null
    return Triple(chosen.centroidX, chosen.centroidY, chosen.diameterPx)
}
