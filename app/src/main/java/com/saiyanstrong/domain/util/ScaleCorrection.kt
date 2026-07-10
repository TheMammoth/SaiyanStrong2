package com.saiyanstrong.domain.util

/**
 * Corrects for depth drift — the bar moving toward/away from the camera during a rep, which
 * foreshortens apparent pixel displacement in a single 2D camera (the accuracy limitation noted
 * since Sprint 25/28). Uses the tracked marker's own apparent size as a depth proxy: if it
 * shrinks relative to the first successfully-tracked frame, the bar has moved farther away and
 * its real-world displacement is being under-represented in pixels, so the correction factor
 * scales displacement back up (and vice versa). Pure, no Android dependency.
 */
object ScaleCorrection {
    private const val MIN_RELIABLE_DIAMETER_PX = 3.0
    private const val MIN_CORRECTION = 0.5
    private const val MAX_CORRECTION = 2.0

    /**
     * @param baselineDiameterPx the marker's apparent diameter in the first successfully-tracked
     * frame of the rep, or null if unavailable.
     * @param currentDiameterPx the marker's apparent diameter in the frame being corrected, or
     * null if unavailable.
     * Returns 1.0 (no correction) if either diameter is missing or too small to be a reliable
     * measurement (<3px — a couple of noisy pixels shouldn't be trusted as a depth signal).
     * Otherwise returns baseline/current, clamped to [0.5, 2.0] so one bad frame can't corrupt
     * the whole rep with a wild correction factor.
     */
    fun compute(baselineDiameterPx: Double?, currentDiameterPx: Double?): Double {
        if (baselineDiameterPx == null || baselineDiameterPx < MIN_RELIABLE_DIAMETER_PX) return 1.0
        if (currentDiameterPx == null || currentDiameterPx < MIN_RELIABLE_DIAMETER_PX) return 1.0
        return (baselineDiameterPx / currentDiameterPx).coerceIn(MIN_CORRECTION, MAX_CORRECTION)
    }
}
