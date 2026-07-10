package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.model.BarPathAnalysis
import com.saiyanstrong.domain.model.BarPathSample
import com.saiyanstrong.domain.model.TrackedFrame
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

    /** window + corrected heights (m) + per-sample SG velocities, index-aligned to `window`. */
    private class Series(
        val window: List<BarPathSample>,
        val heightsMeters: List<Double>,
        val velocities: List<Double>
    )

    private fun computeSeries(
        samples: List<BarPathSample>,
        pixelsPerMeter: Double,
        concentricStartMs: Long,
        concentricEndMs: Long
    ): Series? {
        // Strictly increasing timestamps guarantee every consecutive pair has dt > 0, so the
        // SG velocity series stays index-aligned to `window` with no skip-and-desync risk.
        val window = samples
            .filter { it.timestampMs in concentricStartMs..concentricEndMs }
            .sortedBy { it.timestampMs }
            .distinctBy { it.timestampMs }

        if (window.size < 2 || pixelsPerMeter <= 0.0) return null

        // Depth-drift correction, two mutually exclusive mechanisms (never both — they'd
        // double-correct the same effect):
        //  - Dual-marker mode: sample.perFramePixelsPerMeter is a DIRECT per-frame measurement
        //    (pixel distance between two real markers a known real-world distance apart), more
        //    accurate than a heuristic. When present, it's used as-is.
        //  - Single-marker mode (legacy, unaffected): the marker's own apparent size in the
        //    first successfully-tracked frame is the baseline every later frame's size is
        //    compared against. If the bar has moved farther from the camera, the marker looks
        //    smaller and its real displacement is under-represented in pixels — ScaleCorrection
        //    scales it back up (and vice versa). Missing/unreliable diameters fall back to 1.0.
        // Either way, correction is applied to the frame-to-frame PIXEL DISPLACEMENT and the
        // corrected series rebuilt via cumulative sum — NOT by scaling the raw yPx value
        // directly, which has no true zero to scale from. Image Y increases downward; each
        // delta is negated so "up" is positive.
        val baselineDiameterPx = window.firstNotNullOfOrNull { it.apparentDiameterPx }
        fun effectivePixelsPerMeter(sample: BarPathSample): Double {
            sample.perFramePixelsPerMeter?.let { return it }
            val scaleCorrection = ScaleCorrection.compute(baselineDiameterPx, sample.apparentDiameterPx)
            return pixelsPerMeter / scaleCorrection
        }
        val correctedHeights = DoubleArray(window.size)
        correctedHeights[0] = -window[0].yPx / effectivePixelsPerMeter(window[0])
        for (i in 1 until window.size) {
            val rawDisplacementPx = window[i - 1].yPx - window[i].yPx
            correctedHeights[i] = correctedHeights[i - 1] + rawDisplacementPx / effectivePixelsPerMeter(window[i])
        }
        val heightsMeters = correctedHeights.toList()

        // Savitzky-Golay: local quadratic fit against real (possibly unevenly-spaced) frame
        // timestamps, analytically differentiated — a materially smoother velocity estimate than
        // differencing raw, jittery tracked positions. One velocity per sample (index-aligned).
        val velocities = SavitzkyGolayFilter.differentiate(
            positions = heightsMeters,
            timestamps = window.map { it.timestampMs.toDouble() }
        )
        return Series(window, heightsMeters, velocities)
    }

    fun execute(
        samples: List<BarPathSample>,
        pixelsPerMeter: Double,
        massKg: Double,
        concentricStartMs: Long,
        concentricEndMs: Long
    ): BarPathAnalysis {
        val series = computeSeries(samples, pixelsPerMeter, concentricStartMs, concentricEndMs)
            ?: return BarPathAnalysis(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, VelocityZone.ABSOLUTE_STRENGTH)
        val window = series.window
        val heightsMeters = series.heightsMeters
        val velocities = series.velocities

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

    /**
     * The per-frame series behind [execute]'s aggregate — position + SG velocity per tracked
     * frame — for the replay overlay. Same windowing/velocity math (shared [computeSeries]), so a
     * frame's velocity here matches what the aggregate was derived from. Empty if degenerate.
     */
    fun trackFrames(
        samples: List<BarPathSample>,
        pixelsPerMeter: Double,
        concentricStartMs: Long,
        concentricEndMs: Long
    ): List<TrackedFrame> {
        val series = computeSeries(samples, pixelsPerMeter, concentricStartMs, concentricEndMs) ?: return emptyList()
        return series.window.mapIndexed { i, sample ->
            TrackedFrame(sample.timestampMs, sample.xPx, sample.yPx, series.velocities[i])
        }
    }
}
