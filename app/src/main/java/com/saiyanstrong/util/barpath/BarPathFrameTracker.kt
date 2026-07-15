package com.saiyanstrong.util.barpath

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import com.saiyanstrong.domain.model.BarPathSample
import com.saiyanstrong.domain.util.GyroTimeline
import com.saiyanstrong.domain.util.ShakeCompensator
import javax.inject.Inject
import kotlin.math.hypot

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
class BarPathFrameTracker @Inject constructor(
    private val barPathVideoDecoder: BarPathVideoDecoder
) {

    /**
     * The video's DISPLAY dimensions (width, height) — rotation-applied, so they match both the
     * coordinate space of the tracked centroids (getFrameAtTime returns rotation-applied frames)
     * and what a player renders. Needed to map centroids onto the replay video. (0,0) if unknown.
     */
    fun videoDimensions(videoPath: String): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) h to w else w to h
        } finally {
            retriever.release()
        }
    }

    /** First frame of the video, for the calibration screen (tap two points of known distance). */
    fun extractFirstFrame(videoPath: String): Bitmap? = extractFrameAt(videoPath, 0L)

    /** The frame nearest [ms] — used to sample the marker colour at the point/time the user tapped
     * in the live player, and as the still for the scale step. Rotation-applied, like every frame
     * this class produces. */
    fun extractFrameAt(videoPath: String, ms: Long): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            retriever.getFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
        } finally {
            retriever.release()
        }
    }

    /**
     * @param sampleIntervalMs how often to sample the video. Null (the default) derives it from
     * the video's own recorded capture frame rate, so a high-speed recording actually yields
     * more samples instead of being extracted at a fixed ~30fps regardless of source frame
     * rate — recording faster footage did nothing useful before this without also lowering this
     * interval. Falls back to 33ms (~30fps) when the encoder didn't report a capture frame rate
     * (not guaranteed present on every device/encoder).
     * @param downscaleFactor frames are shrunk before scanning for the marker — exact pixel
     * precision isn't needed for a centroid, and scanning a full-resolution frame per sample is
     * needlessly slow. 0.5 (half-res) rather than a more aggressive shrink: a real gym marker is
     * often small in frame (a sticker/tape band on the sleeve), and at 0.25 it collapsed to a
     * handful of pixels that fell below [MIN_MARKER_PIXELS] or got dropped by the nearest-neighbor
     * downscale entirely — the "dot doesn't sit on the marker" failure. Half-res keeps 4× the
     * marker pixels; the frame count is still capped by MAX_SAMPLES so total scan time stays bounded.
     * @param useStreamingDecode opt-in: extract frames via [BarPathVideoDecoder]'s sequential
     * MediaCodec decode instead of per-timestamp getFrameAtTime seeks (faster at high fps, but
     * device-fragile and unmeasured — falls back to the retriever path automatically on any
     * decode failure or if it yields no frames). Defaults off so the proven path stays default.
     */
    fun trackMarker(
        videoPath: String,
        colorProfile: MarkerColorProfile,
        sampleIntervalMs: Long? = null,
        downscaleFactor: Double = 0.5,
        useStreamingDecode: Boolean = false,
        gyroTimeline: GyroTimeline? = null,
        focalMm: Double = 0.0,
        sensorWidthMm: Double = 0.0,
        videoStartUptimeNs: Long = 0L,
        startMs: Long = 0L,
        initialVideoX: Double? = null,
        initialVideoY: Double? = null,
        onProgress: ((Float) -> Unit)? = null,
        onSample: ((BarPathSample) -> Unit)? = null
    ): List<BarPathSample> {
        val (rawIntervalMs, durationMs) = readTiming(videoPath, sampleIntervalMs)
        if (durationMs <= 0L) return emptyList()
        // Each frame is a slow getFrameAtTime seek+decode, so an uncapped interval (a long clip, or
        // a high-fps recording reporting a tiny interval) means hundreds/thousands of seeks and a
        // "stuck on Tracking…" screen. Cap the frame count: widen the interval so no clip produces
        // more than MAX_SAMPLES samples. Cap is over the tracked span [startMs, durationMs].
        val trackedSpanMs = (durationMs - startMs).coerceAtLeast(1L)
        val intervalMs = maxOf(rawIntervalMs, trackedSpanMs / MAX_SAMPLES).coerceAtLeast(MIN_SAMPLE_INTERVAL_MS)
        val fromMs = startMs.coerceIn(0L, durationMs)

        // Builds a FRESH sample list from whatever frame source drives it — so a failed streaming
        // attempt's partial list is discarded (local to that call), and the retriever fallback
        // starts clean with no risk of double-counting. Each found sample is also streamed via
        // [onSample] for the live overlay (the dot follows as tracking progresses).
        fun collect(drive: (onFrame: (Bitmap, Long) -> Unit) -> Unit): List<BarPathSample> {
            val samples = mutableListOf<BarPathSample>()
            // Seed with the tapped point so frame 1 picks the blob NEAREST the tap (via the existing
            // nearest-neighbor chooseTrackedBlob), not just the largest — robust when there's more
            // than one object of the marker's colour in frame (a reflection, a second marker).
            var previousCentroid: Pair<Double, Double>? =
                if (initialVideoX != null && initialVideoY != null) initialVideoX to initialVideoY else null
            var focalLengthPx = 0.0
            drive { frame, timestampMs ->
                if (trackedSpanMs > 0L) {
                    onProgress?.invoke(((timestampMs - fromMs).toFloat() / trackedSpanMs).coerceIn(0f, 1f))
                }
                if (focalLengthPx == 0.0 && focalMm > 0.0 && sensorWidthMm > 0.0) {
                    focalLengthPx = ShakeCompensator.focalLengthPx(focalMm, sensorWidthMm, frame.width)
                }
                findMarkerCentroid(frame, colorProfile, downscaleFactor, previousCentroid)?.let { tracked ->
                    var x = tracked.xPx
                    var y = tracked.yPx
                    if (gyroTimeline != null && !gyroTimeline.isEmpty && focalLengthPx > 0.0) {
                        val uptimeNs = videoStartUptimeNs + timestampMs * 1_000_000L
                        val (cumX, cumY) = gyroTimeline.cumulativeAngleAt(uptimeNs)
                        val compensated = ShakeCompensator.compensate(x, y, cumX, cumY, focalLengthPx)
                        x = compensated.x
                        y = compensated.y
                    }
                    val sample = BarPathSample(timestampMs, x, y, tracked.diameterPx)
                    samples += sample
                    onSample?.invoke(sample)
                    previousCentroid = x to y
                }
            }
            return samples
        }

        if (useStreamingDecode) {
            val streamed = runCatching {
                collect { onFrame -> barPathVideoDecoder.decodeSampledFrames(videoPath, intervalMs, onFrame) }
            }.getOrNull()
            if (!streamed.isNullOrEmpty()) return streamed
        }
        return collect { onFrame -> driveRetrieverFrames(videoPath, intervalMs, fromMs, durationMs, onFrame) }
    }

    /**
     * Markerless point tracking: follows a small image PATCH around the tapped point ([tapVideoX],
     * [tapVideoY] in full-resolution video pixels) by appearance, so the user can tap the bare bar
     * (a plate edge, collar, bolt) with no coloured marker. Extracts a grayscale template at the mark
     * frame, then per frame searches a window around the previous position for the best NCC match
     * ([TemplateMatcher]). A match below [TEMPLATE_NCC_THRESHOLD] is REJECTED — the previous position
     * is held — so a bad frame can't teleport the point onto something else (the "all over the place"
     * fix). Reuses the same frame-extraction, [startMs], MAX_SAMPLES cap, and [onSample] streaming as
     * [trackMarker]; emits [BarPathSample]s in video-pixel space, so everything downstream is
     * unchanged.
     */
    fun trackTemplate(
        videoPath: String,
        tapVideoX: Double,
        tapVideoY: Double,
        videoWidthPx: Int,
        videoHeightPx: Int,
        startMs: Long,
        onSample: ((BarPathSample) -> Unit)? = null
    ): List<BarPathSample> {
        if (videoWidthPx <= 0 || videoHeightPx <= 0) return emptyList()
        val (rawIntervalMs, durationMs) = readTiming(videoPath, null)
        if (durationMs <= 0L) return emptyList()
        val trackedSpanMs = (durationMs - startMs).coerceAtLeast(1L)
        val intervalMs = maxOf(rawIntervalMs, trackedSpanMs / MAX_SAMPLES).coerceAtLeast(MIN_SAMPLE_INTERVAL_MS)
        val fromMs = startMs.coerceIn(0L, durationMs)

        // Template from the mark frame.
        val startFrame = extractFrameAt(videoPath, fromMs) ?: return emptyList()
        val sgw = (startFrame.width * TEMPLATE_DOWNSCALE).toInt().coerceAtLeast(1)
        val sgh = (startFrame.height * TEMPLATE_DOWNSCALE).toInt().coerceAtLeast(1)
        val startGray = toGray(startFrame, sgw, sgh)
        startFrame.recycle()

        val half = TEMPLATE_PATCH / 2
        val cx0 = (tapVideoX / videoWidthPx * sgw).toInt().coerceIn(half, sgw - half - 1)
        val cy0 = (tapVideoY / videoHeightPx * sgh).toInt().coerceIn(half, sgh - half - 1)
        var template = extractPatch(startGray, sgw, sgh, cx0, cy0, TEMPLATE_PATCH) ?: return emptyList()

        val samples = mutableListOf<BarPathSample>()
        var prevX = cx0
        var prevY = cy0
        driveRetrieverFrames(videoPath, intervalMs, fromMs, durationMs) { frame, timestampMs ->
            val fgw = (frame.width * TEMPLATE_DOWNSCALE).toInt().coerceAtLeast(1)
            val fgh = (frame.height * TEMPLATE_DOWNSCALE).toInt().coerceAtLeast(1)
            val gray = toGray(frame, fgw, fgh)
            val match = TemplateMatcher.bestMatch(
                gray, fgw, fgh, template, TEMPLATE_PATCH, TEMPLATE_PATCH,
                prevX, prevY, TEMPLATE_SEARCH_X, TEMPLATE_SEARCH_Y
            )
            if (match != null && match.score >= TEMPLATE_NCC_THRESHOLD) {
                prevX = match.x
                prevY = match.y
                // Refresh the template on a strong match so it follows appearance change through the
                // rep (lighting/angle) without drifting on the weak, blur-driven matches.
                if (match.score >= TEMPLATE_UPDATE_THRESHOLD) {
                    extractPatch(gray, fgw, fgh, prevX, prevY, TEMPLATE_PATCH)?.let { template = it }
                }
            }
            val xPx = prevX.toDouble() / fgw * videoWidthPx
            val yPx = prevY.toDouble() / fgh * videoHeightPx
            val sample = BarPathSample(timestampMs, xPx, yPx)
            samples += sample
            onSample?.invoke(sample)
        }
        return samples
    }

    /** Downscaled 8-bit grayscale of [bitmap] at [w]×[h] (BT.601 luma), via one bulk getPixels(). */
    private fun toGray(bitmap: Bitmap, w: Int, h: Int): IntArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        scaled.recycle()
        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            gray[i] = (r * 299 + g * 587 + b * 114) / 1000
        }
        return gray
    }

    /** [size]×[size] patch of [gray] centred on ([cx],[cy]); null if it can't fit fully. */
    private fun extractPatch(gray: IntArray, w: Int, h: Int, cx: Int, cy: Int, size: Int): IntArray? {
        val half = size / 2
        val left = cx - half; val top = cy - half
        if (left < 0 || top < 0 || left + size > w || top + size > h) return null
        val patch = IntArray(size * size)
        var i = 0
        for (ty in 0 until size) {
            val row = (top + ty) * w + left
            for (tx in 0 until size) patch[i++] = gray[row + tx]
        }
        return patch
    }

    /**
     * Tracks two independent color markers per frame — the primary marker (returned as each
     * sample's position, exactly like [trackMarker]) and a reference marker a known real-world
     * distance away, used purely to compute a directly-measured pixels-per-meter for that exact
     * frame ([BarPathSample.perFramePixelsPerMeter]). This is a more accurate depth-drift
     * correction than [BarPathSample.apparentDiameterPx]'s single-marker size heuristic — see
     * [com.saiyanstrong.domain.usecase.AnalyzeBarPathUseCase], which uses one or the other, never
     * both (the two aren't additive corrections for the same effect).
     *
     * Decodes each video frame once (not twice) and runs blob detection against it for both
     * color profiles — doubling the CPU cost per frame, not the I/O cost of seeking/decoding,
     * which is the more expensive part (see [deriveSampleIntervalMs]'s performance note).
     *
     * @param referenceDistanceMeters the known real-world distance between the two markers.
     * If the reference marker isn't detected in a given frame (occlusion) but the primary marker
     * is, the last successfully-measured pixels-per-meter is carried forward for that sample
     * rather than leaving it unmeasured.
     */
    fun trackMarkerPair(
        videoPath: String,
        primaryColorProfile: MarkerColorProfile,
        referenceColorProfile: MarkerColorProfile,
        referenceDistanceMeters: Double,
        sampleIntervalMs: Long? = null,
        downscaleFactor: Double = 0.25,
        useStreamingDecode: Boolean = false,
        gyroTimeline: GyroTimeline? = null,
        focalMm: Double = 0.0,
        sensorWidthMm: Double = 0.0,
        videoStartUptimeNs: Long = 0L
    ): List<BarPathSample> {
        val (intervalMs, durationMs) = readTiming(videoPath, sampleIntervalMs)
        if (durationMs <= 0L) return emptyList()

        fun collect(drive: (onFrame: (Bitmap, Long) -> Unit) -> Unit): List<BarPathSample> {
            val samples = mutableListOf<BarPathSample>()
            var previousPrimaryCentroid: Pair<Double, Double>? = null
            var previousReferenceCentroid: Pair<Double, Double>? = null
            var lastKnownPpm: Double? = null
            var focalLengthPx = 0.0
            drive { frame, timestampMs ->
                if (focalLengthPx == 0.0 && focalMm > 0.0 && sensorWidthMm > 0.0) {
                    focalLengthPx = ShakeCompensator.focalLengthPx(focalMm, sensorWidthMm, frame.width)
                }
                val scaledWidth = (frame.width * downscaleFactor).toInt().coerceAtLeast(1)
                val scaledHeight = (frame.height * downscaleFactor).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(frame, scaledWidth, scaledHeight, false)

                val primary = findMarkerCentroidInScaledBitmap(
                    scaled, primaryColorProfile, downscaleFactor, previousPrimaryCentroid
                )
                val reference = findMarkerCentroidInScaledBitmap(
                    scaled, referenceColorProfile, downscaleFactor, previousReferenceCentroid
                )
                scaled.recycle()

                if (primary != null) {
                    var px = primary.xPx
                    var py = primary.yPx
                    if (gyroTimeline != null && !gyroTimeline.isEmpty && focalLengthPx > 0.0) {
                        val uptimeNs = videoStartUptimeNs + timestampMs * 1_000_000L
                        val (cumX, cumY) = gyroTimeline.cumulativeAngleAt(uptimeNs)
                        val compensated = ShakeCompensator.compensate(px, py, cumX, cumY, focalLengthPx)
                        px = compensated.x
                        py = compensated.y
                    }
                    previousPrimaryCentroid = px to py
                    
                    if (reference != null) {
                        var rx = reference.xPx
                        var ry = reference.yPx
                        if (gyroTimeline != null && !gyroTimeline.isEmpty && focalLengthPx > 0.0) {
                            val uptimeNs = videoStartUptimeNs + timestampMs * 1_000_000L
                            val (cumX, cumY) = gyroTimeline.cumulativeAngleAt(uptimeNs)
                            val compensated = ShakeCompensator.compensate(rx, ry, cumX, cumY, focalLengthPx)
                            rx = compensated.x
                            ry = compensated.y
                        }
                        previousReferenceCentroid = rx to ry
                        val pixelDist = hypot(px - rx, py - ry)
                        lastKnownPpm = pixelDist / referenceDistanceMeters
                    }
                    samples += BarPathSample(timestampMs, px, py, primary.diameterPx, lastKnownPpm)
                }
            }
            return samples
        }

        if (useStreamingDecode) {
            val streamed = runCatching {
                collect { onFrame -> barPathVideoDecoder.decodeSampledFrames(videoPath, intervalMs, onFrame) }
            }.getOrNull()
            if (!streamed.isNullOrEmpty()) return streamed
        }
        return collect { onFrame -> driveRetrieverFrames(videoPath, intervalMs, 0L, durationMs, onFrame) }
    }

    /** (sampleIntervalMs, durationMs) — a lightweight metadata-only read, no frame decode. */
    private fun readTiming(videoPath: String, sampleIntervalMs: Long?): Pair<Long, Long> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            (sampleIntervalMs ?: deriveSampleIntervalMs(retriever)) to durationMs
        } finally {
            retriever.release()
        }
    }

    /**
     * The proven frame source: per-timestamp getFrameAtTime seeks over the [startMs, durationMs]
     * grid. Each [Bitmap] is recycled right after [onFrame] returns (consume it synchronously) —
     * the grid timestamp is passed through as the sample time.
     */
    private fun driveRetrieverFrames(
        videoPath: String,
        intervalMs: Long,
        startMs: Long,
        durationMs: Long,
        onFrame: (Bitmap, Long) -> Unit
    ) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoPath)
            var timestampMs = startMs
            while (timestampMs <= durationMs) {
                val frame = retriever.getFrameAtTime(timestampMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                if (frame != null) {
                    onFrame(frame, timestampMs)
                    frame.recycle()
                }
                timestampMs += intervalMs
            }
        } finally {
            retriever.release()
        }
    }

    /**
     * METADATA_KEY_CAPTURE_FRAMERATE isn't guaranteed to be present — many encoders/devices
     * don't report it, in which case this falls back to the historical 33ms (~30fps) default.
     * NOTE: at high frame rates (e.g. 120fps -> ~8ms interval) this means many more individual
     * seek+decode calls via getFrameAtTime, which is genuinely slower — not yet measured on a
     * real device this session, flagged as a real performance unknown, not assumed fine.
     */
    private fun deriveSampleIntervalMs(retriever: MediaMetadataRetriever): Long {
        val captureFps = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            ?.toFloatOrNull()
        return if (captureFps != null && captureFps > 0f) {
            (1000.0 / captureFps).toLong().coerceAtLeast(MIN_SAMPLE_INTERVAL_MS)
        } else {
            DEFAULT_SAMPLE_INTERVAL_MS
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
        val result = findMarkerCentroidInScaledBitmap(scaled, colorProfile, downscaleFactor, previousCentroidPx)
        scaled.recycle()
        return result
    }

    /**
     * Same centroid-finding logic as [findMarkerCentroid], but takes an already-downscaled
     * bitmap — lets [trackMarkerPair] decode/downscale each frame once and run this twice (once
     * per marker color profile) instead of decoding the same frame twice.
     *
     * @param previousCentroidPx the last tracked position, in the SAME original-frame
     * coordinate space this function returns, or null for the first frame.
     */
    private fun findMarkerCentroidInScaledBitmap(
        scaled: Bitmap,
        colorProfile: MarkerColorProfile,
        downscaleFactor: Double,
        previousCentroidPx: Pair<Double, Double>?
    ): TrackedPoint? {
        val scaledWidth = scaled.width
        val scaledHeight = scaled.height

        // Bulk getPixels() (one array copy) instead of per-pixel getPixel() (a JNI call each) —
        // at ~50k+ pixels/frame × 150 frames that's the difference between snappy and "frozen".
        val pixelCount = scaledWidth * scaledHeight
        val pixels = IntArray(pixelCount)
        scaled.getPixels(pixels, 0, scaledWidth, 0, 0, scaledWidth, scaledHeight)
        val mask = BooleanArray(pixelCount)
        val weights = DoubleArray(pixelCount)
        for (idx in 0 until pixelCount) {
            val pixel = pixels[idx]
            val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
            val isMatch = colorProfile.matches(r, g, b)
            mask[idx] = isMatch
            if (isMatch) weights[idx] = colorProfile.matchScore(r, g, b).coerceAtLeast(MIN_WEIGHT)
        }

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

        const val DEFAULT_SAMPLE_INTERVAL_MS = 33L
        const val MIN_SAMPLE_INTERVAL_MS = 5L // sanity floor, ~200fps worst case

        // Markerless template tracking (trackTemplate) — all in downscaled working pixels.
        const val TEMPLATE_DOWNSCALE = 0.5   // work at half-res: precise enough, ~4× faster than full
        const val TEMPLATE_PATCH = 24        // patch side (px @ downscale) ~= 48px full-res
        // Asymmetric per-frame search: a barbell moves mostly VERTICALLY, so allow far more travel in
        // y than x. Wide-y also lets it re-acquire after a blurred stretch. (@downscale px.)
        const val TEMPLATE_SEARCH_X = 28     // ~56px full-res
        const val TEMPLATE_SEARCH_Y = 60     // ~120px full-res — covers fast descent/ascent per frame
        // Accept threshold kept LOW so motion-blurred frames (which drop NCC) are still followed
        // rather than held — the bounded search window keeps a weak match from running off to
        // background. Holding through blur was why the dot stuck at the top and never followed down.
        const val TEMPLATE_NCC_THRESHOLD = 0.25
        // Only refresh the template from a STRONG match, so it follows gradual appearance/lighting
        // change through the rep without drifting onto whatever a weak match happened to land on.
        const val TEMPLATE_UPDATE_THRESHOLD = 0.6

        // Upper bound on sampled frames per clip — each is a slow getFrameAtTime seek+decode, so
        // this caps worst-case tracking time (and the "stuck on Tracking…" perception) regardless
        // of clip length or reported frame rate. Plenty of temporal resolution for one rep.
        const val MAX_SAMPLES = 150L
    }
}
