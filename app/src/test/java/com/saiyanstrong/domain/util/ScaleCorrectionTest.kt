package com.saiyanstrong.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ScaleCorrectionTest {

    @Test
    fun `identical baseline and current diameter means no correction`() {
        assertEquals(1.0, ScaleCorrection.compute(20.0, 20.0), 0.0001)
    }

    @Test
    fun `marker appearing smaller than baseline scales displacement up`() {
        // Bar moved farther from the camera -- marker shrank to half its baseline size --
        // real displacement is under-represented in pixels, so correction should be > 1.
        val correction = ScaleCorrection.compute(baselineDiameterPx = 20.0, currentDiameterPx = 10.0)
        assertEquals(2.0, correction, 0.0001)
    }

    @Test
    fun `marker appearing larger than baseline scales displacement down`() {
        val correction = ScaleCorrection.compute(baselineDiameterPx = 10.0, currentDiameterPx = 20.0)
        assertEquals(0.5, correction, 0.0001)
    }

    @Test
    fun `correction is clamped at the upper bound for an extreme size ratio`() {
        // Both diameters clear the 3px reliability floor -- the 4x raw ratio is what gets
        // clamped here, not the floor check.
        val correction = ScaleCorrection.compute(baselineDiameterPx = 20.0, currentDiameterPx = 5.0)
        assertEquals(2.0, correction, 0.0001) // would be 4x uncorrected, clamped to 2.0
    }

    @Test
    fun `correction is clamped at the lower bound for an extreme size ratio`() {
        val correction = ScaleCorrection.compute(baselineDiameterPx = 5.0, currentDiameterPx = 20.0)
        assertEquals(0.5, correction, 0.0001)
    }

    @Test
    fun `missing baseline diameter falls back to no correction`() {
        assertEquals(1.0, ScaleCorrection.compute(baselineDiameterPx = null, currentDiameterPx = 15.0), 0.0001)
    }

    @Test
    fun `missing current diameter falls back to no correction`() {
        assertEquals(1.0, ScaleCorrection.compute(baselineDiameterPx = 15.0, currentDiameterPx = null), 0.0001)
    }

    @Test
    fun `a current diameter below the reliability floor falls back to no correction`() {
        assertEquals(1.0, ScaleCorrection.compute(baselineDiameterPx = 15.0, currentDiameterPx = 2.0), 0.0001)
    }

    @Test
    fun `a baseline diameter below the reliability floor falls back to no correction`() {
        assertEquals(1.0, ScaleCorrection.compute(baselineDiameterPx = 2.0, currentDiameterPx = 15.0), 0.0001)
    }
}
