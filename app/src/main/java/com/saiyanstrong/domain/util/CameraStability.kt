package com.saiyanstrong.domain.util

import kotlin.math.sqrt

/** How still the phone is right now, from raw (non-integrated) gyroscope angular velocity. */
enum class StabilityLevel { STABLE, SETTLING, MOVING }

/**
 * Classifies phone stability from the gyroscope's instantaneous angular velocity magnitude —
 * a tap during real motion produces a blurred, mixed-color patch sample ([sampleColorPatch]
 * averages a spatial neighborhood, not a temporal one, so motion blur within that neighborhood
 * corrupts the average). Pure, no Android dependency; the actual sensor reading lives in
 * `util/barpath/StabilityMonitor` (Android-touching), which calls [angularVelocityMagnitude] on
 * each raw `SensorEvent`.
 */
object CameraStability {
    private const val STABLE_THRESHOLD_RAD_PER_SEC = 0.05f
    private const val SETTLING_THRESHOLD_RAD_PER_SEC = 0.15f

    fun classify(angularVelocityMagnitudeRadPerSec: Float): StabilityLevel = when {
        angularVelocityMagnitudeRadPerSec < STABLE_THRESHOLD_RAD_PER_SEC -> StabilityLevel.STABLE
        angularVelocityMagnitudeRadPerSec < SETTLING_THRESHOLD_RAD_PER_SEC -> StabilityLevel.SETTLING
        else -> StabilityLevel.MOVING
    }
}

/** sqrt(ωx² + ωy² + ωz²) from a single raw gyroscope sample, in rad/s. Not integrated — this is
 * an instantaneous "how fast is the phone rotating right now" reading, unrelated to [GyroTimeline]
 * (which integrates cumulative angle over a recording for offline shake compensation — a
 * different concern with a different lifecycle, scoped to the recording window only). */
fun angularVelocityMagnitude(x: Float, y: Float, z: Float): Float =
    sqrt(x * x + y * y + z * z)
