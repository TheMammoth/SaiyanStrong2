package com.saiyanstrong.util.barpath

import kotlin.math.abs
import kotlin.math.min

/**
 * A color-match range built from a real sample rather than a guessed fixed threshold —
 * replaces trusting [MarkerColorMatcher]'s hardcoded magenta range for every recording
 * regardless of the user's actual marker or lighting. Pure, no Android dependency.
 */
data class MarkerColorProfile(
    val hueCenter: Double,
    val hueTolerance: Double,
    val minSaturation: Double,
    val minValue: Double,
    // Center + tolerance for saturation/value, used only by matchScore()'s subpixel weighting
    // below — matches()'s floor-based accept/reject test above is untouched and unaffected by
    // these; they exist purely so a matching pixel can be scored by *how close* it is to the
    // sample, not just whether it clears the floor.
    val satCenter: Double = (minSaturation + 0.25).coerceAtMost(1.0),
    val satTolerance: Double = 0.25,
    val valCenter: Double = (minValue + 0.25).coerceAtMost(1.0),
    val valTolerance: Double = 0.25
) {
    fun matches(r: Int, g: Int, b: Int): Boolean {
        val (hue, saturation, value) = MarkerColorMatcher.rgbToHsv(r, g, b)
        return hueDistance(hue, hueCenter) <= hueTolerance &&
            saturation >= minSaturation &&
            value >= minValue
    }

    /**
     * How well a pixel matches the sampled color, in (0.0, 1.0] for a pixel centered exactly
     * on the sample, trending to 0 as hue/saturation/value distance from the sample approaches
     * their tolerances — clamped at 0 rather than going negative, since a pixel can pass the
     * (asymmetric, floor-based) [matches] test while still being far from the sample on this
     * (symmetric, distance-based) scale (e.g. a pixel brighter than the sample still clears the
     * value floor but is far from [valCenter]). Only meaningful for pixels that already passed
     * [matches] — this is a *weight*, not an independent accept/reject test.
     */
    fun matchScore(r: Int, g: Int, b: Int): Double {
        val (hue, saturation, value) = MarkerColorMatcher.rgbToHsv(r, g, b)
        val hueDist = hueDistance(hue, hueCenter)
        val satDist = abs(saturation - satCenter)
        val valDist = abs(value - valCenter)
        val score = 1.0 - (hueDist / hueTolerance + satDist / satTolerance + valDist / valTolerance) / 3.0
        return score.coerceAtLeast(0.0)
    }

    companion object {
        /**
         * Built from one sampled pixel (already averaged over a small neighborhood by the
         * caller to reduce single-pixel noise). Saturation/value floors sit below the sample —
         * an explicit, documented approximation (not tuned against real footage yet) accounting
         * for lighting variation across a clip, not just the single calibration frame.
         */
        fun sample(r: Int, g: Int, b: Int): MarkerColorProfile {
            val (hue, saturation, value) = MarkerColorMatcher.rgbToHsv(r, g, b)
            return MarkerColorProfile(
                hueCenter = hue,
                hueTolerance = 20.0,
                minSaturation = (saturation - 0.25).coerceAtLeast(0.2),
                minValue = (value - 0.25).coerceAtLeast(0.2),
                satCenter = saturation,
                satTolerance = 0.25,
                valCenter = value,
                valTolerance = 0.25
            )
        }

        /** Defensive fallback only — the capture flow always requires a real marker tap. */
        fun default(): MarkerColorProfile = MarkerColorProfile(
            hueCenter = 322.5, hueTolerance = 22.5,
            minSaturation = 0.45, minValue = 0.35,
            satCenter = 0.7, satTolerance = 0.3,
            valCenter = 0.6, valTolerance = 0.3
        )

        /** Circular distance in degrees — hue wraps at 360 (350° and 10° are 20° apart, not 340°). */
        internal fun hueDistance(a: Double, b: Double): Double {
            val diff = abs(a - b) % 360.0
            return min(diff, 360.0 - diff)
        }
    }
}

/**
 * Widens all three tolerance bands (hue/saturation/value) by [factor] — used for a tap sampled
 * while the phone was only SETTLING (not fully STABLE): a small amount of motion blur can shift
 * the true marker color slightly outside a tightly-fit tolerance, so the band is loosened rather
 * than rejecting a sample that's still usable. Deliberately leaves [MarkerColorProfile.minSaturation]/
 * [MarkerColorProfile.minValue] (the floor-based accept/reject gate in [MarkerColorProfile.matches])
 * untouched — only the three tolerance/distance-scale fields widen.
 */
fun MarkerColorProfile.widened(factor: Double): MarkerColorProfile = copy(
    hueTolerance = hueTolerance * factor,
    satTolerance = satTolerance * factor,
    valTolerance = valTolerance * factor
)
