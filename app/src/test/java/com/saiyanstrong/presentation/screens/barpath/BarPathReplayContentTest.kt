package com.saiyanstrong.presentation.screens.barpath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarPathReplayContentTest {

    private fun r(argb: Int) = (argb shr 16) and 0xFF
    private fun g(argb: Int) = (argb shr 8) and 0xFF
    private fun b(argb: Int) = argb and 0xFF
    private fun a(argb: Int) = (argb ushr 24) and 0xFF

    @Test
    fun `slow velocities map to red`() {
        assertEquals(0xFFFF0000.toInt(), velocityColorArgb(0.1f))
        assertEquals(0xFFFF0000.toInt(), velocityColorArgb(0.2f))
    }

    @Test
    fun `fast velocities map to green`() {
        assertEquals(0xFF00FF00.toInt(), velocityColorArgb(0.8f))
        assertEquals(0xFF00FF00.toInt(), velocityColorArgb(1.5f))
    }

    @Test
    fun `mid velocity interpolates smoothly between anchors, not a hard switch`() {
        // 0.3 is halfway between the red (0.2) and orange (0.4) anchors: red->orange means
        // green channel ramps from 0 toward 165, so mid-band green is partway, not 0 or 165.
        val c = velocityColorArgb(0.3f)
        assertEquals(255, r(c))
        assertTrue("green should be partway (was ${g(c)})", g(c) in 40..120)
        assertEquals(0, b(c))
    }

    @Test
    fun `colors are always opaque`() {
        assertEquals(255, a(velocityColorArgb(0.0f)))
        assertEquals(255, a(velocityColorArgb(0.5f)))
        assertEquals(255, a(velocityColorArgb(2.0f)))
    }

    @Test
    fun `a wide video letterboxes top and bottom`() {
        val rect = computeFittedVideoRect(100f, 100f, videoWidthPx = 200, videoHeightPx = 100)
        assertEquals(0f, rect.left, 0.001f)
        assertEquals(100f, rect.width, 0.001f)
        assertEquals(50f, rect.height, 0.001f)
        assertEquals(25f, rect.top, 0.001f)
    }

    @Test
    fun `a tall video pillarboxes left and right`() {
        val rect = computeFittedVideoRect(100f, 100f, videoWidthPx = 100, videoHeightPx = 200)
        assertEquals(0f, rect.top, 0.001f)
        assertEquals(100f, rect.height, 0.001f)
        assertEquals(50f, rect.width, 0.001f)
        assertEquals(25f, rect.left, 0.001f)
    }

    @Test
    fun `degenerate video dimensions fall back to the full container`() {
        val rect = computeFittedVideoRect(120f, 80f, videoWidthPx = 0, videoHeightPx = 0)
        assertEquals(0f, rect.left, 0.001f)
        assertEquals(0f, rect.top, 0.001f)
        assertEquals(120f, rect.width, 0.001f)
        assertEquals(80f, rect.height, 0.001f)
    }
}
