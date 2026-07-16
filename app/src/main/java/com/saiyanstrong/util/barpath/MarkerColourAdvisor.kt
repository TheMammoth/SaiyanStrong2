package com.saiyanstrong.util.barpath

/** How well a marker colour stands out from the current scene. */
enum class MarkerGrade { GOOD, OK, BAD }

/** A nameable marker colour and its representative hue (degrees, [0,360)). */
data class MarkerCandidate(val name: String, val hueDeg: Double)

/** The scene-scan result: colours to use (most absent from the scene) and colours to avoid. */
data class MarkerAdvice(
    val recommended: List<MarkerCandidate>,
    val avoid: List<MarkerCandidate>
)

/**
 * Reads a scene (as a saturated-hue histogram) and recommends which marker colour to use — the
 * saturated colour most ABSENT from the room — and grades a marker the user holds up against the
 * same scene. Pure, no Android dependency, fully unit-testable. Uses [MarkerColorMatcher.rgbToHsv]
 * (hue [0,360)), the one hue convention shared across this pipeline.
 *
 * The point: stop the user guessing a marker colour that turns out to clash (their green-yellow
 * note matched a green drawer; pale pink matched the background). The scene itself decides.
 */
object MarkerColourAdvisor {

    // Order doubles as the tie-break preference when several colours are equally absent — colours
    // rarely present in an indoor scene come first, so a blank/plain scene recommends blue/purple.
    val PALETTE: List<MarkerCandidate> = listOf(
        MarkerCandidate("Blue", 220.0),
        MarkerCandidate("Purple", 275.0),
        MarkerCandidate("Magenta", 320.0),
        MarkerCandidate("Cyan", 190.0),
        MarkerCandidate("Orange", 30.0),
        MarkerCandidate("Red", 2.0),
        MarkerCandidate("Yellow", 55.0),
        MarkerCandidate("Green", 120.0)
    )

    private const val HUE_TOLERANCE = 25.0
    private const val RECOMMEND_COUNT = 2
    private const val AVOID_FRACTION = 0.04
    private const val GRADE_GOOD_MAX = 0.01
    private const val GRADE_OK_MAX = 0.04

    /**
     * Counts sufficiently-saturated AND bright pixels into [bins] hue buckets. The saturation/value
     * floors are what keep grey walls, black plates, beige floor and shadows from drowning the
     * signal — only genuinely coloured pixels count toward "this hue is present in the scene".
     */
    fun buildHueHistogram(
        pixels: IntArray,
        width: Int,
        height: Int,
        bins: Int = 24,
        minSaturation: Double = 0.4,
        minValue: Double = 0.3
    ): IntArray {
        val histogram = IntArray(bins)
        if (width <= 0 || height <= 0 || pixels.isEmpty() || bins <= 0) return histogram
        val binWidth = 360.0 / bins
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val (hue, sat, value) = MarkerColorMatcher.rgbToHsv(r, g, b)
            if (sat >= minSaturation && value >= minValue) {
                val idx = (hue / binWidth).toInt().coerceIn(0, bins - 1)
                histogram[idx]++
            }
        }
        return histogram
    }

    /** Fraction of counted (coloured) pixels whose hue is within [HUE_TOLERANCE] of [hueDeg]. */
    private fun crowdedness(histogram: IntArray, hueDeg: Double): Double {
        val total = histogram.sum()
        if (total == 0) return 0.0
        val binWidth = 360.0 / histogram.size
        var count = 0
        for (i in histogram.indices) {
            val binCenter = (i + 0.5) * binWidth
            if (MarkerColorProfile.hueDistance(binCenter, hueDeg) <= HUE_TOLERANCE) count += histogram[i]
        }
        return count.toDouble() / total
    }

    fun recommend(histogram: IntArray): MarkerAdvice {
        val scored = PALETTE.mapIndexed { idx, c -> Triple(c, crowdedness(histogram, c.hueDeg), idx) }
        // Least-crowded first; ties broken by the palette's preferred order (idx).
        val sorted = scored.sortedWith(compareBy({ it.second }, { it.third }))
        val recommended = sorted.take(RECOMMEND_COUNT).map { it.first }
        val avoid = sorted
            .filter { it.second >= AVOID_FRACTION }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(3)
        return MarkerAdvice(recommended, avoid)
    }

    /** Grades a marker's own hue against the scene: GOOD (empty band), OK, BAD (crowded band). */
    fun grade(histogram: IntArray, hueDeg: Double): MarkerGrade {
        val c = crowdedness(histogram, hueDeg)
        return when {
            c <= GRADE_GOOD_MAX -> MarkerGrade.GOOD
            c <= GRADE_OK_MAX -> MarkerGrade.OK
            else -> MarkerGrade.BAD
        }
    }
}
