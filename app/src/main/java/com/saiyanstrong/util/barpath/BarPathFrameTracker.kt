package com.saiyanstrong.util.barpath

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import com.saiyanstrong.domain.model.BarPathSample
import javax.inject.Inject

internal data class Blob(val centroidX: Double, val centroidY: Double, val size: Int)

/** 4-connected BFS flood fill over a boolean match mask — separates distinct matched objects. */
internal fun findBlobs(mask: BooleanArray, width: Int, height: Int): List<Blob> {
    val visited = BooleanArray(mask.size)
    val blobs = mutableListOf<Blob>()
    val queue = ArrayDeque<Int>()

    for (start in mask.indices) {
        if (!mask[start] || visited[start]) continue
        var sumX = 0.0
        var sumY = 0.0
        var size = 0
        queue.clear()
        queue.add(start)
        visited[start] = true
        while (queue.isNotEmpty()) {
            val idx = queue.removeFirst()
            val x = idx % width
            val y = idx / width
            sumX += x
            sumY += y
            size++
            for ((nx, ny) in listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)) {
                if (nx in 0 until width && ny in 0 until height) {
                    val nIdx = ny * width + nx
                    if (mask[nIdx] && !visited[nIdx]) {
                        visited[nIdx] = true
                        queue.add(nIdx)
                    }
                }
            }
        }
        blobs += Blob(sumX / size, sumY / size, size)
    }
    return blobs
}

/**
 * A real marker can't teleport across the room in 33ms, so tracking prefers spatial
 * continuity over raw blob size once it has a previous position — this is what rejects a
 * stray pink/magenta object elsewhere in frame. The first frame has nothing to anchor to,
 * so it falls back to the largest blob (the deliberately-placed marker is usually the
 * biggest contiguous patch of the target color).
 */
internal fun chooseTrackedBlob(blobs: List<Blob>, previousCentroid: Pair<Double, Double>?): Blob? {
    if (blobs.isEmpty()) return null
    return if (previousCentroid != null) {
        blobs.minBy { blob ->
            val dx = blob.centroidX - previousCentroid.first
            val dy = blob.centroidY - previousCentroid.second
            dx * dx + dy * dy
        }
    } else {
        blobs.maxBy { it.size }
    }
}

/**
 * Extracts frames from a recorded video and tracks the marker's centroid per frame against a
 * [MarkerColorProfile] sampled from the user's actual marker (not a fixed guessed threshold —
 * see the "tap-to-calibrate color" follow-up). First real-footage test (Sprint 28) surfaced the
 * expected failure mode: a naive centroid of every matching pixel in the frame snaps toward any
 * other object in the room that happens to match the color threshold, producing one huge
 * spurious frame-to-frame jump. Fixed with connected-component blob detection ([findBlobs]) +
 * nearest-neighbor tracking across frames ([chooseTrackedBlob]).
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
        colorProfile: MarkerColorProfile,
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
            var previousCentroid: Pair<Double, Double>? = null
            var timestampMs = 0L
            while (timestampMs <= durationMs) {
                val frame = retriever.getFrameAtTime(timestampMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                if (frame != null) {
                    findMarkerCentroid(frame, colorProfile, downscaleFactor, previousCentroid)?.let { (x, y) ->
                        samples += BarPathSample(timestampMs, x, y)
                        previousCentroid = x to y
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

    /**
     * @param previousCentroidPx the last tracked position, in the SAME original-frame
     * coordinate space this function returns, or null for the first frame.
     */
    private fun findMarkerCentroid(
        frame: Bitmap,
        colorProfile: MarkerColorProfile,
        downscaleFactor: Double,
        previousCentroidPx: Pair<Double, Double>?
    ): Pair<Double, Double>? {
        val scaledWidth = (frame.width * downscaleFactor).toInt().coerceAtLeast(1)
        val scaledHeight = (frame.height * downscaleFactor).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(frame, scaledWidth, scaledHeight, false)

        val mask = BooleanArray(scaledWidth * scaledHeight)
        for (y in 0 until scaledHeight) {
            for (x in 0 until scaledWidth) {
                val pixel = scaled.getPixel(x, y)
                mask[y * scaledWidth + x] =
                    colorProfile.matches(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
            }
        }
        scaled.recycle()

        val blobs = findBlobs(mask, scaledWidth, scaledHeight).filter { it.size >= MIN_MARKER_PIXELS }
        val previousScaled = previousCentroidPx?.let { (it.first * downscaleFactor) to (it.second * downscaleFactor) }
        val chosen = chooseTrackedBlob(blobs, previousScaled) ?: return null

        // Centroid was computed in downscaled coordinates — scale back up to the original frame.
        return (chosen.centroidX / downscaleFactor) to (chosen.centroidY / downscaleFactor)
    }

    private companion object {
        const val MIN_MARKER_PIXELS = 10
    }
}
