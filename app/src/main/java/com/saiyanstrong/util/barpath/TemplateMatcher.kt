package com.saiyanstrong.util.barpath

import kotlin.math.sqrt

/**
 * Pure normalized cross-correlation (NCC) template matcher for markerless point tracking — follows
 * a small image PATCH (a plate edge, bar collar, a bolt) by its appearance, not by colour, so the
 * user can tap the bare bar directly. Operates on 8-bit grayscale (0..255) [IntArray]s, no Android
 * dependency, unit-tested.
 *
 * NCC (mean-and-variance normalized), not raw SSD: the tracked point's brightness shifts through a
 * lift (lighting, angle), and NCC is invariant to a linear brightness change, so a slightly
 * lighter/darker version of the patch still matches. Rejecting low-NCC matches (the caller holds
 * the previous position) is what stops the tracked point teleporting onto unrelated objects.
 */
object TemplateMatcher {

    data class MatchResult(val x: Int, val y: Int, val score: Double)

    /**
     * Best match for [template] (tW×tH) within [searchRadius] px of ([centerX],[centerY]) in [frame]
     * (frameW×frameH), by maximum NCC. Returns the best-match CENTRE + its NCC score in [-1, 1]
     * (1 = perfect). Null if degenerate (template bigger than frame, featureless template, or no
     * candidate centre keeps the patch fully inside the frame).
     */
    fun bestMatch(
        frame: IntArray, frameW: Int, frameH: Int,
        template: IntArray, tW: Int, tH: Int,
        centerX: Int, centerY: Int, searchRadius: Int
    ): MatchResult? {
        if (tW <= 0 || tH <= 0 || tW > frameW || tH > frameH) return null
        if (frame.size < frameW * frameH || template.size < tW * tH) return null
        val halfW = tW / 2
        val halfH = tH / 2

        // Template mean + variance (computed once).
        var tSum = 0.0
        for (v in template) tSum += v
        val tMean = tSum / (tW * tH)
        var tVar = 0.0
        for (v in template) { val d = v - tMean; tVar += d * d }
        if (tVar <= 1e-9) return null // featureless template — nothing to lock onto

        // Candidate centres, kept so the patch stays fully inside the frame.
        val minCx = (centerX - searchRadius).coerceAtLeast(halfW)
        val maxCx = (centerX + searchRadius).coerceAtMost(frameW - (tW - halfW))
        val minCy = (centerY - searchRadius).coerceAtLeast(halfH)
        val maxCy = (centerY + searchRadius).coerceAtMost(frameH - (tH - halfH))
        if (minCx > maxCx || minCy > maxCy) return null

        var bestX = -1; var bestY = -1; var bestScore = Double.NEGATIVE_INFINITY
        for (cy in minCy..maxCy) {
            for (cx in minCx..maxCx) {
                val left = cx - halfW
                val top = cy - halfH
                var fSum = 0.0
                for (ty in 0 until tH) {
                    val row = (top + ty) * frameW + left
                    for (tx in 0 until tW) fSum += frame[row + tx]
                }
                val fMean = fSum / (tW * tH)
                var num = 0.0; var fVar = 0.0
                var i = 0
                for (ty in 0 until tH) {
                    val row = (top + ty) * frameW + left
                    for (tx in 0 until tW) {
                        val fd = frame[row + tx] - fMean
                        val td = template[i] - tMean
                        num += fd * td
                        fVar += fd * fd
                        i++
                    }
                }
                val denom = sqrt(fVar * tVar)
                val score = if (denom > 1e-9) num / denom else 0.0
                if (score > bestScore) { bestScore = score; bestX = cx; bestY = cy }
            }
        }
        return if (bestX >= 0) MatchResult(bestX, bestY, bestScore) else null
    }
}
