package com.saiyanstrong.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GyroTimelineTest {

    private fun timeline() = GyroTimeline().apply {
        addSample(0L, 0.0, 0.0)
        addSample(100L, 0.1, 0.2)
        addSample(200L, 0.3, 0.4)
    }

    @Test
    fun `empty timeline returns zero`() {
        val (x, y) = GyroTimeline().cumulativeAngleAt(50L)
        assertEquals(0.0, x, 0.0001); assertEquals(0.0, y, 0.0001)
        assertTrue(GyroTimeline().isEmpty)
    }

    @Test
    fun `interpolates linearly between samples`() {
        val (x, y) = timeline().cumulativeAngleAt(50L) // halfway 0->100
        assertEquals(0.05, x, 0.0001)
        assertEquals(0.10, y, 0.0001)
        val (x2, y2) = timeline().cumulativeAngleAt(150L) // halfway 100->200
        assertEquals(0.20, x2, 0.0001)
        assertEquals(0.30, y2, 0.0001)
    }

    @Test
    fun `hits sample values exactly`() {
        val (x, y) = timeline().cumulativeAngleAt(100L)
        assertEquals(0.1, x, 0.0001); assertEquals(0.2, y, 0.0001)
    }

    @Test
    fun `clamps outside the recorded range`() {
        val before = timeline().cumulativeAngleAt(-50L)
        assertEquals(0.0, before.first, 0.0001); assertEquals(0.0, before.second, 0.0001)
        val after = timeline().cumulativeAngleAt(500L)
        assertEquals(0.3, after.first, 0.0001); assertEquals(0.4, after.second, 0.0001)
    }
}
