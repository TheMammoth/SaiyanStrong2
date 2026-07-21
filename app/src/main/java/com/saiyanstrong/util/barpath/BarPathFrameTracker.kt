package com.saiyanstrong.util.barpath

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import com.saiyanstrong.domain.model.BarPathSample
import com.saiyanstrong.domain.util.GyroTimeline
import com.saiyanstrong.domain.util.ShakeCompensator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.hypot

internal data class Blob(
    val centroidX: Double,
    val centroidY: Double,
    val size: Int,
    /** Bounding-box diameter (larger of width/height, in pixels) — feeds depth-drift correction. */
    val diameterPx: Double = 0.0,
    // Bounding box (inclusive pixel extents) — feeds the calibration region-highlight overlay and
    // the background-clash check. Defaulted so existing test fixtures that build a Blob positionally
    // (centroid/size only) still compile.
    val minX: Int = 0,
    val minY: Int = 0,
    val maxX: Int = 0,
    val maxY: Int = 0
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
        blobs += Blob(centroidX, centroidY, size, diameterPx, minX, minY, maxX, maxY)
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
 * Picks which colour blob is the plate this frame, for drift-free re-detection tracking: among the
 * blobs whose size is within a band of the plate's expected diameter (rejecting a tiny same-colour
 * speck or a merged giant), the one nearest the previous plate position. Because it re-chooses the
 * real plate every frame with a size gate, it can't gradually climb onto a different-coloured body
 * the way an incremental region tracker does. Falls back to all blobs if none are in the size band
 * (then still nearest-wins), and to the largest blob if there's no previous position. Pure/tested.
 */
internal fun choosePlateBlob(
    blobs: List<Blob>,
    previousCentroid: Pair<Double, Double>?,
    expectedDiameter: Double,
    minSizeFraction: Double = 0.4,
    maxSizeFraction: Double = 2.5
): Blob? {
    if (blobs.isEmpty()) return null
    val inBand = if (expectedDiameter > 0.0) {
        blobs.filter { it.diameterPx in (expectedDiameter * minSizeFraction)..(expectedDiameter * maxSizeFraction) }
    } else {
        blobs
    }
    val pool = inBand.ifEmpty { blobs }
    return if (previousCentroid != null) {
        pool.minBy { blob ->
            val dx = blob.centroidX - previousCentroid.first
            val dy = blob.centroidY - previousCentroid.second
            dx * dx + dy * dy
        }
    } else {
        pool.maxBy { it.size }
    }
}

/**
 * Merge a forward and a backward per-frame detection pass (index-aligned by timestamp): prefer the
 * forward detection, fall back to the backward one, null only where both missed. This is why two
 * marks (bottom + top) cover the top misses — the top mark's backward pass is confident exactly
 * where the bottom mark's forward pass is weakest. Pure/unit-tested.
 */
internal fun mergeDetections(
    fwd: List<Pair<Double, Double>?>,
    bwd: List<Pair<Double, Double>?>
): List<Pair<Double, Double>?> {
    val n = maxOf(fwd.size, bwd.size)
    return (0 until n).map { i -> fwd.getOrNull(i) ?: bwd.getOrNull(i) }
}

/**
 * Fill nulls (frames missed by both passes) by linear interpolation between the nearest detected
 * neighbours; leading/trailing nulls clamp to the nearest detection. All-null → empty. Pure/tested.
 */
internal fun fillGaps(points: List<Pair<Double, Double>?>): List<Pair<Double, Double>> {
    val firstKnown = points.indexOfFirst { it != null }
    if (firstKnown < 0) return emptyList()
    val lastKnown = points.indexOfLast { it != null }
    val n = points.size
    val out = arrayOfNulls<Pair<Double, Double>>(n)
    for (i in points.indices) out[i] = points[i]
    for (i in 0 until firstKnown) out[i] = points[firstKnown]
    for (i in lastKnown + 1 until n) out[i] = points[lastKnown]
    var i = firstKnown
    while (i <= lastKnown) {
        if (out[i] != null) { i++; continue }
        val start = i - 1 // known (i > firstKnown here)
        var j = i
        while (out[j] == null) j++ // out[j] known — guaranteed at or before lastKnown
        val p0 = out[start]!!; val p1 = out[j]!!
        val gapLen = (j - start).toDouble()
        for (k in i until j) {
            val f = (k - start) / gapLen
            out[k] = (p0.first + (p1.first - p0.first) * f) to (p0.second + (p1.second - p0.second) * f)
        }
        i = j
    }
    return out.map { it!! }
}

