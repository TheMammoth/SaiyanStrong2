package com.saiyanstrong.util.barpath

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerColorRangeBuilderTest {

    private fun hsvOf(r: Int, g: Int, b: Int) = MarkerColorMatcher.rgbToHsv(r, g, b)

    @Test
    fun `empty samples produce null`() {
        assertNull(MarkerColorRangeBuilder.build(emptyList()))
    }

    @Test
    fun `builds a profile that matches every input color`() {
        // The same magenta marker under a few brightnesses/saturations across the rep.
        val samples = listOf(
            hsvOf(230, 30, 200),
            hsvOf(200, 50, 175),
            hsvOf(255, 20, 220),
            hsvOf(210, 40, 185)
        )
        val profile = MarkerColorRangeBuilder.build(samples)!!
        assertTrue(profile.matches(230, 30, 200))
        assertTrue(profile.matches(200, 50, 175))
        assertTrue(profile.matches(255, 20, 220))
    }

    @Test
    fun `hue center wraps for reds straddling 0 and 360`() {
        // Two reds just either side of the wrap — a naive linear mean would land near 180 (cyan).
        val samples = listOf(hsvOf(255, 10, 4), hsvOf(255, 4, 12))
        val profile = MarkerColorRangeBuilder.build(samples)!!
        assertTrue("hue center should be near 0/360, was ${profile.hueCenter}",
            profile.hueCenter < 30.0 || profile.hueCenter > 330.0)
        assertTrue(profile.matches(255, 8, 8))
    }

    @Test
    fun `a wider saturation spread lowers the saturation floor`() {
        val tight = listOf(hsvOf(230, 30, 200), hsvOf(232, 28, 202))
        // Same hue family, but saturation varies a lot (bright vs washed-out pink).
        val wide = listOf(hsvOf(230, 30, 200), hsvOf(220, 150, 205))
        val tightFloor = MarkerColorRangeBuilder.build(tight)!!.minSaturation
        val wideFloor = MarkerColorRangeBuilder.build(wide)!!.minSaturation
        assertTrue("a wider spread ($wideFloor) should floor lower than a tight one ($tightFloor)",
            wideFloor < tightFloor)
    }

    @Test
    fun `a uniform sample set keeps a usable hue tolerance floor`() {
        val samples = List(20) { hsvOf(230, 30, 200) }
        assertTrue(MarkerColorRangeBuilder.build(samples)!!.hueTolerance >= 8.0)
    }
}
