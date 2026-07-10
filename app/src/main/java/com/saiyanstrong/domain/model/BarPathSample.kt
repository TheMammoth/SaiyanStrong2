package com.saiyanstrong.domain.model

/**
 * One tracked video frame of the bar marker — pixel position + timestamp.
 *
 * @param apparentDiameterPx the marker blob's bounding-box diameter in that frame, used to
 * correct for depth drift (the bar moving toward/away from the camera during a rep, which
 * foreshortens apparent pixel displacement) — see [com.saiyanstrong.domain.util.ScaleCorrection].
 * Null when unavailable (e.g. hand-constructed test fixtures), or when dual-marker mode is
 * active (see [perFramePixelsPerMeter] below, which supersedes this heuristic entirely).
 * @param perFramePixelsPerMeter a directly-measured pixels-per-meter scale for this exact
 * frame, from the pixel distance between two tracked markers a known real-world distance apart
 * — a more accurate depth-drift correction than [apparentDiameterPx]'s single-marker size
 * heuristic, since it's a direct geometric measurement rather than an inferred proxy. Null for
 * single-marker tracking (the normal/legacy path, unaffected).
 */
data class BarPathSample(
    val timestampMs: Long,
    val xPx: Double,
    val yPx: Double,
    val apparentDiameterPx: Double? = null,
    val perFramePixelsPerMeter: Double? = null
)
