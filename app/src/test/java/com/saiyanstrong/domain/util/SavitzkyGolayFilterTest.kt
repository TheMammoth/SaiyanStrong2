package com.saiyanstrong.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class SavitzkyGolayFilterTest {

    @Test
    fun `derivative of a sine wave matches the analytical cosine within 5 percent`() {
        val n = 60
        val timestamps = (0 until n).map { it * 20.0 } // ms, 20ms apart
        val omega = 2.0 * Math.PI // 1 Hz
        val positions = timestamps.map { tMs -> sin(omega * (tMs / 1000.0)) }

        val velocities = SavitzkyGolayFilter.differentiate(positions, timestamps, windowSize = 7)

        val i = n / 2 // interior point, away from a cos zero-crossing
        val tSec = timestamps[i] / 1000.0
        val expected = omega * cos(omega * tSec)
        val relativeError = abs(velocities[i] - expected) / abs(expected)
        assertTrue(
            "expected ~$expected, got ${velocities[i]} (relative error $relativeError)",
            relativeError < 0.05
        )
    }

    @Test
    fun `velocity is zero everywhere for a constant position series`() {
        val positions = List(20) { 5.0 }
        val timestamps = (0 until 20).map { it * 33.0 }
        val velocities = SavitzkyGolayFilter.differentiate(positions, timestamps)
        velocities.forEach { assertEquals(0.0, it, 0.0001) }
    }

    @Test
    fun `a series shorter than windowSize does not throw and returns one value per input`() {
        val positions = listOf(1.0, 2.0, 4.0)
        val timestamps = listOf(0.0, 33.0, 66.0)
        val velocities = SavitzkyGolayFilter.differentiate(positions, timestamps, windowSize = 7)
        assertEquals(3, velocities.size)
    }

    @Test
    fun `constant velocity series differentiates to the constant slope everywhere`() {
        // 0.8 units per 100ms = 8.0 units per second, perfectly linear
        val positions = (0..10).map { it * 0.08 }
        val timestamps = (0..10).map { it * 100.0 }
        val velocities = SavitzkyGolayFilter.differentiate(positions, timestamps)
        velocities.forEach { assertEquals(0.8, it, 0.001) }
    }

    @Test
    fun `smoothing a constant series returns the same constant`() {
        val positions = List(10) { 3.0 }
        SavitzkyGolayFilter.smooth(positions).forEach { assertEquals(3.0, it, 0.0001) }
    }

    @Test
    fun `smoothing reduces a single-sample spike in an otherwise linear series`() {
        val noisy = (0..10).map { it.toDouble() }.toMutableList().also { it[5] = it[5] + 5.0 }
        val smoothed = SavitzkyGolayFilter.smooth(noisy)
        assertTrue(smoothed[5] < noisy[5])
    }

    @Test
    fun `smoothing too few points returns the input unchanged`() {
        val positions = listOf(1.0, 2.0)
        assertEquals(positions, SavitzkyGolayFilter.smooth(positions))
    }
}
