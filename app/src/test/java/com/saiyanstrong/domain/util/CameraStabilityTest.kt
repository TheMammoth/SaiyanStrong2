package com.saiyanstrong.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraStabilityTest {

    @Test
    fun `magnitude computes the euclidean norm of the three axes`() {
        assertEquals(5f, angularVelocityMagnitude(3f, 4f, 0f), 0.0001f)
        assertEquals(0f, angularVelocityMagnitude(0f, 0f, 0f), 0.0001f)
    }

    @Test
    fun `below 0_05 rad-s classifies as STABLE`() {
        assertEquals(StabilityLevel.STABLE, CameraStability.classify(0.0f))
        assertEquals(StabilityLevel.STABLE, CameraStability.classify(0.049f))
    }

    @Test
    fun `between 0_05 and 0_15 rad-s classifies as SETTLING`() {
        assertEquals(StabilityLevel.SETTLING, CameraStability.classify(0.05f))
        assertEquals(StabilityLevel.SETTLING, CameraStability.classify(0.1f))
        assertEquals(StabilityLevel.SETTLING, CameraStability.classify(0.149f))
    }

    @Test
    fun `at or above 0_15 rad-s classifies as MOVING`() {
        assertEquals(StabilityLevel.MOVING, CameraStability.classify(0.15f))
        assertEquals(StabilityLevel.MOVING, CameraStability.classify(5.0f))
    }
}
