package com.saiyanstrong.util.barpath

/**
 * Collects a small patch of pixels around a tap point and builds a [MarkerColorProfile] from them
 * (via [MarkerColorRangeBuilder]) — a single pixel would be poisoned by sensor noise or a specular
 * highlight. Pure, no Android/Bitmap dependency: operates on the same flat ARGB [IntArray] the live
 * pipeline already decodes, so no extra Bitmap allocation is needed and this is unit-testable.
 *
 * Saturation-filtered pixels are preferred (drops near-grey specular highlights on a metal
 * bar/plate within the patch), but if too few pixels clear the filter the patch is re-sampled
 * without it — the user may have deliberately tapped a low-saturation marker, and a too-small
 * sample is unreliable either way.
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
    val samples = collectPatchSamples(
        pixels, width, height, centerX, centerY, patchRadius, minSaturationForFilter, minFilteredSamples
    )
    return MarkerColorRangeBuilder.build(samples)
}

/**
 * The raw (hue°, sat, val) samples inside the patch — exposed so the calibration flow can
 * ACCUMULATE samples across many frames (multi-sample color range) and build one profile from the
 * whole set, instead of one profile per frame. Uses [MarkerColorMatcher.rgbToHsv] (hue [0,360)),
 * the same convention every other color-matching path uses — NOT `android.graphics.Color`'s [0,180].
 */
internal fun collectPatchSamples(
    pixels: IntArray,
    width: Int,
    height: Int,
    centerX: Int,
    centerY: Int,
    patchRadius: Int = 8,
    minSaturationForFilter: Double = 0.15,
    minFilteredSamples: Int = 20
): List<Triple<Double, Double, Double>> {
    if (width <= 0 || height <= 0 || pixels.isEmpty()) return emptyList()
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

    val filtered = collect(applySaturationFilter = true)
    return if (filtered.size >= minFilteredSamples) filtered else collect(applySaturationFilter = false)
}
