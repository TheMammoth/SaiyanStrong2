package com.saiyanstrong.domain.util

import com.saiyanstrong.domain.model.BarPathSample

/**
 * Splits a whole-set tracked path into individual reps' concentric (lifting) phases, so a multi-rep
 * recording produces per-rep velocities instead of one number for the whole clip.
 *
 * Works on "height" = -yPx (image y increases downward, so a higher bar = a smaller yPx = a larger
 * height). A rep's concentric is a VALLEY (bottom, min height) → PEAK (top, max height) ascent. A
 * hysteresis "zigzag" finds the turning points using a deadband (a fraction of the whole clip's
 * height range) so tracking noise doesn't create spurious turns; then a rep is kept only if its
 * ascent covers at least [minRomFraction] of the biggest rep's range — so a small bounce, re-grip,
 * or partial doesn't count as a rep.
 *
 * Falls back to [ConcentricDetector]'s single concentric window for a too-short/flat clip (i.e. a
 * one-rep clip yields exactly one window). Pure — no Android/IO — unit-tested.
 */
object RepSegmenter {

    private enum class PivotType { VALLEY, PEAK }

    fun segment(
        samples: List<BarPathSample>,
        minRomFraction: Double = 0.5,
        deadbandFraction: Double = 0.15
    ): List<Pair<Long, Long>> {
        if (samples.size < 4) return fallback(samples)
        val ordered = samples.sortedBy { it.timestampMs }
        val height = DoubleArray(ordered.size) { -ordered[it].yPx }
        val globalRange = height.max() - height.min()
        if (globalRange <= 1e-6) return fallback(samples)
        val deadband = globalRange * deadbandFraction

        val pivots = ArrayList<Pair<Int, PivotType>>()
        var trend = 0 // +1 rising, -1 falling, 0 unknown
        var maxV = height[0]; var maxIdx = 0
        var minV = height[0]; var minIdx = 0
        fun reset(i: Int) { val h = height[i]; maxV = h; maxIdx = i; minV = h; minIdx = i }
        for (i in 1 until height.size) {
            val h = height[i]
            if (h > maxV) { maxV = h; maxIdx = i }
            if (h < minV) { minV = h; minIdx = i }
            when {
                trend >= 0 && maxV - h > deadband -> { // reversed down from a high → a top
                    pivots.add(maxIdx to PivotType.PEAK); trend = -1; reset(i)
                }
                trend <= 0 && h - minV > deadband -> { // reversed up from a low → a bottom
                    pivots.add(minIdx to PivotType.VALLEY); trend = 1; reset(i)
                }
            }
        }
        // Commit the trailing extreme (an ascent that ends at lockout never got a reversal to confirm).
        val trailingType = when {
            trend > 0 -> PivotType.PEAK
            trend < 0 -> PivotType.VALLEY
            else -> null
        }
        val trailingIdx = if (trend > 0) maxIdx else minIdx
        if (trailingType != null && (pivots.isEmpty() || pivots.last().second != trailingType)) {
            pivots.add(trailingIdx to trailingType)
        }
        // If the clip starts already at the bottom and rises straight into the first PEAK, the start
        // valley was never committed — synthesize it as the lowest point before that first peak.
        if (pivots.isNotEmpty() && pivots.first().second == PivotType.PEAK) {
            val firstPeakIdx = pivots.first().first
            val bottomIdx = (0..firstPeakIdx).minByOrNull { height[it] } ?: 0
            pivots.add(0, bottomIdx to PivotType.VALLEY)
        }

        // Build candidate ascents from consecutive VALLEY → PEAK pivots.
        val candidates = ArrayList<Triple<Int, Int, Double>>() // (bottomIdx, topIdx, range)
        var i = 0
        while (i < pivots.size - 1) {
            val (idxA, typeA) = pivots[i]
            val (idxB, typeB) = pivots[i + 1]
            if (typeA == PivotType.VALLEY && typeB == PivotType.PEAK && idxB > idxA) {
                candidates.add(Triple(idxA, idxB, height[idxB] - height[idxA]))
            }
            i++
        }
        if (candidates.isEmpty()) return fallback(samples)

        val maxRom = candidates.maxOf { it.third }
        val threshold = maxRom * minRomFraction
        return candidates
            .filter { it.third >= threshold }
            .map { ordered[it.first].timestampMs to ordered[it.second].timestampMs }
    }

    private fun fallback(samples: List<BarPathSample>): List<Pair<Long, Long>> =
        ConcentricDetector.detect(samples)?.let { listOf(it) } ?: emptyList()
}
