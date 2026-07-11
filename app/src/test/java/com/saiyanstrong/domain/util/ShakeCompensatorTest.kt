package com.saiyanstrong.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ShakeCompensatorTest {

    @Test
    fun `compensate subtracts the gyro-predicted shift`() {
        // yaw 0.02 rad -> horizontal shift 1000*0.02 = 20 px; pitch 0.01 -> vertical shift 10 px.
        val result = ShakeCompensator.compensate(
            rawX = 100.0, rawY = 100.0, cumulativeAngleX = 0.01, cumulativeAngleY = 0.02, focalLengthPx = 1000.0
        )
        assertEquals(80.0, result.x, 0.0001)
        assertEquals(90.0, result.y, 0.0001)
    }

    @Test
    fun `zero rotation leaves the centroid unchanged`() {
        val result = ShakeCompensator.compensate(300.0, 400.0, 0.0, 0.0, 1000.0)
        assertEquals(300.0, result.x, 0.0001)
        assertEquals(400.0, result.y, 0.0001)
    }

    @Test
    fun `focalLengthPx follows the pinhole formula`() {
        // (4mm / 5mm) * 1000px = 800px
        assertEquals(800.0, ShakeCompensator.focalLengthPx(4.0, 5.0, 1000), 0.0001)
    }

    @Test
    fun `focalLengthPx guards invalid input`() {
        assertEquals(0.0, ShakeCompensator.focalLengthPx(4.0, 0.0, 1000), 0.0001)
        assertEquals(0.0, ShakeCompensator.focalLengthPx(4.0, 5.0, 0), 0.0001)
    }
}
