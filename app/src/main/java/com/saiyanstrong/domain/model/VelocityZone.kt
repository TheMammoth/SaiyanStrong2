package com.saiyanstrong.domain.model

/**
 * Bryan Mann VBT zone table — widely taught in strength & conditioning, population-level and
 * not lift-specific (bench/squat/deadlift velocity-at-failure genuinely differs). Good enough
 * for a first pass; the real long-term upgrade is a personal load-velocity profile per exercise
 * (see SPEC.md §8).
 */
enum class VelocityZone(val label: String, val minMs: Double, val maxMs: Double) {
    ABSOLUTE_STRENGTH("Absolute Strength", 0.0, 0.50),
    STRENGTH_SPEED("Strength-Speed", 0.50, 0.75),
    SPEED_STRENGTH("Speed-Strength", 0.75, 1.00),
    SPEED_ACCEL("Speed (Accelerative)", 1.00, 1.30),
    SPEED_MAX("Speed (Max)", 1.30, Double.MAX_VALUE);

    companion object {
        fun fromVelocity(meanConcentricVelocityMs: Double): VelocityZone {
            val v = meanConcentricVelocityMs.coerceAtLeast(0.0)
            return entries.first { v >= it.minMs && v < it.maxMs }
        }
    }
}
