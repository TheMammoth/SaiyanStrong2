package com.saiyanstrong.util.barpath

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import com.saiyanstrong.domain.model.BarPathSample
import javax.inject.Inject

/**
 * Extracts frames from a recorded video and tracks the marker's centroid per frame via
 * [MarkerColorMatcher]. This class is genuinely UNVERIFIED — it compiles and the logic is
 * internally consistent, but marker tracking against a real video (real lighting, real marker
 * visibility, real motion blur) has not been tried against actual footage this session. Expect
 * to come back and tune [MarkerColorMatcher]'s thresholds, the sample interval, or the minimum
 * blob size below once you have a real recorded lift to test against.
 */
class BarPathFrameTracker @Inject constructor() {

    /** First frame of the video, for the calibration screen (tap two points of known distance). */
    fun extractFirstFrame(videoPath: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)
        } finally {
            retriever.release()
        }
    }

    /**
     * @param sampleIntervalMs how often to sample the video. 33ms ≈ 30fps; lower this if your
     * device records at 60fps and you want every frame, at the cost of more processing time.
     * @param downscaleFactor frames are shrunk before scanning for the marker — exact pixel
     * precision isn't needed for a centroid, and scanning a full-resolution frame per sample is
     * needlessly slow.
     */
    fun trackMarker(
        videoPath: String,
        sampleIntervalMs: Long = 33,
        downscaleFactor: Double = 0.25
    ): List<BarPathSample> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            val samples = mutableListOf<BarPathSample>()
            var timestampMs = 0L
            while (timestampMs <= durationMs) {
                val frame = retriever.getFrameAtTime(timestampMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                if (frame != null) {
                    findMarkerCentroid(frame, downscaleFactor)?.let { (x, y) ->
                        samples += BarPathSample(timestampMs, x, y)
                    }
                    frame.recycle()
                }
                timestampMs += sampleIntervalMs
            }
            samples
        } finally {
            retriever.release()
        }
    }

    private fun findMarkerCentroid(frame: Bitmap, downscaleFactor: Double): Pair<Double, Double>? {
        val scaledWidth = (frame.width * downscaleFactor).toInt().coerceAtLeast(1)
        val scaledHeight = (frame.height * downscaleFactor).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(frame, scaledWidth, scaledHeight, false)

        var sumX = 0.0
        var sumY = 0.0
        var matchCount = 0
        for (y in 0 until scaledHeight) {
            for (x in 0 until scaledWidth) {
                val pixel = scaled.getPixel(x, y)
                if (MarkerColorMatcher.matchesRgb(Color.red(pixel), Color.green(pixel), Color.blue(pixel))) {
                    sumX += x
                    sumY += y
                    matchCount++
                }
            }
        }
        scaled.recycle()

        if (matchCount < MIN_MARKER_PIXELS) return null
        // Centroid was computed in downscaled coordinates — scale back up to the original frame.
        return (sumX / matchCount / downscaleFactor) to (sumY / matchCount / downscaleFactor)
    }

    private companion object {
        const val MIN_MARKER_PIXELS = 10
    }
}
