package com.saiyanstrong.util.barpath

/**
 * A tracker init box in full-resolution video-pixel space. Pure (no android.graphics.Rect, which
 * isn't available in plain JUnit) so the geometry that seeds [VitBarTracker] is unit-testable; the
 * Android/OpenCV shell converts this to an org.opencv.core.Rect at the boundary.
 */
data class BarInitBox(val x: Int, val y: Int, val width: Int, val height: Int) {
    val centerX: Double get() = x + width / 2.0
    val centerY: Double get() = y + height / 2.0
}

/**
 * Pure support math for the OpenCV TrackerVit path. Everything here is deliberately
 * Android/OpenCV-free so it can be unit-tested; the stateful native tracking lives in
 * [VitBarTracker] (untestable shell, like CameraX/ExoPlayer).
 */
object VitBarTrackerSupport {

    /** Fraction of the frame's shorter side used as the init box side length — a weight plate
     * fills a good chunk of the frame at a typical filming distance, and TrackerVit tracks a
     * region, so a plate-ish box seeds it well. First-pass value, tunable after a device look. */
    const val DEFAULT_BOX_FRACTION = 0.18

    /** A standard competition/Olympic plate is 0.45 m across — the reference for the optional
     * auto-scale from the tracked box width. */
    const val STANDARD_PLATE_DIAMETER_M = 0.45

    /**
     * A square init box centred on the tap ([tapX], [tapY] in full-res video px), side =
     * shorterSide × [boxFraction], with the box shifted (not shrunk) to stay fully inside
     * [frameW]×[frameH] when the tap is near an edge. Returns null for a degenerate frame.
     */
    fun initBoxFromTap(
        tapX: Double,
        tapY: Double,
        frameW: Int,
        frameH: Int,
        boxFraction: Double = DEFAULT_BOX_FRACTION
    ): BarInitBox? {
        if (frameW <= 0 || frameH <= 0) return null
        val side = (minOf(frameW, frameH) * boxFraction).toInt().coerceAtLeast(1).coerceAtMost(minOf(frameW, frameH))
        val half = side / 2.0
        // Clamp the top-left so the whole box stays inside the frame (shift toward the interior).
        val x = (tapX - half).toInt().coerceIn(0, frameW - side)
        val y = (tapY - half).toInt().coerceIn(0, frameH - side)
        return BarInitBox(x, y, side, side)
    }

    /** Smallest allowed init-box side (px), so a pinched-tiny box still gives the tracker something. */
    const val MIN_BOX_SIDE = 24

    /**
     * A square init box with an explicit user-chosen [sidePx] centred on ([centerX], [centerY]) —
     * for the movable/resizable start box (the user drags + pinches it onto the plate). Side is
     * floored at [MIN_BOX_SIDE] and capped at the shorter frame side; the box is shifted (not
     * shrunk) to stay fully inside [frameW]×[frameH]. Null for a degenerate frame.
     */
    fun initBox(
        centerX: Double,
        centerY: Double,
        sidePx: Int,
        frameW: Int,
        frameH: Int
    ): BarInitBox? {
        if (frameW <= 0 || frameH <= 0) return null
        val side = sidePx.coerceIn(MIN_BOX_SIDE, minOf(frameW, frameH))
        val half = side / 2.0
        val x = (centerX - half).toInt().coerceIn(0, frameW - side)
        val y = (centerY - half).toInt().coerceIn(0, frameH - side)
        return BarInitBox(x, y, side, side)
    }

    /** Centre of a tracked box, in the same pixel space as the box. */
    fun boxCenter(x: Int, y: Int, width: Int, height: Int): Pair<Double, Double> =
        (x + width / 2.0) to (y + height / 2.0)

    /**
     * Optional auto-scale: the tracked box width ≈ the plate's on-screen diameter, so
     * pixels-per-meter ≈ boxWidthPx / plateDiameterM — a scale with no separate two-tap step.
     * Deferred to a real-device look before it replaces the manual scale (see SPEC.md §3); the
     * math lives here so it's ready and tested. Returns null for a non-positive box width.
     */
    fun plateScalePpm(boxWidthPx: Int, plateDiameterM: Double = STANDARD_PLATE_DIAMETER_M): Double? {
        if (boxWidthPx <= 0 || plateDiameterM <= 0.0) return null
        return boxWidthPx / plateDiameterM
    }
}
