package com.saiyanstrong.util.barpath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorPatchSamplerTest {

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** A uniform width x height grid of one color. */
    private fun solidGrid(width: Int, height: Int, r: Int, g: Int, b: Int) =
        IntArray(width * height) { argb(r, g, b) }

    @Test
    fun `samples a uniform magenta patch and locks onto that exact color`() {
        val pixels = solidGrid(40, 40, 230, 30, 200)
        val profile = sampleColorPatch(pixels, 40, 40, centerX = 20, centerY = 20, patchRadius = 8)
        assertNotNull(profile)
        assertTrue(profile!!.matches(230, 30, 200))
    }

    @Test
    fun `a uniform patch still gets a usable tolerance floor, not a zero-width range`() {
        // Zero variance in a perfectly uniform patch would collapse hueTolerance to 0 without
        // the documented floor -- verify the floor actually applies.
        val pixels = solidGrid(40, 40, 230, 30, 200)
        val profile = sampleColorPatch(pixels, 40, 40, centerX = 20, centerY = 20, patchRadius = 8)
        assertNotNull(profile)
        assertTrue(profile!!.hueTolerance >= 8.0)
    }

    @Test
    fun `center clamps into bounds for a tap near the frame edge`() {
        val pixels = solidGrid(20, 20, 100, 200, 50)
        // Tap coordinates far outside the buffer must not crash and must still sample something.
        val profile = sampleColorPatch(pixels, 20, 20, centerX = 500, centerY = -50, patchRadius = 5)
        assertNotNull(profile)
    }

    @Test
    fun `relaxes the saturation filter when too few pixels pass it`() {
        // Entirely grey/desaturated patch: the saturation-filtered pass finds nothing, so the
        // function must fall back to the unfiltered pass instead of returning null.
        val pixels = solidGrid(30, 30, 120, 120, 120) // grey, saturation ~0
        val profile = sampleColorPatch(pixels, 30, 30, centerX = 15, centerY = 15, patchRadius = 6)
        assertNotNull("a low-saturation patch should still produce a profile via the relaxed fallback", profile)
    }

    @Test
    fun `an empty pixel buffer returns null instead of crashing`() {
        assertNull(sampleColorPatch(IntArray(0), 0, 0, 0, 0))
    }

    @Test
    fun `distinguishes two different patch colors`() {
        val pixels = IntArray(40 * 40) { argb(20, 200, 60) } // green background
        // paint a magenta patch in the corner
        val mutable = pixels.copyOf()
        for (y in 25..35) for (x in 25..35) mutable[y * 40 + x] = argb(230, 30, 200)
        val magentaProfile = sampleColorPatch(mutable, 40, 40, centerX = 30, centerY = 30, patchRadius = 4)
        val greenProfile = sampleColorPatch(mutable, 40, 40, centerX = 5, centerY = 5, patchRadius = 4)
        assertNotNull(magentaProfile); assertNotNull(greenProfile)
        assertTrue(magentaProfile!!.matches(230, 30, 200))
        assertTrue(greenProfile!!.matches(20, 200, 60))
        assertTrue(!magentaProfile.matches(20, 200, 60))
    }

    @Test
    fun `circular mean of hues clustered near zero does not average to 180`() {
        // 350 degrees and 10 degrees should average to 0 (wrapping), not 180 (naive linear mean).
        val mean = circularMeanDegrees(listOf(350.0, 10.0))
        assertTrue("expected ~0 or ~360, got $mean", mean < 20.0 || mean > 340.0)
    }

    @Test
    fun `circular mean of identical hues is that hue`() {
        assertEquals(120.0, circularMeanDegrees(listOf(120.0, 120.0, 120.0)), 0.01)
    }

    @Test
    fun `circular std of identical hues is near zero`() {
        val mean = circularMeanDegrees(listOf(90.0, 90.0, 90.0))
        assertEquals(0.0, circularStdDegrees(listOf(90.0, 90.0, 90.0), mean), 0.01)
    }

    @Test
    fun `circular std grows with spread`() {
        val tight = listOf(100.0, 102.0, 98.0)
        val wide = listOf(60.0, 100.0, 140.0)
        val tightMean = circularMeanDegrees(tight)
        val wideMean = circularMeanDegrees(wide)
        val tightStd = circularStdDegrees(tight, tightMean)
        val wideStd = circularStdDegrees(wide, wideMean)
        assertTrue("wide spread ($wideStd) should exceed tight spread ($tightStd)", wideStd > tightStd)
    }
}
