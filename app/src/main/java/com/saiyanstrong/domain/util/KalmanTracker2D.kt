package com.saiyanstrong.domain.util

import kotlin.math.hypot

/** Pure 2D point — substitutes for android.graphics.PointF to keep this class Android-free. */
data class Point2D(val x: Double, val y: Double)

/**
 * Constant-acceleration Kalman filter over a marker's 2D pixel position, for smoothing a LIVE
 * per-frame velocity display. Pure Kotlin, no Android dependency.
 *
 * State vector (6): [x, y, vx, vy, ax, ay]. Observation (2): [x_px, y_px] (blob centroid).
 *
 * IMPORTANT — this is a standalone, unwired building block. There is currently no live-capture /
 * real-time-analysis loop in this app to feed it (the VBT pipeline records to a file and
 * analyzes it afterward via MediaMetadataRetriever — see BarPathFrameTracker). This class is the
 * correct foundation for a live velocity display *if/when* such a loop is built; it is
 * deliberately NOT injected or called from anywhere yet. When it is wired up, instantiate one
 * per rep/session (it holds mutable state — a shared/singleton instance would be a bug) and it
 * must stay UI-only: the Savitzky-Golay post-analysis pipeline receives raw centroids, never
 * Kalman-smoothed ones.
 *
 * @param measurementNoiseSigma centroid uncertainty, in pixels.
 * @param processNoiseSigma model/process noise, in pixels/s² (roughly max bar acceleration).
 * @param maxAccelerationMps2 physical sanity cap on estimated acceleration — a magnitude above
 * this (default 15 m/s² ≈ 1.5g) is a tracking error, not real barbell motion, so the predicted
 * acceleration vector is scaled back to this bound (direction preserved). Converting the cap
 * between m/s² and the filter's pixel space needs a scale factor: set [pixelsPerMeter] each
 * frame (it can vary per frame in dual-marker mode).
 */
