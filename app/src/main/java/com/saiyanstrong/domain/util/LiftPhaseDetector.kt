package com.saiyanstrong.domain.util

import kotlin.math.hypot
import kotlin.math.sqrt

enum class LiftPhase { IDLE, SETTLING, READY, MOVING, COMPLETE }

/**
 * @param filteredCentroid baseline-subtracted in READY (so a stationary bar reads ~(0,0) and
 * produces no phantom velocity), the raw centroid in MOVING, null otherwise. (Pure [Point2D],
 * substituted for the requested Android `PointF`.)
 */
data class LiftPhaseUpdate(
    val phase: LiftPhase,
    val shouldRecordVelocity: Boolean,   // true only in MOVING
    val filteredCentroid: Point2D?,
    val repJustCompleted: Boolean         // true only on the first frame of COMPLETE
)

/**
 * Rep-phase state machine that distinguishes real bar movement from camera-shake jitter during
 * the stationary phase, and gates velocity output to the actual lift. Pure Kotlin, no Android
 * dependency — feed it one centroid per frame; drive rep starts with [startRep].
 *
 * IDLE → SETTLING (via [startRep]) → READY (after a settling window; a per-session baseline of
 * what "stationary" looks like is measured) → MOVING (sustained, direction-consistent movement
 * away from baseline) → COMPLETE (velocity dies out after a real range of motion) → READY (rebar).
 *
 * Velocity thresholds are in m/s, so a pixels→meters scale is needed to interpret pixel motion;
 * live there's no calibration yet, so [pixelsPerMeter] defaults to a placeholder and the m/s
 * completion check is approximate until a real scale is supplied — the pixel-space onset check
 * (MOVEMENT_THRESHOLD_PX) is unaffected.
 */
class LiftPhaseDetector {

    var pixelsPerMeter: Double = 1000.0

    var phase: LiftPhase = LiftPhase.IDLE
        private set

    private var baselineCentroid: Point2D? = null
    private var baselineVariance: Double = 0.0

    private val settlingCentroids = ArrayDeque<Point2D>()
    private var settlingStartMs = -1L

    private var onsetCounter = 0
    private var lastMotion: Point2D? = null
    private var moveStartCentroid: Point2D? = null
    private var completionCounter = 0

    private var completeStartMs = -1L
    private val completeCentroids = ArrayDeque<Point2D>()

    private var lastCentroid: Point2D? = null
    private var lastTimestampMs = 0L

    /** Full reset to IDLE — call when (re)binding the camera / starting a session. */
    fun reset() {
        phase = LiftPhase.IDLE
        baselineCentroid = null; baselineVariance = 0.0
        settlingCentroids.clear(); settlingStartMs = -1L
        onsetCounter = 0; lastMotion = null; moveStartCentroid = null; completionCounter = 0
        completeStartMs = -1L; completeCentroids.clear()
        lastCentroid = null; lastTimestampMs = 0L
    }

    /** User tapped "Start rep" (or the screen) — begins the settling window. Ignored mid-rep. */
    fun startRep() {
        if (phase == LiftPhase.SETTLING || phase == LiftPhase.MOVING) return
        phase = LiftPhase.SETTLING
        settlingStartMs = -1L
        settlingCentroids.clear()
        onsetCounter = 0
        lastMotion = null
    }

