package com.saiyanstrong.presentation.screens.barpath

import com.saiyanstrong.domain.model.BarPathSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarPathTrackPlaybackContentTest {

    private fun samples(vararg ts: Long): List<BarPathSample> =
        ts.map { BarPathSample(timestampMs = it, xPx = 0.0, yPx = 0.0) }

    @Test
    fun `empty samples returns -1`() {
        assertEquals(-1, currentSampleIndex(emptyList(), 100L))
    }

    @Test
    fun `before the first sample clamps to 0`() {
        val s = samples(100L, 200L, 300L)
        assertEquals(0, currentSampleIndex(s, 0L))
        assertEquals(0, currentSampleIndex(s, 50L))
    }

    @Test
    fun `picks the latest sample at or before the playback time`() {
        val s = samples(0L, 100L, 200L, 300L)
        assertEquals(0, currentSampleIndex(s, 0L))
        assertEquals(1, currentSampleIndex(s, 150L))
        assertEquals(2, currentSampleIndex(s, 200L)) // exact boundary lands on that sample
        assertEquals(2, currentSampleIndex(s, 299L))
    }

    @Test
    fun `after the last sample returns the last index`() {
        val s = samples(0L, 100L, 200L)
        assertEquals(2, currentSampleIndex(s, 999L))
    }

    // ── screenToVideoPx ──────────────────────────────────────────────────────────────

    @Test
    fun `tap at the center of a matched-aspect video maps to the video center`() {
        // 100x100 container, 100x100 video → rect fills the container, no letterbox.
        val (vx, vy) = screenToVideoPx(50f, 50f, 100f, 100f, 200, 200)!!
        assertEquals(100f, vx, 1e-3f)
        assertEquals(100f, vy, 1e-3f)
    }

    @Test
    fun `tap in the letterbox margin returns null`() {
        // 100x100 container, 200x100 video (wider) → full width, 50px tall band centered:
        // rect top = 25, height = 50. A tap at y=5 is in the top letterbox margin.
        assertNull(screenToVideoPx(50f, 5f, 100f, 100f, 200, 100))
    }

    @Test
    fun `tap inside the fitted rect of a letterboxed video maps into the image`() {
        // Same 200x100 video in 100x100: rect = (0,25,100,50). Tap at the rect's top-left corner.
        val (vx, vy) = screenToVideoPx(0f, 25f, 100f, 100f, 200, 100)!!
        assertEquals(0f, vx, 1e-3f)
        assertEquals(0f, vy, 1e-3f)
        // Bottom-right of the fitted rect maps to the video's bottom-right.
        val (vx2, vy2) = screenToVideoPx(100f, 75f, 100f, 100f, 200, 100)!!
        assertEquals(200f, vx2, 1e-3f)
        assertEquals(100f, vy2, 1e-3f)
    }

    @Test
    fun `degenerate dimensions return null`() {
        assertNull(screenToVideoPx(10f, 10f, 100f, 100f, 0, 100))
        assertNull(screenToVideoPx(10f, 10f, 0f, 100f, 100, 100))
    }
}
