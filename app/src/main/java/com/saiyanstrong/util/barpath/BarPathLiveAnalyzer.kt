package com.saiyanstrong.util.barpath

import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.saiyanstrong.domain.util.KalmanTracker2D

/** One live-analyzed frame: whether the marker was found, its (Kalman-smoothed) position in the
 * downsampled analysis-image space, and the smoothed velocity. */
data class LiveFrameResult(
    val markerDetected: Boolean,
    val xPx: Float,
    val yPx: Float,
    val smoothedVelocityMps: Float
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
    private var previousCentroid: Pair<Double, Double>? = null
    private var lastTimestampNs = 0L
    private var initialized = false

    /** Call at recording (rep) start — clears the trail/velocity history. */
    fun reset() {
        previousCentroid = null
        lastTimestampNs = 0L
        initialized = false
    }

    override fun analyze(image: ImageProxy) {
        try {
            val pixels = imageProxyToDownsampledPixels(image) ?: return
            val centroid = detectMarkerCentroidInPixels(
                pixels.data, pixels.width, pixels.height, colorProfile, previousCentroid
            )
            val timestampNs = image.imageInfo.timestamp

            if (centroid != null) {
                val (cx, cy, _) = centroid
                if (!initialized) {
                    kalman.reset(cx, cy)
                    initialized = true
                } else {
                    val dt = (timestampNs - lastTimestampNs) / 1_000_000_000.0
                    if (dt > 0.0) kalman.predict(dt)
                    kalman.update(cx, cy)
                }
                previousCentroid = cx to cy
                lastTimestampNs = timestampNs
                val pos = kalman.smoothedPosition
                onResult(LiveFrameResult(true, pos.x.toFloat(), pos.y.toFloat(), kalman.smoothedVelocityMps))
            } else {
                // Missed frame: coast on the filter's momentum if we've already locked on.
                if (initialized) {
                    val dt = (timestampNs - lastTimestampNs) / 1_000_000_000.0
                    if (dt > 0.0) {
                        kalman.predict(dt)
                        lastTimestampNs = timestampNs
                    }
                }
                onResult(LiveFrameResult(false, 0f, 0f, if (initialized) kalman.smoothedVelocityMps else 0f))
            }
        } catch (_: Exception) {
            // A single bad frame must never crash the analyzer / camera pipeline.
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
