package com.saiyanstrong.util.barpath

import kotlin.math.hypot

enum class ClashVerdict { CLEAN, CLASH }

/**
 * Decides whether the chosen marker color ALSO lights up something big in the background — the
 * exact failure that makes tracking wander (a same-color window/wall/shirt elsewhere in frame).
 * Pure, no Android dependency: operates on the [Blob] list the detector already produces.
 *
 * A blob is a clash only if it is BOTH large (a meaningful fraction of the marker blob's size) AND
 * far from the marker (so the marker's own halo/adjacent pixels don't count against it). Advisory
 * only — the caller warns but does not block recording (SPEC.md §8).
 */
object BackgroundClashDetector {

    internal fun classify(
        blobs: List<Blob>,
        markerCentroid: Pair<Double, Double>?,
        frameWidth: Int,
        frameHeight: Int,
        minBlobPixels: Int = 10,
        clashSizeFraction: Double = 0.5
    ): ClashVerdict {
        val significant = blobs.filter { it.size >= minBlobPixels }
        if (significant.isEmpty()) return ClashVerdict.CLEAN

        val marker = if (markerCentroid != null) {
            significant.minBy { blobDistanceSq(it, markerCentroid) }
        } else {
            significant.maxBy { it.size }
        }

        // "Far" = clearly separated from the marker, not merely touching it. Scaled to the frame so
        // the same rule holds at any analysis resolution; also never smaller than a couple marker
        // diameters, so a marker with a jittery halo isn't flagged against itself.
        val frameDiagonal = hypot(frameWidth.toDouble(), frameHeight.toDouble())
        val farRadius = maxOf(frameDiagonal * 0.15, marker.diameterPx * 2.0)

        val hasFarLargeBlob = significant.any { blob ->
            blob !== marker &&
                blob.size >= marker.size * clashSizeFraction &&
                hypot(blob.centroidX - marker.centroidX, blob.centroidY - marker.centroidY) > farRadius
        }
        return if (hasFarLargeBlob) ClashVerdict.CLASH else ClashVerdict.CLEAN
    }

    private fun blobDistanceSq(blob: Blob, point: Pair<Double, Double>): Double {
        val dx = blob.centroidX - point.first
        val dy = blob.centroidY - point.second
        return dx * dx + dy * dy
    }
}
