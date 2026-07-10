package com.saiyanstrong.domain.model

/**
 * One tracked video frame of the bar marker — pixel position + timestamp.
 *
 * @param apparentDiameterPx the marker blob's bounding-box diameter in that frame, used to
 * correct for depth drift (the bar moving toward/away from the camera during a rep, which
 * foreshortens apparent pixel displacement) — see [com.saiyanstrong.domain.util.ScaleCorrection].
 * Null when unavailable (e.g. hand-constructed test fixtures).
 */
data class BarPathSample(
    val timestampMs: Long,
    val xPx: Double,
    val yPx: Double,
    val apparentDiameterPx: Double? = null
)