    fun update(centroid: Point2D?, timestampMs: Long): LiftPhaseUpdate {
        var repJustCompleted = false

        when (phase) {
            LiftPhase.IDLE -> { /* collect nothing; wait for startRep() */ }

            LiftPhase.SETTLING -> {
                if (settlingStartMs < 0L) settlingStartMs = timestampMs
                centroid?.let {
                    settlingCentroids.addLast(it)
                    while (settlingCentroids.size > BASELINE_SAMPLE_COUNT) settlingCentroids.removeFirst()
                }
                if (timestampMs - settlingStartMs >= SETTLING_DURATION_MS && settlingCentroids.size >= 2) {
                    computeBaseline(settlingCentroids.toList()).let { (base, varc) ->
                        baselineCentroid = base; baselineVariance = varc
                    }
                    phase = LiftPhase.READY
                    onsetCounter = 0; lastMotion = null
                }
            }

            LiftPhase.READY -> {
                val base = baselineCentroid
                if (centroid != null && base != null) {
                    val displacement = centroid - base
                    val motion = lastCentroid?.let { centroid - it }
                    val previousMotion = lastMotion
                    val movedFarEnough = displacement.magnitude() > MOVEMENT_THRESHOLD_PX
                    // Direction consistency: consecutive frame-to-frame motion vectors must agree
                    // (dot > 0) — same-direction movement, not random back-and-forth jitter.
                    val directionConsistent = if (motion != null && previousMotion != null) dot(motion, previousMotion) > 0.0 else true
                    if (movedFarEnough && directionConsistent) {
                        onsetCounter++
                        if (onsetCounter >= N_ONSET) {
                            phase = LiftPhase.MOVING
                            moveStartCentroid = centroid
                            completionCounter = 0
                            onsetCounter = 0
                        }
                    } else {
                        onsetCounter = 0 // jitter or too small — restart the run
                    }
                    if (motion != null) lastMotion = motion
                }
            }

            LiftPhase.MOVING -> {
                val velocity = instantVelocityMps(centroid, timestampMs)
                val totalDisplacementM = if (centroid != null && moveStartCentroid != null)
                    (centroid - moveStartCentroid!!).magnitude() / pixelsPerMeter else 0.0
                if (velocity < COMPLETION_VELOCITY_MPS) completionCounter++ else completionCounter = 0
                if (completionCounter >= COMPLETION_FRAMES && totalDisplacementM > MIN_ROM_M) {
                    phase = LiftPhase.COMPLETE
                    completeStartMs = timestampMs
                    completeCentroids.clear()
                    repJustCompleted = true
                }
            }

            LiftPhase.COMPLETE -> {
                centroid?.let {
                    completeCentroids.addLast(it)
                    while (completeCentroids.size > BASELINE_SAMPLE_COUNT) completeCentroids.removeFirst()
                }
                if (timestampMs - completeStartMs >= COMPLETE_DURATION_MS) {
                    if (completeCentroids.size >= 2) {
                        computeBaseline(completeCentroids.toList()).let { (base, varc) ->
                            baselineCentroid = base; baselineVariance = varc
                        }
                    }
                    phase = LiftPhase.READY
                    onsetCounter = 0; lastMotion = null
                }
            }
        }

        val filtered: Point2D? = when (phase) {
            LiftPhase.READY -> {
                val base = baselineCentroid
                if (centroid != null && base != null) {
                    val displacement = centroid - base
                    // Spec said "< baselineVariance * 2.0", but displacement is px and variance is
                    // px² — dimensionally invalid. Using 2 standard deviations (2·√variance), the
                    // statistically-correct "within normal settling noise" band, instead.
                    if (displacement.magnitude() < 2.0 * sqrt(baselineVariance)) Point2D(0.0, 0.0) else displacement
                } else null
            }
            LiftPhase.MOVING -> centroid
            else -> null
        }

        lastCentroid = centroid
        lastTimestampMs = timestampMs
        return LiftPhaseUpdate(phase, phase == LiftPhase.MOVING, filtered, repJustCompleted)
    }

    private fun instantVelocityMps(centroid: Point2D?, timestampMs: Long): Double {
        val previous = lastCentroid
        if (centroid == null || previous == null) return 0.0
        val dtSeconds = (timestampMs - lastTimestampMs) / 1000.0
        if (dtSeconds <= 0.0) return 0.0
        return (centroid - previous).magnitude() / pixelsPerMeter / dtSeconds
    }

    companion object {
        const val SETTLING_DURATION_MS = 1500L
        const val BASELINE_SAMPLE_COUNT = 20
        const val N_ONSET = 8
        const val MOVEMENT_THRESHOLD_PX = 12.0
        const val COMPLETION_FRAMES = 15
        const val COMPLETION_VELOCITY_MPS = 0.05
        const val MIN_ROM_M = 0.05
        const val COMPLETE_DURATION_MS = 500L

        /** Mean centroid + variance as mean squared distance from that mean (px²). */
        internal fun computeBaseline(points: List<Point2D>): Pair<Point2D, Double> {
            val n = points.size
            val mean = Point2D(points.sumOf { it.x } / n, points.sumOf { it.y } / n)
            val variance = points.sumOf { val d = it - mean; d.x * d.x + d.y * d.y } / n
            return mean to variance
        }
    }
}

private operator fun Point2D.minus(other: Point2D) = Point2D(x - other.x, y - other.y)
private fun Point2D.magnitude() = hypot(x, y)
private fun dot(a: Point2D, b: Point2D) = a.x * b.x + a.y * b.y
