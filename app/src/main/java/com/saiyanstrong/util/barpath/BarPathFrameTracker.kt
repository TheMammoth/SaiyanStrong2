package com.saiyanstrong.util.barpath

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import com.saiyanstrong.domain.model.BarPathSample
import javax.inject.Inject

internal data class Blob(
    val centroidX: Double,
    val centroidY: Double,
    val size: Int,
    /** Bounding-box diameter (larger of width/height, in pixels) — feeds depth-drift correction. */
    val diameterPx: Double = 0.0
)

/**
 * 4-connected BFS flood fill over a boolean match mask — separates distinct matched objects.
 * Connectivity (which pixels belong to a blob) is driven purely by [mask], exactly as before;
 * [weights] (0.0 for non-matching pixels, [MarkerColorProfile.matchScore] for matching ones)
 * only changes how a blob's CENTROID is computed within that unchanged shape — a pixel that
 * matches but scores poorly still holds the blob together, it just barely pulls the centroid
 * toward itself. Falls back to an unweighted (size-based) centroid if every pixel in a blob
 * happens to score exactly 0, so a degenerate all-zero-weight blob never divides by zero.
 */
internal fun findBlobs(mask: BooleanArray, weights: DoubleArray, width: Int, height: Int): List<Blob> {
    val visited = BooleanArray(mask.size)
    val blobs = mutableListOf<Blob>()
    val queue = ArrayDeque<Int>()

    for (start in mask.indices) {
        if (!mask[start] || visited[start]) continue
        var sumXWeighted = 0.0
        var sumYWeighted = 0.0
        var sumWeight = 0.0
        var sumX = 0.0
        var sumY = 0.0
        var size = 0
        var minX = Int.MAX_VALUE; var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE; var maxY = Int.MIN_VALUE
        queue.clear()
        queue.add(start)
        visited[start] = true
        while (queue.isNotEmpty()) {
            val idx = queue.removeFirst()
            val x = idx % width
            val y = idx / width
            val w = weights[idx]
            sumXWeighted += x * w
            sumYWeighted += y * w
            sumWeight += w
            sumX += x
            sumY += y
            size++
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
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
        val (centroidX, centroidY) = if (sumWeight > 0.0) {
            sumXWeighted / sumWeight to sumYWeighted / sumWeight
        } else {
            sumX / size to sumY / size
        }
        val diameterPx = maxOf(maxX - minX + 1, maxY - minY + 1).toDouble()
        blobs += Blob(centroidX, centroidY, size, diameterPx)
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
                    findMarkerCentroid(frame, colorProfile, downscaleFactor, previousCentroid)?.let { tracked ->
                        samples += BarPathSample(timestampMs, tracked.xPx, tracked.yPx, tracked.diameterPx)
                        previousCentroid = tracked.xPx to tracked.yPx
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

    /** Centroid + apparent bounding-box diameter, both already scaled to full-frame pixels. */
    private data class TrackedPoint(val xPx: Double, val yPx: Double, val diameterPx: Double)

    /**
     * @param previousCentroidPx the last tracked position, in the SAME original-frame
     * coordinate space this function returns, or null for the first frame.
     */
    private fun findMarkerCentroid(
        frame: Bitmap,
        colorProfile: MarkerColorProfile,
        downscaleFactor: Double,
        previousCentroidPx: Pair<Double, Double>?
    ): TrackedPoint? {
        val scaledWidth = (frame.width * downscaleFactor).toInt().coerceAtLeast(1)
        val scaledHeight = (frame.height * downscaleFactor).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(frame, scaledWidth, scaledHeight, false)

        val mask = BooleanArray(scaledWidth * scaledHeight)
        val weights = DoubleArray(scaledWidth * scaledHeight)
        for (y in 0 until scaledHeight) {
            for (x in 0 until scaledWidth) {
                val pixel = scaled.getPixel(x, y)
                val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
                val idx = y * scaledWidth + x
                val isMatch = colorProfile.matches(r, g, b)
                mask[idx] = isMatch
                if (isMatch) weights[idx] = colorProfile.matchScore(r, g, b).coerceAtLeast(MIN_WEIGHT)
            }
        }
        scaled.recycle()

        val blobs = findBlobs(mask, weights, scaledWidth, scaledHeight).filter { it.size >= MIN_MARKER_PIXELS }
        val previousScaled = previousCentroidPx?.let { (it.first * downscaleFactor) to (it.second * downscaleFactor) }
        val chosen = chooseTrackedBlob(blobs, previousScaled) ?: return null

        // Centroid and diameter were computed in downscaled coordinates — scale back up.
        return TrackedPoint(
            xPx = chosen.centroidX / downscaleFactor,
            yPx = chosen.centroidY / downscaleFactor,
            diameterPx = chosen.diameterPx / downscaleFactor
        )
    }

    private companion object {
        const val MIN_MARKER_PIXELS = 10

        // A matching pixel always contributes at least a little weight, even if matchScore()
        // rounds to 0 at the tolerance boundary — keeps its contribution to the centroid
        // proportionally tiny rather than letting a whole blob's weight vanish to 0 and force
        // the unweighted fallback for what could otherwise be a legitimately weighted blob.
        const val MIN_WEIGHT = 0.01
    }
}