/**
 * Average RGB of the sufficiently-saturated, bright pixels in the [rx,ry,rw,rh] sub-rect of an ARGB
 * grid — the anti-drift guard's COLOUR ANCHOR. Returns null when too few pixels are colourful (a
 * grey/white/chrome region has no distinct colour to anchor to, so the guard stays disabled and the
 * tracker is simply trusted). Anchoring to a real colour (e.g. a plate's blue ring) is what lets the
 * guard tell "still on the plate" from "climbed onto skin/shorts", which grayscale similarity did
 * not do reliably on real footage. Pure/unit-tested.
 */
internal fun regionDominantColor(
    argb: IntArray, w: Int, h: Int, rx: Int, ry: Int, rw: Int, rh: Int,
    minSaturation: Double = 0.30, minValue: Double = 0.25, minFraction: Double = 0.08
): IntArray? {
    var sr = 0L; var sg = 0L; var sb = 0L; var n = 0; var total = 0
    for (y in ry until ry + rh) {
        if (y < 0 || y >= h) continue
        for (x in rx until rx + rw) {
            if (x < 0 || x >= w) continue
            val p = argb[y * w + x]
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            total++
            val (_, s, v) = MarkerColorMatcher.rgbToHsv(r, g, b)
            if (s >= minSaturation && v >= minValue) { sr += r; sg += g; sb += b; n++ }
        }
    }
    if (total == 0 || n < 10 || n < total * minFraction) return null
    return intArrayOf((sr / n).toInt(), (sg / n).toInt(), (sb / n).toInt())
}

/** Fraction of the [rx,ry,rw,rh] sub-rect of an ARGB grid that matches [profile] — how "plate
 * coloured" the tracked box still is. Pure/unit-tested. */
