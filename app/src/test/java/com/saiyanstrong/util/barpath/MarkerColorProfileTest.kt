package com.saiyanstrong.util.barpath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerColorProfileTest {

    @Test
    fun `a profile sampled from a color matches that same color`() {
        // Bright magenta
        val profile = MarkerColorProfile.sample(230, 30, 200)
        assertTrue(profile.matches(230, 30, 200))
    }

    @Test
    fun `a profile sampled from a color matches a close variant of it`() {
        val profile = MarkerColorProfile.sample(230, 30, 200)
        // Slightly darker/less saturated version of the same marker under different lighting
        assertTrue(profile.matches(200, 60, 170))
    }

    @Test
    fun `a profile sampled from magenta rejects a clearly different hue`() {
        val profile = MarkerColorProfile.sample(230, 30, 200) // magenta
        assertFalse(profile.matches(30, 200, 60)) // green
    }

    @Test
    fun `hue distance wraps correctly around 360`() {
        assertEquals(20.0, MarkerColorProfile.hueDistance(350.0, 10.0), 0.0001)
        assertEquals(20.0, MarkerColorProfile.hueDistance(10.0, 350.0), 0.0001)
        assertEquals(0.0, MarkerColorProfile.hueDistance(0.0, 360.0) % 360.0, 0.0001)
    }

    @Test
    fun `hue distance for identical hues is zero`() {
        assertEquals(0.0, MarkerColorProfile.hueDistance(120.0, 120.0), 0.0001)
    }

    @Test
    fun `default profile matches a bright magenta marker`() {
        val default = MarkerColorProfile.default()
        assertTrue(default.matches(230, 20, 210))
    }
}
