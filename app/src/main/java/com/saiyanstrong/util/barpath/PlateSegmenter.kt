package com.saiyanstrong.util.barpath

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The result of one-tap plate segmentation: where the plate is, how big, and a colour model built
 * from its actual pixels (fed to the drift-free re-detection tracker). All positions are in the
 * pixel space of the grid passed to [PlateSegmenter.segment].
 */
data class PlateSelection(
    val centroidX: Double,
    val centroidY: Double,
    val minX: Int,
    val minY: Int,
    val maxX: Int,
    val maxY: Int,
    val diameterPx: Double,
    val pixelCount: Int,
    val colorProfile: MarkerColorProfile
)

/**
 * "Magic wand" plate selection: a BFS flood-fill from the tapped pixel over the connected region of
 * similar colour, outlining the whole plate (or its coloured rim). From that region it builds a
 * region-sampled [MarkerColorProfile] — robust because it averages the real plate, not one pixel or
 * a guessed marker colour — plus the plate's centre and size, which seed the drift-free
 * re-detection tracker. Pure (operates on an ARGB IntArray), no Android dependency, unit-tested.
 */
object PlateSegmenter {

    /**
     * @param tolerance colour distance from the SEED pixel a candidate must be within to join the
     * region — fixed to the seed (not a running mean) so the flood can't gradually drift its colour
     * criterion and leak across a soft edge onto the background.
     * @return null if the region is smaller than [minPixels] (a mis-tap on a tiny/edge feature) or
     * the region has no buildable colour model.
     */
    fun segment(
        pixels: IntArray,
        w: Int,
        h: Int,
        tapX: Int,
        tapY: Int,
        hueTolerance: Double = 22.0,
        satTolerance: Double = 0.34,
        valTolerance: Double = 0.34,
        minPixels: Int = 60
    ): PlateSelection? {
        if (w <= 0 || h <= 0 || pixels.size < w * h) return null
        val sx = tapX.coerceIn(0, w - 1)
        val sy = tapY.coerceIn(0, h - 1)
        val seed = pixels[sy * w + sx]
        val (seedH, seedS, seedV) = MarkerColorMatcher.rgbToHsv(
            (seed shr 16) and 0xFF, (seed shr 8) and 0xFF, seed and 0xFF
        )

        val visited = BooleanArray(w * h)
        val queue = ArrayDeque<Int>()
        val samples = ArrayList<Triple<Double, Double, Double>>()
        var sumX = 0.0; var sumY = 0.0; var count = 0
        var minX = Int.MAX_VALUE; var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE; var maxY = Int.MIN_VALUE

        val start = sy * w + sx
        visited[start] = true
        queue.add(start)
        while (queue.isNotEmpty()) {
            val idx = queue.removeFirst()
            val p = pixels[idx]
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            val (ph, ps, pv) = MarkerColorMatcher.rgbToHsv(r, g, b)
            val within = hueDistance(ph, seedH) <= hueTolerance &&
                abs(ps - seedS) <= satTolerance &&
                abs(pv - seedV) <= valTolerance
            if (!within) continue // visited but not part of the region — a boundary pixel

            val x = idx % w; val y = idx / w
            samples += Triple(ph, ps, pv)
            sumX += x; sumY += y; count++
            minX = min(minX, x); maxX = max(maxX, x)
            minY = min(minY, y); maxY = max(maxY, y)
            for ((nx, ny) in listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)) {
                if (nx in 0 until w && ny in 0 until h) {
                    val nIdx = ny * w + nx
                    if (!visited[nIdx]) { visited[nIdx] = true; queue.add(nIdx) }
                }
            }
        }

        if (count < minPixels) return null
        val profile = MarkerColorRangeBuilder.build(samples) ?: return null
        return PlateSelection(
            centroidX = sumX / count,
            centroidY = sumY / count,
            minX = minX, minY = minY, maxX = maxX, maxY = maxY,
            diameterPx = max(maxX - minX + 1, maxY - minY + 1).toDouble(),
            pixelCount = count,
            colorProfile = profile
        )
    }

    /** Circular hue distance in degrees (hues wrap at 360). */
    private fun hueDistance(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return min(d, 360.0 - d)
    }
}
