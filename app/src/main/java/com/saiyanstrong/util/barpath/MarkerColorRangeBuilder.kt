package com.saiyanstrong.util.barpath

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Builds one [MarkerColorProfile] from a list of sampled HSV pixels — the shared statistics core
 * behind both the single-tap patch sampler ([sampleColorPatch]) and the multi-sample calibration
 * flow ("train the marker before recording", SPEC.md). Pure, no Android dependency.
 *
 * The point of feeding it MANY samples (calibration accumulates the marker across ~1-2 s of live
 * frames, not one patch) is a color RANGE that already spans the glare/shadow the marker will move
 * through during the rep, rather than a tight fit to one instant that then misses in later frames.
 * The range still maps onto [MarkerColorProfile]'s existing center+tolerance+floor fields — no new
 * profile type, nothing downstream changes.
 */
object MarkerColorRangeBuilder {

    /**
     * @param samples (hue°[0,360), saturation[0,1], value[0,1]) triples — already collected/filtered
     * by the caller (patch neighborhood, or accumulated calibration frames). Returns null if empty.
     */
    fun build(samples: List<Triple<Double, Double, Double>>): MarkerColorProfile? {
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
            // 2.5 std + a floor, so a very uniform sample set (near-zero std) doesn't over-tighten
            // the range and then miss the marker under slightly different lighting.
            hueTolerance = (hueStd * 2.5).coerceAtLeast(8.0),
            minSaturation = (satMean - satStd * 2.0 - 0.1).coerceIn(0.0, 1.0),
            minValue = (valMean - valStd * 2.0 - 0.15).coerceIn(0.0, 1.0),
            satCenter = satMean.coerceIn(0.0, 1.0),
            satTolerance = (satStd * 2.0 + 0.1).coerceIn(0.05, 1.0),
            valCenter = valMean.coerceIn(0.0, 1.0),
            valTolerance = (valStd * 2.0 + 0.15).coerceIn(0.05, 1.0)
        )
    }
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

internal fun stdDev(values: List<Double>, mean: Double): Double {
    if (values.size < 2) return 0.0
    val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
    return sqrt(variance)
}
