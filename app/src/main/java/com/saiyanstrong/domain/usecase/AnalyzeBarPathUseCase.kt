package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.model.BarPathAnalysis
import com.saiyanstrong.domain.model.BarPathSample
import com.saiyanstrong.domain.model.VelocityZone
import com.saiyanstrong.domain.util.SavitzkyGolayFilter
import com.saiyanstrong.domain.util.ScaleCorrection
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

        // Depth-drift correction: the marker's own apparent size in the first successfully-
        // tracked frame is the baseline against which every later frame's size is compared. If
        // the bar has moved farther from the camera, the marker looks smaller and its real
        // displacement is under-represented in pixels — ScaleCorrection scales it back up (and
        // vice versa). Missing/unreliable diameters fall back to 1.0 (no correction), so this
        // is a no-op for samples that never populated apparentDiameterPx.
        //
        // Correction must be applied to the frame-to-frame PIXEL DISPLACEMENT and the corrected
        // series rebuilt via cumulative sum — NOT by scaling the raw yPx value directly, which
        // has no true zero to scale from (it's an arbitrary pixel coordinate, not a physical
        // displacement). Image Y increases downward; each delta is negated so "up" is positive.
        val baselineDiameterPx = window.firstNotNullOfOrNull { it.apparentDiameterPx }
        val correctedHeights = DoubleArray(window.size)
        correctedHeights[0] = -window[0].yPx / pixelsPerMeter
        for (i in 1 until window.size) {
            val rawDisplacementPx = window[i - 1].yPx - window[i].yPx
            val scaleCorrection = ScaleCorrection.compute(baselineDiameterPx, window[i].apparentDiameterPx)
            correctedHeights[i] = correctedHeights[i - 1] + rawDisplacementPx * scaleCorrection / pixelsPerMeter
        }
        val heightsMeters = correctedHeights.toList()

        // Savitzky-Golay: local quadratic fit against real (possibly unevenly-spaced) frame
        // timestamps, analytically differentiated — a materially smoother velocity estimate
        // than differencing raw, jittery tracked positions frame to frame. One velocity per
        // sample (index-aligned to `window`), not one per gap.
        val velocities = SavitzkyGolayFilter.differentiate(
            positions = heightsMeters,
            timestamps = window.map { it.timestampMs.toDouble() }
        )

        val powers = (1 until window.size).map { i ->
            val dtSeconds = (window[i].timestampMs - window[i - 1].timestampMs) / 1000.0
            val acceleration = if (dtSeconds > 0.0) (velocities[i] - velocities[i - 1]) / dtSeconds else 0.0
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
