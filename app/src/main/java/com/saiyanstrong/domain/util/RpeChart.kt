package com.saiyanstrong.domain.util

import kotlin.math.roundToInt

/**
 * RPE → %1RM lookup, the standard published RTS/Tuchscherer autoregulation chart used across
 * powerlifting coaching. Rows are reps 1..12, columns are RPE 10.0 down to 6.0 in 0.5 steps —
 * values are % of estimated true 1RM.
 */
object RpeChart {

    private val RPE_STEPS = listOf(10.0, 9.5, 9.0, 8.5, 8.0, 7.5, 7.0, 6.5, 6.0)

    private val TABLE = arrayOf(
        doubleArrayOf(100.0, 97.8, 95.5, 93.9, 92.2, 90.7, 89.2, 87.8, 86.3), // 1 rep
        doubleArrayOf(95.5, 93.9, 92.2, 90.7, 89.2, 87.8, 86.3, 85.0, 83.7),  // 2 reps
        doubleArrayOf(92.2, 90.7, 89.2, 87.8, 86.3, 85.0, 83.7, 82.4, 81.1),  // 3 reps
        doubleArrayOf(89.2, 87.8, 86.3, 85.0, 83.7, 82.4, 81.1, 79.8, 78.6),  // 4 reps
        doubleArrayOf(86.3, 85.0, 83.7, 82.4, 81.1, 79.8, 78.6, 77.4, 76.2),  // 5 reps
        doubleArrayOf(83.7, 82.4, 81.1, 79.8, 78.6, 77.4, 76.2, 75.1, 73.9),  // 6 reps
        doubleArrayOf(81.1, 79.8, 78.6, 77.4, 76.2, 75.1, 73.9, 72.8, 71.7),  // 7 reps
        doubleArrayOf(78.6, 77.4, 76.2, 75.1, 73.9, 72.8, 71.7, 70.7, 69.6),  // 8 reps
        doubleArrayOf(76.2, 75.1, 73.9, 72.8, 71.7, 70.7, 69.6, 68.6, 67.6),  // 9 reps
        doubleArrayOf(73.9, 72.8, 71.7, 70.7, 69.6, 68.6, 67.6, 66.6, 65.6),  // 10 reps
        doubleArrayOf(71.7, 70.7, 69.6, 68.6, 67.6, 66.6, 65.6, 64.6, 63.7),  // 11 reps
        doubleArrayOf(69.6, 68.6, 67.6, 66.6, 65.6, 64.6, 63.7, 62.8, 61.9)   // 12 reps
    )

    /** Reps beyond 12 clamp to the 12-rep row — an explicit, documented approximation. */
    fun percentOf1Rm(reps: Int, rpe: Float): Double {
        val repRow = reps.coerceIn(1, 12) - 1
        val snappedRpe = (rpe.toDouble() * 2).roundToInt() / 2.0
        val rpeCol = RPE_STEPS.indexOf(snappedRpe.coerceIn(6.0, 10.0))
            .let { if (it >= 0) it else RPE_STEPS.size - 1 }
        return TABLE[repRow][rpeCol] / 100.0
    }

    fun estimateTrue1Rm(weightKg: Double, reps: Int, rpe: Float): Double =
        weightKg / percentOf1Rm(reps, rpe)
}