internal fun regionColorFraction(
    argb: IntArray, w: Int, h: Int, rx: Int, ry: Int, rw: Int, rh: Int,
    profile: MarkerColorProfile
): Double {
    var match = 0; var total = 0
    for (y in ry until ry + rh) {
        if (y < 0 || y >= h) continue
        for (x in rx until rx + rw) {
            if (x < 0 || x >= w) continue
            val p = argb[y * w + x]
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            total++
            if (profile.matches(r, g, b)) match++
        }
    }
    return if (total == 0) 0.0 else match.toDouble() / total
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
    @ApplicationContext private val context: Context,
    private val barPathVideoDecoder: BarPathVideoDecoder
) {

    /**
     * Tracks the tapped object (the weight plate) through the video with OpenCV's [TrackerVit] DNN
     * tracker — the replacement for the hand-rolled colour/blob/template tracking. Extracts frames
     * the same way [trackMarker] does ([driveRetrieverFrames] over the [startMs, duration] grid,
     * capped at [MAX_SAMPLES]), but per frame runs the tracker instead of a pixel scan:
     *  - frame 1: init the tracker with [initBox] (in full-res video px, from the tap); emit its centre.
     *  - each later frame: [VitBarTracker.update] → box centre → [BarPathSample] in video px.
     *  - a low-confidence frame HOLDS the last position (re-emits it) rather than teleporting.
     * Streams each sample via [onSample] so the live-player dot follows as tracking progresses.
     * Returns an empty list (caller shows a retry message) if OpenCV can't init or the model is
     * missing — never throws for those.
     */
    fun trackWithVit(
        videoPath: String,
        initBox: BarInitBox,
        startMs: Long = 0L,
        onProgress: ((Float) -> Unit)? = null,
        onSample: ((BarPathSample) -> Unit)? = null
    ): List<BarPathSample> {
        if (!OpenCvInitializer.ensureInitialized()) return emptyList()
        val modelPath = OpenCvInitializer.vitModelPath(context) ?: return emptyList()
        val tracker = VitBarTracker.create(modelPath) ?: return emptyList()

        val (rawIntervalMs, durationMs) = readTiming(videoPath, null)
        if (durationMs <= 0L) return emptyList()
        val trackedSpanMs = (durationMs - startMs).coerceAtLeast(1L)
        val intervalMs = maxOf(rawIntervalMs, trackedSpanMs / MAX_SAMPLES).coerceAtLeast(MIN_SAMPLE_INTERVAL_MS)
        val fromMs = startMs.coerceIn(0L, durationMs)

        val samples = mutableListOf<BarPathSample>()
        var initialized = false
        // Anti-drift guard, COLOUR-anchored. At the mark frame we sample the dominant saturated
        // colour inside the box (e.g. a plate's blue ring); each frame we check TrackerVit's box
        // still contains enough of that colour. When the box gradually climbs off the plate onto the
        // lifter's skin/shorts (a different colour) — the deadlift-lockout failure — we HOLD the last
        // good position and re-init the tracker there so it re-acquires when the plate returns. A
        // grey/white/chrome mark has no distinct colour to anchor to, so the guard stays disabled and
        // the tracker is simply trusted — it never regresses a target it can't anchor. A hard cap on
        // consecutive holds means it can never freeze the whole clip.
        var colorAnchor: MarkerColorProfile? = null
        var lastGoodX = 0.0
        var lastGoodY = 0.0
        var lastGoodBox = initBox
        var consecutiveRejects = 0
        driveRetrieverFrames(videoPath, intervalMs, fromMs, durationMs) { frame, timestampMs ->
            if (trackedSpanMs > 0L) {
                onProgress?.invoke(((timestampMs - fromMs).toFloat() / trackedSpanMs).coerceIn(0f, 1f))
            }
            val gw = (frame.width * GUARD_DOWNSCALE).toInt().coerceAtLeast(1)
            val gh = (frame.height * GUARD_DOWNSCALE).toInt().coerceAtLeast(1)
            val argb = scaledArgb(frame, gw, gh)

            if (!initialized) {
                runCatching { tracker.init(frame, initBox) }.onFailure { return@driveRetrieverFrames }
                initialized = true
                val (cx0, cy0) = VitBarTrackerSupport.boxCenter(initBox.x, initBox.y, initBox.width, initBox.height)
                lastGoodX = cx0; lastGoodY = cy0; lastGoodBox = initBox
                regionDominantColor(
                    argb, gw, gh,
                    (initBox.x * GUARD_DOWNSCALE).toInt(), (initBox.y * GUARD_DOWNSCALE).toInt(),
                    (initBox.width * GUARD_DOWNSCALE).toInt(), (initBox.height * GUARD_DOWNSCALE).toInt()
                )?.let { colorAnchor = MarkerColorProfile.sample(it[0], it[1], it[2]) }
                val sample = BarPathSample(timestampMs, cx0, cy0, initBox.width.toDouble())
                samples += sample; onSample?.invoke(sample)
                return@driveRetrieverFrames
            }

            val box = tracker.update(frame)
            var outX = lastGoodX
            var outY = lastGoodY
            var outDiam = lastGoodBox.width.toDouble()
            if (box != null) {
                val (cx, cy) = VitBarTrackerSupport.boxCenter(box.x, box.y, box.width, box.height)
                val trackedBox = BarInitBox(box.x, box.y, box.width, box.height)
                val disp = hypot(cx - lastGoodX, cy - lastGoodY)
                val teleport = disp > box.width * MAX_JUMP_FRACTION
                val anchor = colorAnchor
                val colorLost = anchor != null && regionColorFraction(
                    argb, gw, gh,
                    (box.x * GUARD_DOWNSCALE).toInt(), (box.y * GUARD_DOWNSCALE).toInt(),
                    (box.width * GUARD_DOWNSCALE).toInt(), (box.height * GUARD_DOWNSCALE).toInt(),
                    anchor
                ) < MIN_PLATE_FRACTION
                // Hold on a clear drift (teleport, or the box left the plate's colour) — but never
                // for more than MAX_CONSECUTIVE_REJECTS frames, so a bad anchor can't freeze the clip.
                if ((teleport || colorLost) && consecutiveRejects < MAX_CONSECUTIVE_REJECTS) {
                    runCatching { tracker.init(frame, lastGoodBox) }
                    consecutiveRejects++
                } else {
                    outX = cx; outY = cy; outDiam = box.width.toDouble()
                    lastGoodX = cx; lastGoodY = cy; lastGoodBox = trackedBox
                    consecutiveRejects = 0
                }
            } else {
                // TrackerVit lost confidence — hold last good and re-seed there.
                runCatching { tracker.init(frame, lastGoodBox) }
            }
            val sample = BarPathSample(timestampMs, outX, outY, outDiam)
            samples += sample; onSample?.invoke(sample)
        }
        return samples
    }

    /** Whole frame downscaled to [w]×[h] as an ARGB pixel grid (one bulk getPixels). */
    private fun scaledArgb(frame: Bitmap, w: Int, h: Int): IntArray {
        val scaled = Bitmap.createScaledBitmap(frame, w, h, true)
        val px = IntArray(w * h)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        scaled.recycle()
        return px
    }

    /** Plate centre + apparent diameter (full-resolution video px) for the selection preview. */
    data class PlateHit(val centerX: Double, val centerY: Double, val diameterPx: Double)

    /**
     * One-tap "magic wand" plate selection on a single [frame] at ([videoX],[videoY]) in full-res
     * video px — floods the plate's colour region ([PlateSegmenter]) and returns its centre + size
     * for the on-screen selection preview. Null if nothing plate-like is under the tap.
     */
    fun segmentPlate(frame: Bitmap, videoX: Double, videoY: Double): PlateHit? {
        val gw = (frame.width * GUARD_DOWNSCALE).toInt().coerceAtLeast(1)
        val gh = (frame.height * GUARD_DOWNSCALE).toInt().coerceAtLeast(1)
        val argb = scaledArgb(frame, gw, gh)
        val sel = PlateSegmenter.segment(
            argb, gw, gh, (videoX * GUARD_DOWNSCALE).toInt(), (videoY * GUARD_DOWNSCALE).toInt()
        ) ?: return null
        return PlateHit(sel.centroidX / GUARD_DOWNSCALE, sel.centroidY / GUARD_DOWNSCALE, sel.diameterPx / GUARD_DOWNSCALE)
    }

    /**
     * Drift-free plate tracking by RE-DETECTION. On the mark frame it segments the plate at the tap
     * ([PlateSegmenter]) to get the plate's colour model + expected size; then, every frame, it
     * finds the colour blobs and re-picks the plate ([choosePlateBlob], size- and position-gated).
     * Because each frame independently re-locates the real plate, drift can't accumulate and the
     * point can't gradually climb onto a different-coloured body. Holds the last position when no
     * plate blob qualifies (occlusion/blur), then re-detects when it reappears. Reuses the same
     * frame extraction, [startMs], [MAX_SAMPLES] cap, [onProgress] and [onSample] as the others.
     */
    fun trackPlateByRedetection(
        videoPath: String,
        tapVideoX: Double,
        tapVideoY: Double,
        startMs: Long = 0L,
        onProgress: ((Float) -> Unit)? = null,
        onSample: ((BarPathSample) -> Unit)? = null
    ): List<BarPathSample> {
        val (rawIntervalMs, durationMs) = readTiming(videoPath, null)
        if (durationMs <= 0L) return emptyList()
        val trackedSpanMs = (durationMs - startMs).coerceAtLeast(1L)
        val intervalMs = maxOf(rawIntervalMs, trackedSpanMs / MAX_SAMPLES).coerceAtLeast(MIN_SAMPLE_INTERVAL_MS)
        val fromMs = startMs.coerceIn(0L, durationMs)

        var profile: MarkerColorProfile? = null
        var expectedDiameter = 0.0
        var prevCentroid: Pair<Double, Double>? = null
        var initialized = false
        val samples = mutableListOf<BarPathSample>()

        driveRetrieverFrames(videoPath, intervalMs, fromMs, durationMs) { frame, timestampMs ->
            if (trackedSpanMs > 0L) {
                onProgress?.invoke(((timestampMs - fromMs).toFloat() / trackedSpanMs).coerceIn(0f, 1f))
            }
            val gw = (frame.width * GUARD_DOWNSCALE).toInt().coerceAtLeast(1)
            val gh = (frame.height * GUARD_DOWNSCALE).toInt().coerceAtLeast(1)
            val argb = scaledArgb(frame, gw, gh)

            if (!initialized) {
                initialized = true
                // Segment the plate ONLY on the mark frame (the tap is for this frame). If it fails,
                // profile stays null and later frames are skipped — the caller shows a retry.
                val sel = PlateSegmenter.segment(
                    argb, gw, gh, (tapVideoX * GUARD_DOWNSCALE).toInt(), (tapVideoY * GUARD_DOWNSCALE).toInt()
                ) ?: return@driveRetrieverFrames
                profile = sel.colorProfile
                expectedDiameter = sel.diameterPx
                prevCentroid = sel.centroidX to sel.centroidY // downscaled space (blob space)
                val sample = BarPathSample(
                    timestampMs, sel.centroidX / GUARD_DOWNSCALE, sel.centroidY / GUARD_DOWNSCALE,
                    sel.diameterPx / GUARD_DOWNSCALE
                )
                samples += sample; onSample?.invoke(sample)
                return@driveRetrieverFrames
            }

            val p = profile ?: return@driveRetrieverFrames // segmentation failed — skip
            val mask = BooleanArray(argb.size)
            val weights = DoubleArray(argb.size)
            for (i in argb.indices) {
                val px = argb[i]
                val r = (px shr 16) and 0xFF; val g = (px shr 8) and 0xFF; val b = px and 0xFF
                if (p.matches(r, g, b)) { mask[i] = true; weights[i] = p.matchScore(r, g, b).coerceAtLeast(MIN_WEIGHT) }
            }
            val blobs = findBlobs(mask, weights, gw, gh).filter { it.size >= MIN_MARKER_PIXELS }
            val chosen = choosePlateBlob(blobs, prevCentroid, expectedDiameter)
            val sample = if (chosen != null) {
                prevCentroid = chosen.centroidX to chosen.centroidY
                BarPathSample(
                    timestampMs, chosen.centroidX / GUARD_DOWNSCALE, chosen.centroidY / GUARD_DOWNSCALE,
                    chosen.diameterPx / GUARD_DOWNSCALE
                )
            } else {
                samples.lastOrNull()?.copy(timestampMs = timestampMs) // occlusion — hold
            }
            if (sample != null) { samples += sample; onSample?.invoke(sample) }
        }
        return samples
    }

    /**
     * TWO-MARK plate tracking (bottom + top). Segments the plate at BOTH marks, builds one combined
     * colour model spanning both lighting conditions, then re-detects the plate forward from the
     * earlier mark and backward from the later mark over the between-marks range and merges them
     * ([mergeDetections] + [fillGaps]) — so a miss near the top (where the plate is backlit) is
     * covered by the top mark's backward pass. Falls back to single-mark [trackPlateByRedetection]
     * if one segmentation fails; empty if both fail. Tap coords in full-res video px.
     */
    fun trackPlateTwoMark(
        videoPath: String,
        tapAX: Double, tapAY: Double, atAMs: Long,
        tapBX: Double, tapBY: Double, atBMs: Long,
        onProgress: ((Float) -> Unit)? = null,
        onSample: ((BarPathSample) -> Unit)? = null
    ): List<BarPathSample> {
        val selA = segmentAtTime(videoPath, atAMs, tapAX, tapAY)
        val selB = segmentAtTime(videoPath, atBMs, tapBX, tapBY)
        if (selA == null && selB == null) return emptyList()
        // One mark failed — fall back to single-mark tracking from the good one.
        if (selA == null || selB == null) {
            val (tx, ty, ms) = if (selA != null) Triple(tapAX, tapAY, atAMs) else Triple(tapBX, tapBY, atBMs)
            return trackPlateByRedetection(videoPath, tx, ty, ms, onProgress, onSample)
        }

        val (rawIntervalMs, durationMs) = readTiming(videoPath, null)
        if (durationMs <= 0L) return emptyList()
        val loMs = minOf(atAMs, atBMs).coerceIn(0L, durationMs)
        val hiMs = maxOf(atAMs, atBMs).coerceIn(0L, durationMs)
        val spanMs = (hiMs - loMs).coerceAtLeast(1L)
        val intervalMs = maxOf(rawIntervalMs, spanMs / MAX_SAMPLES).coerceAtLeast(MIN_SAMPLE_INTERVAL_MS)

        val timestamps = ArrayList<Long>()
        var t = loMs
        while (t <= hiMs) { timestamps.add(t); t += intervalMs }
        if (timestamps.isEmpty()) timestamps.add(loMs)

        val combinedProfile = MarkerColorRangeBuilder.build(selA.samples + selB.samples) ?: selA.colorProfile
        val expectedDiameter = (selA.diameterPx + selB.diameterPx) / 2.0 // downscaled space
        val early = if (atAMs <= atBMs) selA else selB
        val late = if (atAMs <= atBMs) selB else selA
        val earlySeed = early.centroidX to early.centroidY
        val lateSeed = late.centroidX to late.centroidY

        val total = timestamps.size * 2
        var doneCount = 0
        val tick: () -> Unit = { doneCount++; onProgress?.invoke((doneCount.toFloat() / total).coerceIn(0f, 1f)) }

        val fwdMap = detectPlateAlong(videoPath, combinedProfile, expectedDiameter, earlySeed, timestamps, tick)
        val bwdMap = detectPlateAlong(videoPath, combinedProfile, expectedDiameter, lateSeed, timestamps.reversed(), tick)

        val merged = mergeDetections(timestamps.map { fwdMap[it] }, timestamps.map { bwdMap[it] })
        val filled = fillGaps(merged)
        if (filled.isEmpty()) return emptyList()

        val samples = timestamps.indices.map { i ->
            val (x, y) = filled[i]
            BarPathSample(timestamps[i], x / GUARD_DOWNSCALE, y / GUARD_DOWNSCALE, expectedDiameter / GUARD_DOWNSCALE)
        }
        samples.forEach { onSample?.invoke(it) }
        return samples
    }

    /**
     * WHOLE-CLIP multi-rep tracking. Uses the two first-rep marks only to build the combined colour
     * model + anchor, then re-detects the plate over the ENTIRE video (forward from the bottom mark
     * to the end, backward from it to the start; merged + gap-filled) so every rep is tracked. Rep
     * segmentation happens later ([com.saiyanstrong.domain.util.RepSegmenter]) once a scale is known.
     */
    fun trackPlateWholeClip(
        videoPath: String,
        tapAX: Double, tapAY: Double, atAMs: Long,
        tapBX: Double, tapBY: Double, atBMs: Long,
        onProgress: ((Float) -> Unit)? = null,
        onSample: ((BarPathSample) -> Unit)? = null
    ): List<BarPathSample> {
        val selA = segmentAtTime(videoPath, atAMs, tapAX, tapAY)
        val selB = segmentAtTime(videoPath, atBMs, tapBX, tapBY)
        if (selA == null && selB == null) return emptyList()

        val (rawIntervalMs, durationMs) = readTiming(videoPath, null)
        if (durationMs <= 0L) return emptyList()

        val combinedSamples = (selA?.samples ?: emptyList()) + (selB?.samples ?: emptyList())
        val combinedProfile = MarkerColorRangeBuilder.build(combinedSamples) ?: (selA ?: selB)!!.colorProfile
        val expectedDiameter = listOfNotNull(selA?.diameterPx, selB?.diameterPx).average()
        // Anchor on the bottom mark (A if present) — a known-good plate position to expand out from.
        val anchor = selA ?: selB!!
        val anchorMs = (if (selA != null) atAMs else atBMs).coerceIn(0L, durationMs)
        val anchorSeed = anchor.centroidX to anchor.centroidY

        val intervalMs = maxOf(rawIntervalMs, durationMs / MAX_SAMPLES_MULTIREP).coerceAtLeast(MIN_SAMPLE_INTERVAL_MS)
        val timestamps = ArrayList<Long>()
        var t = 0L
        while (t <= durationMs) { timestamps.add(t); t += intervalMs }
        if (timestamps.isEmpty()) timestamps.add(0L)

        val fwdTs = timestamps.filter { it >= anchorMs }
        val bwdTs = timestamps.filter { it <= anchorMs }.reversed()
        val total = (fwdTs.size + bwdTs.size).coerceAtLeast(1)
        var doneCount = 0
        val tick: () -> Unit = { doneCount++; onProgress?.invoke((doneCount.toFloat() / total).coerceIn(0f, 1f)) }

        val fwdMap = detectPlateAlong(videoPath, combinedProfile, expectedDiameter, anchorSeed, fwdTs, tick)
        val bwdMap = detectPlateAlong(videoPath, combinedProfile, expectedDiameter, anchorSeed, bwdTs, tick)

        val merged = mergeDetections(timestamps.map { fwdMap[it] }, timestamps.map { bwdMap[it] })
        val filled = fillGaps(merged)
        if (filled.isEmpty()) return emptyList()

        val samples = timestamps.indices.map { i ->
            val (x, y) = filled[i]
            BarPathSample(timestamps[i], x / GUARD_DOWNSCALE, y / GUARD_DOWNSCALE, expectedDiameter / GUARD_DOWNSCALE)
        }
        samples.forEach { onSample?.invoke(it) }
        return samples
    }

    /** One rep's two marks (bottom + top), full-res video px + playback ms. */
    data class RepMarks(
        val bottomTapX: Double, val bottomTapY: Double, val bottomMs: Long,
        val topTapX: Double, val topTapY: Double, val topMs: Long
    )

    /**
     * PER-REP multi-rep tracking: tracks EACH rep with the reliable bounded two-mark path
     * ([trackPlateTwoMark] over that rep's `[bottom, top]`, both ends anchored) and concatenates the
     * per-rep samples in time order. Each rep is short + both-ends-anchored, so this doesn't drift
     * onto same-colour distractors or stall at the top the way whole-clip auto tracking did. Progress
     * spans all reps.
     */
    fun trackPlateReps(
        videoPath: String,
        reps: List<RepMarks>,
        onProgress: ((Float) -> Unit)? = null,
        onSample: ((BarPathSample) -> Unit)? = null
    ): List<BarPathSample> {
        if (reps.isEmpty()) return emptyList()
        val all = mutableListOf<BarPathSample>()
        reps.forEachIndexed { i, rep ->
            val repSamples = trackPlateTwoMark(
                videoPath = videoPath,
                tapAX = rep.bottomTapX, tapAY = rep.bottomTapY, atAMs = rep.bottomMs,
                tapBX = rep.topTapX, tapBY = rep.topTapY, atBMs = rep.topMs,
                onProgress = { p -> onProgress?.invoke(((i + p) / reps.size).coerceIn(0f, 1f)) },
                onSample = { s -> onSample?.invoke(s) }
            )
            all += repSamples
        }
        return all
    }

    /** Segments the plate on the frame nearest [atMs] at the tap ([tapX],[tapY], full-res video px);
     * returns the selection in DOWNSCALED (blob) space, or null. */
    private fun segmentAtTime(videoPath: String, atMs: Long, tapX: Double, tapY: Double): PlateSelection? {
        val frame = extractFrameAt(videoPath, atMs) ?: return null
        val gw = (frame.width * GUARD_DOWNSCALE).toInt().coerceAtLeast(1)
        val gh = (frame.height * GUARD_DOWNSCALE).toInt().coerceAtLeast(1)
        val argb = scaledArgb(frame, gw, gh)
        frame.recycle()
        return PlateSegmenter.segment(argb, gw, gh, (tapX * GUARD_DOWNSCALE).toInt(), (tapY * GUARD_DOWNSCALE).toInt())
    }

    /**
     * One directional re-detection pass: over [timestamps] in the given order (ascending = forward,
     * descending = backward), find the plate blob per frame (seeded at [seedCentre], downscaled
     * space) and return each timestamp's detected centre (downscaled) or null on a miss. No internal
     * hold — misses are explicit so the two passes can be merged.
     */
    private fun detectPlateAlong(
        videoPath: String,
        profile: MarkerColorProfile,
        expectedDiameter: Double,
        seedCentre: Pair<Double, Double>,
        timestamps: List<Long>,
        onFrame: (() -> Unit)? = null
    ): Map<Long, Pair<Double, Double>?> {
        val result = HashMap<Long, Pair<Double, Double>?>()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoPath)
            var prev: Pair<Double, Double>? = seedCentre
            for (ts in timestamps) {
                val frame = retriever.getFrameAtTime(ts * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                if (frame == null) { result[ts] = null; onFrame?.invoke(); continue }
                val gw = (frame.width * GUARD_DOWNSCALE).toInt().coerceAtLeast(1)
                val gh = (frame.height * GUARD_DOWNSCALE).toInt().coerceAtLeast(1)
                val argb = scaledArgb(frame, gw, gh)
                frame.recycle()
                val mask = BooleanArray(argb.size)
                val weights = DoubleArray(argb.size)
                for (i in argb.indices) {
                    val px = argb[i]
                    val r = (px shr 16) and 0xFF; val g = (px shr 8) and 0xFF; val b = px and 0xFF
                    if (profile.matches(r, g, b)) { mask[i] = true; weights[i] = profile.matchScore(r, g, b).coerceAtLeast(MIN_WEIGHT) }
                }
                val blobs = findBlobs(mask, weights, gw, gh).filter { it.size >= MIN_MARKER_PIXELS }
                val chosen = choosePlateBlob(blobs, prev, expectedDiameter)
                if (chosen != null) {
                    prev = chosen.centroidX to chosen.centroidY
                    result[ts] = prev
                } else {
                    result[ts] = null
                }
                onFrame?.invoke()
            }
        } finally {
            retriever.release()
        }
        return result
    }

    /** The video's DISPLAY dimensions (width, height) — rotation-applied, so they match both the
     * coordinate space of the tracked centroids (getFrameAtTime returns rotation-applied frames)
     * and what a player renders. Needed to map centroids onto the replay video. (0,0) if unknown. */
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

        // Anti-drift guard (trackWithVit): working resolution for the colour check + the thresholds.
        const val GUARD_DOWNSCALE = 0.5
        // Below this fraction of plate-coloured pixels, the box is considered to have left the plate.
        const val MIN_PLATE_FRACTION = 0.12
        // A per-frame move beyond this fraction of the box side is an implausible teleport (a barbell
        // moves a small fraction of a plate width per frame at real speeds).
        const val MAX_JUMP_FRACTION = 1.5
        // Max consecutive holds before the guard gives up and trusts the tracker — so a bad anchor
        // can never freeze the dot for the whole clip.
        const val MAX_CONSECUTIVE_REJECTS = 12

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

        // Multi-rep whole-clip tracking spans a longer video (several reps), so it gets a higher
        // sample cap than the single-rep paths to keep enough temporal resolution per rep.
        const val MAX_SAMPLES_MULTIREP = 300L

        // Upper bound on sampled frames per clip — each is a slow getFrameAtTime seek+decode, so
        // this caps worst-case tracking time (and the "stuck on Tracking…" perception) regardless
        // of clip length or reported frame rate. Plenty of temporal resolution for one rep.
        const val MAX_SAMPLES = 150L
    }
}
