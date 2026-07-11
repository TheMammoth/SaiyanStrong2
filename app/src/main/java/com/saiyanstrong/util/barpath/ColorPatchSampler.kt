package com.saiyanstrong.util.barpath

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Builds a [MarkerColorProfile] from a small patch of pixels around a tap point, rather than a
 * single pixel — a lone sensor-noise pixel or a specular highlight would poison the whole
 * profile. Pure, no Android/Bitmap dependency: operates on the same flat ARGB [IntArray] the live
 * pipeline already decodes ([BarPathLiveAnalyzer]'s downsampled frame), so no extra Bitmap
 * allocation is needed and this is directly unit-testable.
 *
 * Saturation-filtered pixels are preferred (drops near-grey specular highlights on a metal
 * bar/plate within the patch), but if too few pixels clear the filter the patch is re-sampled
 * without it — the user may have deliberately tapped a low-saturation marker, and a
 * too-small sample is unreliable either way.
 *
 * Uses [MarkerColorMatcher.rgbToHsv] (hue in degrees [0,360)), the same convention every other
 * color-matching path in this pipeline uses — NOT `android.graphics.Color.colorToHSV`'s [0,180]
 * hue range, which would silently desync from [MarkerColorProfile]'s own hue math.
 */
internal fun sampleColorPatch(
    pixels: IntArray,
    width: Int,
    height: Int,
    centerX: Int,
    centerY: Int,
    patchRadius: Int = 8,
    minSaturationForFilter: Double = 0.15,
    minFilteredSamples: Int = 20
): MarkerColorProfile? {
    if (width <= 0 || height <= 0 || pixels.isEmpty()) return null
    val cx = centerX.coerceIn(0, width - 1)
    val cy = centerY.coerceIn(0, height - 1)

    fun collect(applySaturationFilter: Boolean): List<Triple<Double, Double, Double>> {
        val out = ArrayList<Triple<Double, Double, Double>>()
        for (dy in -patchRadius..patchRadius) {
            val py = cy + dy
            if (py !in 0 until height) continue
            for (dx in -patchRadius..patchRadius) {
                val px = cx + dx
                if (px !in 0 until width) continue
                val p = pixels[py * width + px]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val hsv = MarkerColorMatcher.rgbToHsv(r, g, b)
                if (!applySaturationFilter || hsv.second >= minSaturationForFilter) out.add(hsv)
            }
        }
        return out
    }

    var samples = collect(applySaturationFilter = true)
    if (samples.size < minFilteredSamples) samples = collect(applySaturationFilter = false)
    if (samples.isEmpty()) return null

    val hues = samples.map { it.first }
    val sats = samples.map { it.second }
    val vals = samples.map { it.third }

    val hueCenter = circularMeanDegrees(hues)
    val hueStd = circularStdDegrees(hues, hueCenter)
    val satMean = sats.average()
    val satStd = stdDev(sats, satMean)
    val valMean = vals.average()
    val valStd = stdDev(vals, valMean)

    return MarkerColorProfile(
        hueCenter = hueCenter,
        // 2.5 std + a floor, so a very uniform patch (near-zero std) doesn't over-tighten the range.
        hueTolerance = (hueStd * 2.5).coerceAtLeast(8.0),
        minSaturation = (satMean - satStd * 2.0 - 0.1).coerceIn(0.0, 1.0),
        minValue = (valMean - valStd * 2.0 - 0.15).coerceIn(0.0, 1.0),
        satCenter = satMean.coerceIn(0.0, 1.0),
        satTolerance = (satStd * 2.0 + 0.1).coerceIn(0.05, 1.0),
        valCenter = valMean.coerceIn(0.0, 1.0),
        valTolerance = (valStd * 2.0 + 0.15).coerceIn(0.05, 1.0)
    )
}

/** Circular mean of hue values in degrees [0,360) — atan2(mean(sin), mean(cos)), wrapped positive. */
internal fun circularMeanDegrees(degrees: List<Double>): Double {
    var sumSin = 0.0
    var sumCos = 0.0
    for (d in degrees) {
        val rad = Math.toRadians(d)
        sumSin += sin(rad); sumCos += cos(rad)
    }
    val meanRad = atan2(sumSin / degrees.size, sumCos / degrees.size)
    val meanDeg = Math.toDegrees(meanRad)
    return if (meanDeg < 0) meanDeg + 360.0 else meanDeg
}

/**
 * Circular standard deviation in degrees via the standard circular-statistics formula
 * (Fisher): std = sqrt(-2 ln R), where R is the mean resultant length. R is recomputed from
 * the raw samples rather than derived from [meanDegrees] algebraically — simpler and exactly
 * equivalent, since R only depends on the samples themselves.
 */
internal fun circularStdDegrees(degrees: List<Double>, meanDegrees: Double): Double {
    if (degrees.isEmpty()) return 0.0
    var sumSin = 0.0
    var sumCos = 0.0
    for (d in degrees) {
        val rad = Math.toRadians(d)
        sumSin += sin(rad); sumCos += cos(rad)
    }
    val n = degrees.size
    val r = sqrt((sumSin / n) * (sumSin / n) + (sumCos / n) * (sumCos / n)).coerceIn(1e-6, 1.0)
    return Math.toDegrees(sqrt(-2.0 * ln(r)))
}

private fun stdDev(values: List<Double>, mean: Double): Double {
    if (values.size < 2) return 0.0
    val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
    return sqrt(variance)
}
