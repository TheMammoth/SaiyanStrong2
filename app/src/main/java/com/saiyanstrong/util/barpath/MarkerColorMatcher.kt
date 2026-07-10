package com.saiyanstrong.util.barpath

/**
 * Pure RGB→HSV + thresholding, no Android dependency — fully unit-testable. Tuned for a bright
 * magenta/pink marker (sticker or tape on the bar), a color rare in typical gym backgrounds
 * (skin tones, black/gray plates and rubber, chrome/steel).
 *
 * UNVERIFIED against real footage this session — the actual hue/saturation/value thresholds
 * below are a reasonable starting guess, not something tuned against a real camera + real
 * lighting. Expect to need real-world adjustment once tried against an actual recorded lift.
 */
object MarkerColorMatcher {

    private const val TARGET_HUE_MIN = 300.0
    private const val TARGET_HUE_MAX = 345.0
    private const val MIN_SATURATION = 0.45
    private const val MIN_VALUE = 0.35

    /** Standard RGB→HSV conversion. Hue in degrees [0,360), saturation/value in [0,1]. */
    fun rgbToHsv(r: Int, g: Int, b: Int): Triple<Double, Double, Double> {
        val rf = r / 255.0
        val gf = g / 255.0
        val bf = b / 255.0
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val delta = max - min

        val hue = when {
            delta == 0.0 -> 0.0
            max == rf -> 60.0 * (((gf - bf) / delta).mod(6.0))
            max == gf -> 60.0 * (((bf - rf) / delta) + 2.0)
            else -> 60.0 * (((rf - gf) / delta) + 4.0)
        }
        val saturation = if (max == 0.0) 0.0 else delta / max
        val value = max
        return Triple(hue, saturation, value)
    }

    fun matchesRgb(r: Int, g: Int, b: Int): Boolean {
        val (hue, saturation, value) = rgbToHsv(r, g, b)
        return hue in TARGET_HUE_MIN..TARGET_HUE_MAX && saturation >= MIN_SATURATION && value >= MIN_VALUE
    }
}
