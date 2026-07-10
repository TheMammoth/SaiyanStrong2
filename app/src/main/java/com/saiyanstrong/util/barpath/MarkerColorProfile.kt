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
    val minValue: Double
) {
    fun matches(r: Int, g: Int, b: Int): Boolean {
        val (hue, saturation, value) = MarkerColorMatcher.rgbToHsv(r, g, b)
        return hueDistance(hue, hueCenter) <= hueTolerance &&
            saturation >= minSaturation &&
            value >= minValue
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
                minValue = (value - 0.25).coerceAtLeast(0.2)
            )
        }

        /** Defensive fallback only — the capture flow always requires a real marker tap. */
        fun default(): MarkerColorProfile = MarkerColorProfile(
            hueCenter = 322.5, hueTolerance = 22.5, minSaturation = 0.45, minValue = 0.35
        )

        /** Circular distance in degrees — hue wraps at 360 (350° and 10° are 20° apart, not 340°). */
        internal fun hueDistance(a: Double, b: Double): Double {
            val diff = abs(a - b) % 360.0
            return min(diff, 360.0 - diff)
        }
    }
}
