package com.saiyanstrong.domain.util

import kotlin.math.abs

/**
 * Savitzky-Golay smoothing/differentiation (quadratic local fit) for a 1D position series —
 * pure Kotlin, no Android dependency. Replaces simple frame-to-frame finite differences, which
 * produce spiky velocity curves from small marker-tracking jitter between consecutive frames.
 *
 * [smooth] fits a local quadratic over evenly-spaced integer offsets (index-based, no timestamps
 * needed) and returns the fitted value at each point's own position — standard SG smoothing.
 *
 * [differentiate] fits a local quadratic against each window's REAL timestamps (not assumed
 * evenly spaced — real frame extraction timestamps aren't perfectly uniform) and returns the
 * analytical derivative of that fit at the center point, which is a materially better velocity
 * estimate than finite-differencing the raw (noisy) positions directly.
 */
object SavitzkyGolayFilter {

    /**
     * @param windowSize should be odd; if even, the odd window `2*(windowSize/2)+1` is used.
     * Near the edges (fewer than `windowSize/2` neighbors on one side), the fit uses whatever
     * partial, still-symmetric-when-possible window is available rather than falling back to
     * the raw value, so the smoothed series stays continuous with the interior.
     */
    fun smooth(positions: List<Double>, windowSize: Int = 7): List<Double> {
        val n = positions.size
        if (n < 3) return positions
        val k = windowSize.coerceAtLeast(3) / 2

        return List(n) { i ->
            val start = (i - k).coerceAtLeast(0)
            val end = (i + k).coerceAtMost(n - 1)
            if (end - start < 2) return@List positions[i]

            val xs = DoubleArray(end - start + 1) { j -> (start + j - i).toDouble() }
            val ys = DoubleArray(end - start + 1) { j -> positions[start + j] }
            fitLocalQuadratic(xs, ys)?.first ?: positions[i]
        }
    }

    /**
     * Returns velocity (position-units per second) at every input point. `timestamps` are in
     * milliseconds (converted to seconds internally). One output value per input position.
     *
     * Per-point behavior:
     * - Fewer than [windowSize] samples total: finite differences for the whole series (no SG
     *   fit is attempted — too little data to trust a quadratic model over).
     * - The first/last `windowSize/2` points (not enough neighbors for a full symmetric
     *   window): finite differences for that point specifically.
     * - Otherwise: analytical derivative of a quadratic fit to that point's full symmetric
     *   window of real timestamps.
     */
    fun differentiate(positions: List<Double>, timestamps: List<Double>, windowSize: Int = 7): List<Double> {
        require(positions.size == timestamps.size) { "positions and timestamps must be the same size" }
        val n = positions.size
        if (n < 2) return List(n) { 0.0 }
        val timestampsSec = timestamps.map { it / 1000.0 }

        fun finiteDifference(i: Int): Double {
            val (loIdx, hiIdx) = when {
                i == 0 -> 0 to 1
                i == n - 1 -> n - 2 to n - 1
                else -> i - 1 to i + 1
            }
            val dt = timestampsSec[hiIdx] - timestampsSec[loIdx]
            if (dt == 0.0) return 0.0
            return (positions[hiIdx] - positions[loIdx]) / dt
        }

        if (n < windowSize) return List(n) { i -> finiteDifference(i) }

        val k = windowSize / 2
        return List(n) { i ->
            if (i < k || i >= n - k) {
                finiteDifference(i)
            } else {
                val xs = DoubleArray(windowSize) { j -> timestampsSec[i - k + j] - timestampsSec[i] }
                val ys = DoubleArray(windowSize) { j -> positions[i - k + j] }
                fitLocalQuadratic(xs, ys)?.second ?: finiteDifference(i)
            }
        }
    }

    /**
     * Least-squares fit of y = a + b*x + c*x^2 over the given points via the normal equations,
     * solved with Gaussian elimination (partial pivoting) rather than a hand-derived closed
     * form — fewer places for an algebra mistake to hide. Returns (a, b, c), or null if the
     * system is singular (e.g. all x values identical).
     */
    private fun fitLocalQuadratic(xs: DoubleArray, ys: DoubleArray): Triple<Double, Double, Double>? {
        var s0 = 0.0; var s1 = 0.0; var s2 = 0.0; var s3 = 0.0; var s4 = 0.0
        var sy = 0.0; var sxy = 0.0; var sx2y = 0.0
        for (j in xs.indices) {
            val x = xs[j]; val y = ys[j]
            val x2 = x * x
            s0 += 1.0; s1 += x; s2 += x2; s3 += x2 * x; s4 += x2 * x2
            sy += y; sxy += x * y; sx2y += x2 * y
        }
        val matrix = arrayOf(
            doubleArrayOf(s0, s1, s2),
            doubleArrayOf(s1, s2, s3),
            doubleArrayOf(s2, s3, s4)
        )
        val solved = solve3x3(matrix, doubleArrayOf(sy, sxy, sx2y)) ?: return null
        return Triple(solved[0], solved[1], solved[2])
    }

    private fun solve3x3(matrix: Array<DoubleArray>, rhs: DoubleArray): DoubleArray? {
        val a = Array(3) { i -> doubleArrayOf(matrix[i][0], matrix[i][1], matrix[i][2], rhs[i]) }
        for (col in 0..2) {
            var pivotRow = col
            for (row in col + 1..2) {
                if (abs(a[row][col]) > abs(a[pivotRow][col])) pivotRow = row
            }
            if (abs(a[pivotRow][col]) < 1e-12) return null
            val tmp = a[col]; a[col] = a[pivotRow]; a[pivotRow] = tmp
            for (row in 0..2) {
                if (row == col) continue
                val factor = a[row][col] / a[col][col]
                for (c in col..3) a[row][c] -= factor * a[col][c]
            }
        }
        return doubleArrayOf(a[0][3] / a[0][0], a[1][3] / a[1][1], a[2][3] / a[2][2])
    }
}
