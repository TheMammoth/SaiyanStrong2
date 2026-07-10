package com.saiyanstrong.util.barpath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerColorMatcherTest {

    @Test
    fun `pure magenta converts to hue 300, full saturation and value`() {
        val (hue, saturation, value) = MarkerColorMatcher.rgbToHsv(255, 0, 255)
        assertEquals(300.0, hue, 0.5)
        assertEquals(1.0, saturation, 0.01)
        assertEquals(1.0, value, 0.01)
    }

    @Test
    fun `pure red converts to hue 0`() {
        val (hue, _, _) = MarkerColorMatcher.rgbToHsv(255, 0, 0)
        assertEquals(0.0, hue, 0.5)
    }

    @Test
    fun `pure green converts to hue 120`() {
        val (hue, _, _) = MarkerColorMatcher.rgbToHsv(0, 255, 0)
        assertEquals(120.0, hue, 0.5)
    }

    @Test
    fun `grayscale has zero saturation regardless of brightness`() {
        val (_, saturation, _) = MarkerColorMatcher.rgbToHsv(128, 128, 128)
        assertEquals(0.0, saturation, 0.01)
    }

    @Test
    fun `bright magenta-pink marker color matches`() {
        assertTrue(MarkerColorMatcher.matchesRgb(255, 20, 180))
    }

    @Test
    fun `pure magenta matches`() {
        assertTrue(MarkerColorMatcher.matchesRgb(255, 0, 255))
    }

    @Test
    fun `pure red does not match`() {
        assertFalse(MarkerColorMatcher.matchesRgb(255, 0, 0))
    }

    @Test
    fun `black does not match — value too low`() {
        assertFalse(MarkerColorMatcher.matchesRgb(0, 0, 0))
    }

    @Test
    fun `white does not match — saturation too low`() {
        assertFalse(MarkerColorMatcher.matchesRgb(255, 255, 255))
    }

    @Test
    fun `dark gray plate color does not match`() {
        assertFalse(MarkerColorMatcher.matchesRgb(50, 50, 50))
    }

    @Test
    fun `approximate skin tone does not false-positive`() {
        assertFalse(MarkerColorMatcher.matchesRgb(224, 172, 144))
    }

    @Test
    fun `desaturated pink does not match — below saturation threshold`() {
        // Same hue family as the marker but washed out (low saturation) — should be rejected.
        assertFalse(MarkerColorMatcher.matchesRgb(220, 180, 210))
    }
}
