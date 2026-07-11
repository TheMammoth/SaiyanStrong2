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

    @Test
    fun `matchScore for the exact sampled color is close to its maximum`() {
        val profile = MarkerColorProfile.sample(230, 30, 200)
        assertEquals(1.0, profile.matchScore(230, 30, 200), 0.001)
    }

    @Test
    fun `matchScore decreases as a pixel drifts further from the sample`() {
        val profile = MarkerColorProfile.sample(230, 30, 200)
        val close = profile.matchScore(220, 35, 195)
        val far = profile.matchScore(180, 80, 150)
        assertTrue("close ($close) should score higher than far ($far)", close > far)
    }

    @Test
    fun `matchScore never goes negative even for a pixel far outside tolerance`() {
        val profile = MarkerColorProfile.sample(230, 30, 200)
        assertEquals(0.0, profile.matchScore(30, 200, 60), 0.0001) // green, nowhere near magenta
    }

    @Test
    fun `a pixel brighter than the sample can still pass matches but score below the maximum`() {
        // matches() only enforces a floor on value/saturation -- a pixel well above the sample
        // still clears the floor but isn't close to the sampled center, so matchScore should
        // reflect that distance even though matches() alone would call it a match.
        val profile = MarkerColorProfile.sample(230, 30, 200)
        assertTrue(profile.matches(255, 0, 255))
        assertTrue(profile.matchScore(255, 0, 255) < 1.0)
    }

    @Test
    fun `widened scales all three tolerance bands by the given factor`() {
        val profile = MarkerColorProfile.sample(230, 30, 200)
        val widened = profile.widened(1.2)
        assertEquals(profile.hueTolerance * 1.2, widened.hueTolerance, 0.0001)
        assertEquals(profile.satTolerance * 1.2, widened.satTolerance, 0.0001)
        assertEquals(profile.valTolerance * 1.2, widened.valTolerance, 0.0001)
    }

    @Test
    fun `widened leaves the floor-based accept-reject fields untouched`() {
        val profile = MarkerColorProfile.sample(230, 30, 200)
        val widened = profile.widened(1.2)
        assertEquals(profile.minSaturation, widened.minSaturation, 0.0001)
        assertEquals(profile.minValue, widened.minValue, 0.0001)
        assertEquals(profile.hueCenter, widened.hueCenter, 0.0001)
    }

    @Test
    fun `widened tolerance scores a slightly-off pixel at least as well as the original`() {
        val profile = MarkerColorProfile.sample(230, 30, 200)
        val original = profile.matchScore(200, 60, 170)
        val wider = profile.widened(1.2).matchScore(200, 60, 170)
        assertTrue("widened tolerance should not score a near-boundary pixel worse", wider >= original)
    }
}