class KalmanTracker2D(
    private val measurementNoiseSigma: Double = VbtConstants.KALMAN_MEASUREMENT_NOISE,
    private val processNoiseSigma: Double = VbtConstants.KALMAN_PROCESS_NOISE,
    private val maxAccelerationMps2: Double = 15.0
) {
    /** Current pixels-per-meter scale — set by the caller each frame; used by the acceleration
     * clamp and the m/s velocity/acceleration readouts. Defaults to a neutral value so the
     * clamp is effectively inactive until a real scale is supplied. */
    var pixelsPerMeter: Double = 1000.0

    private var state = DoubleArray(6)
    private var covariance = Array(6) { DoubleArray(6) }

    private val observationMatrix = arrayOf(  // H: 2x6, observes x and y only
        doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 0.0),
        doubleArrayOf(0.0, 1.0, 0.0, 0.0, 0.0, 0.0)
    )
    private val measurementNoise = run {  // R: 2x2
        val r2 = measurementNoiseSigma * measurementNoiseSigma
        arrayOf(doubleArrayOf(r2, 0.0), doubleArrayOf(0.0, r2))
    }

    init { reset(0.0, 0.0) }

    /** Call at rep start — seeds the position from the first centroid, zeros velocity/acceleration. */
    fun reset(initialX: Double, initialY: Double) {
        state = doubleArrayOf(initialX, initialY, 0.0, 0.0, 0.0, 0.0)
        val r2 = measurementNoiseSigma * measurementNoiseSigma
        val s2 = processNoiseSigma * processNoiseSigma
        covariance = Array(6) { DoubleArray(6) }
        covariance[0][0] = r2; covariance[1][1] = r2          // position: known from measurement
        covariance[2][2] = s2; covariance[3][3] = s2          // velocity: unknown at start
        covariance[4][4] = s2; covariance[5][5] = s2          // acceleration: unknown at start
    }

    /** Prediction step. Advances the state by [dt] seconds under the constant-acceleration model. */
    fun predict(dt: Double) {
        val f = transitionMatrix(dt)
        state = matVec(f, state)
        covariance = matAdd(matMul(matMul(f, covariance), matT(f)), processNoise(dt))
        clampAcceleration()
    }

    /** Measurement update step, folding a newly detected centroid into the estimate. */
    fun update(measuredX: Double, measuredY: Double) {
        val h = observationMatrix
        val ht = matT(h)                                              // 6x2
        val predictedObs = matVec(h, state)                          // 2
        val innovation = doubleArrayOf(measuredX - predictedObs[0], measuredY - predictedObs[1])
        val s = matAdd(matMul(matMul(h, covariance), ht), measurementNoise)  // 2x2
        val sInv = inverse2x2(s)
        val gain = matMul(matMul(covariance, ht), sInv)              // K: 6x2
        val correction = matVec(gain, innovation)                    // 6
        for (i in 0 until 6) state[i] += correction[i]
        covariance = matMul(matSub(identity(6), matMul(gain, h)), covariance)
    }

    val smoothedPosition: Point2D get() = Point2D(state[0], state[1])

    val smoothedVelocityMps: Float
        get() = if (pixelsPerMeter > 0.0) (hypot(state[2], state[3]) / pixelsPerMeter).toFloat() else 0f

    /** Exposed for debug/validation — the estimated acceleration magnitude in real units. */
    val accelerationMagnitudeMps2: Double
        get() = if (pixelsPerMeter > 0.0) hypot(state[4], state[5]) / pixelsPerMeter else 0.0

    private fun clampAcceleration() {
        val magPx = hypot(state[4], state[5])
        if (pixelsPerMeter <= 0.0 || magPx <= 0.0) return
        val maxPx = maxAccelerationMps2 * pixelsPerMeter
        if (magPx > maxPx) {
            val scale = maxPx / magPx
            state[4] *= scale
            state[5] *= scale
        }
    }

    private fun transitionMatrix(dt: Double): Array<DoubleArray> {
        val half = 0.5 * dt * dt
        return arrayOf(
            doubleArrayOf(1.0, 0.0, dt, 0.0, half, 0.0),
            doubleArrayOf(0.0, 1.0, 0.0, dt, 0.0, half),
            doubleArrayOf(0.0, 0.0, 1.0, 0.0, dt, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0, dt),
            doubleArrayOf(0.0, 0.0, 0.0, 0.0, 1.0, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0)
        )
    }

    /**
     * Discrete process-noise covariance for a constant-acceleration model driven by a random
     * acceleration increment of std [processNoiseSigma] each step (noise-gain G = [½dt², dt, 1]
     * per axis, Q_axis = σ²·G·Gᵀ). x and y are independent, so their state blocks
     * ({0,2,4} and {1,3,5} in the [x,y,vx,vy,ax,ay] ordering) have no cross terms.
     */
    private fun processNoise(dt: Double): Array<DoubleArray> {
        val s2 = processNoiseSigma * processNoiseSigma
        val dt2 = dt * dt; val dt3 = dt2 * dt; val dt4 = dt3 * dt
        val qpp = 0.25 * dt4 * s2; val qpv = 0.5 * dt3 * s2; val qpa = 0.5 * dt2 * s2
        val qvv = dt2 * s2; val qva = dt * s2; val qaa = s2
        val q = Array(6) { DoubleArray(6) }
        // x-axis states: pos=0, vel=2, acc=4
        q[0][0] = qpp; q[0][2] = qpv; q[0][4] = qpa
        q[2][0] = qpv; q[2][2] = qvv; q[2][4] = qva
        q[4][0] = qpa; q[4][2] = qva; q[4][4] = qaa
        // y-axis states: pos=1, vel=3, acc=5
        q[1][1] = qpp; q[1][3] = qpv; q[1][5] = qpa
        q[3][1] = qpv; q[3][3] = qvv; q[3][5] = qva
        q[5][1] = qpa; q[5][3] = qva; q[5][5] = qaa
        return q
    }
}

// ── Minimal dense matrix helpers (pure, private to this file) ──────────────────────────────

private fun matMul(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
    val rows = a.size; val inner = b.size; val cols = b[0].size
    return Array(rows) { i ->
        DoubleArray(cols) { j ->
            var sum = 0.0
            for (k in 0 until inner) sum += a[i][k] * b[k][j]
            sum
        }
    }
}

private fun matVec(m: Array<DoubleArray>, v: DoubleArray): DoubleArray =
    DoubleArray(m.size) { i ->
        var sum = 0.0
        for (j in v.indices) sum += m[i][j] * v[j]
        sum
    }

private fun matT(a: Array<DoubleArray>): Array<DoubleArray> {
    val rows = a.size; val cols = a[0].size
    return Array(cols) { i -> DoubleArray(rows) { j -> a[j][i] } }
}

private fun matAdd(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> =
    Array(a.size) { i -> DoubleArray(a[i].size) { j -> a[i][j] + b[i][j] } }

private fun matSub(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> =
    Array(a.size) { i -> DoubleArray(a[i].size) { j -> a[i][j] - b[i][j] } }

private fun identity(n: Int): Array<DoubleArray> =
    Array(n) { i -> DoubleArray(n) { j -> if (i == j) 1.0 else 0.0 } }

/** Closed-form inverse of the 2x2 innovation covariance S (positive-definite in practice). */
private fun inverse2x2(s: Array<DoubleArray>): Array<DoubleArray> {
    val det = s[0][0] * s[1][1] - s[0][1] * s[1][0]
    val safeDet = if (det != 0.0) det else 1e-9
    return arrayOf(
        doubleArrayOf(s[1][1] / safeDet, -s[0][1] / safeDet),
        doubleArrayOf(-s[1][0] / safeDet, s[0][0] / safeDet)
    )
}
