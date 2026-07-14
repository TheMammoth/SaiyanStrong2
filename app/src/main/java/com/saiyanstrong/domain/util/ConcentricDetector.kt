package com.saiyanstrong.domain.util

import com.saiyanstrong.domain.model.BarPathSample

/**
 * Finds the concentric (lifting) phase of a recorded lift from the tracked vertical positions, so
 * the analyzer measures velocity over the actual upward effort instead of the entire clip.
 *
 * Image Y increases downward, so the bar's LOWEST physical point is the MAX yPx (the bottom of a
 * squat, or the floor start of a deadlift) and its HIGHEST point is the MIN yPx (lockout). The
 * concentric phase is the ascent: from the bottom to the highest point reached after it.
 *
 * ## Why this exists
 * If the whole clip of a full descend-then-ascend rep is fed to [AnalyzeBarPathUseCase], the bar
 * starts and ends standing, so net vertical displacement over the clip is ~0 → mean concentric
 * velocity reads ~0, and peak velocity can be grabbed from the eccentric (downward) phase. That is
 * exactly the "0.00 mean, implausible peak" nonsense the feature produced against real footage.
 * Restricting analysis to the ascent fixes both. A clip that's already just the concentric (a
 * deadlift filmed floor-to-lockout, or a pre-trimmed ascent) returns ~the whole clip, so this is
 * safe to always apply.
 *
 * Pure — no Android/IO dependency, unit-tested in ConcentricDetectorTest.
 */
object ConcentricDetector {

    /**
     * Below this many samples in the detected window, fall back to the whole clip rather than
     * trust a too-short segment. The analyzer itself needs >= 2; a small handful gives the
     * downstream Savitzky-Golay fit something meaningful to work with.
     */
    private const val MIN_CONCENTRIC_SAMPLES = 3

    /**
     * @return (startMs, endMs) of the detected concentric window, or the whole clip's span when no
     * clear ascent is present (e.g. a clip that only shows a descent). Null only for < 2 samples.
     * Samples are sorted by timestamp defensively; the returned bounds are real sample timestamps.
     */
    fun detect(samples: List<BarPathSample>): Pair<Long, Long>? {
        if (samples.size < 2) return null
        val ordered = samples.sortedBy { it.timestampMs }

        // Bottom of the rep = lowest physical point = greatest yPx.
        val bottomIndex = ordered.indices.maxBy { ordered[it].yPx }

        // Highest physical point (least yPx) reached at or after the bottom = end of the ascent.
        var topIndex = bottomIndex
        for (i in bottomIndex until ordered.size) {
            if (ordered[i].yPx < ordered[topIndex].yPx) topIndex = i
        }

        val windowSize = topIndex - bottomIndex + 1
        return if (topIndex > bottomIndex && windowSize >= MIN_CONCENTRIC_SAMPLES) {
            ordered[bottomIndex].timestampMs to ordered[topIndex].timestampMs
        } else {
            ordered.first().timestampMs to ordered.last().timestampMs
        }
    }
}
