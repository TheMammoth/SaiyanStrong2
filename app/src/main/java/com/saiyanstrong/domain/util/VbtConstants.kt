package com.saiyanstrong.domain.util

/**
 * Tunable VBT constants, gathered here so they can be adjusted without hunting through code.
 * (Namespaced in an object rather than bare top-level `const val`s purely for discoverability —
 * `VbtConstants.KALMAN_MEASUREMENT_NOISE` greps cleanly.)
 */
object VbtConstants {
    /** Centroid position uncertainty, in pixels — how much a single blob-detected centroid is trusted. */
    const val KALMAN_MEASUREMENT_NOISE = 2.0

    /** Process (model) noise, in pixels/s² — roughly the max plausible bar acceleration the filter allows. */
    const val KALMAN_PROCESS_NOISE = 50.0
}
