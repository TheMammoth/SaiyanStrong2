package com.saiyanstrong.domain.util

/**
 * Gyroscope-based camera-shake compensation. A camera rotation makes a stationary point appear to
 * move by `focalLengthPx * angle` (small-angle: tan θ ≈ θ). Subtracting the predicted apparent
 * shift from the raw centroid removes the motion the camera rotation caused, leaving the bar's
 * real motion. Pure — no Android dependency.
 */
object ShakeCompensator {

    /**
     * @param cumulativeAngleX pitch (rotation about the image X axis) accumulated since the
     * compensation reference (rep/clip start), radians → vertical shift.
     * @param cumulativeAngleY yaw (about the image Y axis) accumulated since the reference,
     * radians → horizontal shift.
     *
     * IMPORTANT — CUMULATIVE, not per-frame. The original spec passed the angle *since the last
     * frame* and subtracted it from the raw centroid. That is mathematically wrong: the raw
     * centroid carries the *accumulated* rotation since the rep began, so subtracting one frame's
     * delta leaves position (and hence frame-to-frame velocity) corrupted by all prior rotation.
     * Subtracting the cumulative angle since a fixed reference corrects every frame consistently,
     * and makes the compensated frame-to-frame displacement equal (real motion − that interval's
     * rotation) — which is exactly what velocity should measure.
     */
    fun compensate(
        rawX: Double,
        rawY: Double,
        cumulativeAngleX: Double,
        cumulativeAngleY: Double,
        focalLengthPx: Double
    ): Point2D {
        val predictedShiftX = focalLengthPx * cumulativeAngleY
        val predictedShiftY = focalLengthPx * cumulativeAngleX
        return Point2D(rawX - predictedShiftX, rawY - predictedShiftY)
    }

    /**
     * focalLengthPx = (focalLengthMm / sensorWidthMm) * imageWidthPx. [imageWidthPx] must be the
     * pixel width of the SAME coordinate space the centroids being compensated live in (the full
     * video-frame width offline, the downsampled analysis width live). Returns 0 for invalid input.
     */
    fun focalLengthPx(focalLengthMm: Double, sensorWidthMm: Double, imageWidthPx: Int): Double {
        if (sensorWidthMm <= 0.0 || imageWidthPx <= 0) return 0.0
        return (focalLengthMm / sensorWidthMm) * imageWidthPx
    }
}
