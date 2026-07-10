package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.model.BarPathAnalysis
import com.saiyanstrong.domain.model.BarPathSample
import com.saiyanstrong.domain.model.VelocityZone
import javax.inject.Inject

private const val GRAVITY_MS2 = 9.81

/**
 * Pure physics — no camera, no I/O. Turns tracked marker positions into real velocity/
 * acceleration/force/power, the way an actual VBT device reports them.
 */
class AnalyzeBarPathUseCase @Inject constructor() {

    fun execute(
        samples: List<BarPathSample>,
        pixelsPerMeter: Double,
        massKg: Double,
        concentricStartMs: Long,
        concentricEndMs: Long
    ): BarPathAnalysis {
        // Strictly increasing timestamps guarantee every consecutive pair has dt > 0, which in
        // turn guarantees `velocities` always has exactly window.size - 1 entries — velocities[i]
        // and powers[i] can then be trusted to line up with window[i+1] by index, with no
        // skip-and-desync risk from a duplicate/out-of-order frame timestamp.
        val window = samples
            .filter { it.timestampMs in concentricStartMs..concentricEndMs }
            .sortedBy { it.timestampMs }
            .distinctBy { it.timestampMs }

        if (window.size < 2 || pixelsPerMeter <= 0.0) {
            return BarPathAnalysis(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, VelocityZone.ABSOLUTE_STRENGTH)
        }

        // Image Y increases downward; negate so "up" is positive in real-world meters.
        val heightsMeters = window.map { -it.yPx / pixelsPerMeter }

        val velocities = (1 until window.size).map { i ->
            val dtSeconds = (window[i].timestampMs - window[i - 1].timestampMs) / 1000.0
            (heightsMeters[i] - heightsMeters[i - 1]) / dtSeconds
        }

        val powers = (1 until velocities.size).map { i ->
            val dtSeconds = (window[i + 1].timestampMs - window[i].timestampMs) / 1000.0
            val acceleration = (velocities[i] - velocities[i - 1]) / dtSeconds
            val force = massKg * (GRAVITY_MS2 + acceleration)
            force * velocities[i]
        }

        val totalTimeSeconds = (window.last().timestampMs - window.first().timestampMs) / 1000.0
        val meanConcentricVelocityMs =
            if (totalTimeSeconds > 0.0) (heightsMeters.last() - heightsMeters.first()) / totalTimeSeconds else 0.0

        val xPxValues = window.map { it.xPx }

        return BarPathAnalysis(
            peakVelocityMs = velocities.maxOrNull() ?: 0.0,
            meanConcentricVelocityMs = meanConcentricVelocityMs,
            peakPowerWatts = powers.maxOrNull() ?: 0.0,
            meanPowerWatts = if (powers.isNotEmpty()) powers.average() else 0.0,
            rangeOfMotionCm = (heightsMeters.max() - heightsMeters.min()) * 100.0,
            barPathDeviationCm = (xPxValues.max() - xPxValues.min()) / pixelsPerMeter * 100.0,
            velocityZone = VelocityZone.fromVelocity(meanConcentricVelocityMs)
        )
    }
}
