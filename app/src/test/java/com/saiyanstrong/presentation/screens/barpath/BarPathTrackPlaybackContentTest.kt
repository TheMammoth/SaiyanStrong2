package com.saiyanstrong.presentation.screens.barpath

import com.saiyanstrong.domain.model.BarPathSample
import com.saiyanstrong.domain.model.TrackedFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ── smoothedPathPoints ───────────────────────────────────────────────────────────

    private fun samplesXY(vararg xy: Pair<Double, Double>): List<BarPathSample> =
        xy.mapIndexed { i, (x, y) -> BarPathSample(i * 100L, x, y) }

    @Test
    fun `smoothing a short series returns it unchanged`() {
        val s = samplesXY(0.0 to 0.0, 10.0 to 10.0)
        assertEquals(listOf(0.0 to 0.0, 10.0 to 10.0), smoothedPathPoints(s))
    }

    @Test
    fun `smoothing pulls a single jittery point toward its neighbors`() {
        // A straight line at y=0 with one spike up to y=100 in the middle.
        val s = samplesXY(0.0 to 0.0, 1.0 to 0.0, 2.0 to 100.0, 3.0 to 0.0, 4.0 to 0.0)
        val smoothed = smoothedPathPoints(s, window = 5)
        // The spike (index 2) should be pulled far below 100 by its zero neighbors.
        assertTrue("spike should be smoothed down, was ${smoothed[2].second}", smoothed[2].second < 40.0)
        // Endpoints stay put-ish (fewer neighbors), still index-aligned.
        assertEquals(s.size, smoothed.size)
    }

    // ── smoothedFramePoints (replay path) ──────────────────────────────────────────────

    private fun frames(vararg xy: Pair<Double, Double>): List<TrackedFrame> =
        xy.mapIndexed { i, (x, y) -> TrackedFrame(i * 100L, x, y, 0.0) }

    @Test
    fun `frame smoothing returns a short series unchanged`() {
        val f = frames(0.0 to 0.0, 10.0 to 10.0)
        assertEquals(listOf(0.0 to 0.0, 10.0 to 10.0), smoothedFramePoints(f))
    }

    @Test
    fun `frame smoothing pulls a jittery point toward its neighbors and stays index-aligned`() {
        val f = frames(0.0 to 0.0, 1.0 to 0.0, 2.0 to 100.0, 3.0 to 0.0, 4.0 to 0.0)
        val smoothed = smoothedFramePoints(f, window = 5)
        assertTrue("spike should be smoothed down, was ${smoothed[2].second}", smoothed[2].second < 40.0)
        assertEquals(f.size, smoothed.size)
    }

    // ── loupeSource (magnifier crop) ───────────────────────────────────────────────────

    @Test
    fun `loupe window is centred and sized by zoom in the interior`() {
        // 120px loupe at 3x → 40px source window, centred on (500,500).
        val src = loupeSource(500.0, 500.0, 1080, 1920, 120, 3f)!!
        assertEquals(40, src.size)
        assertEquals(480, src.x) // 500 - 20
        assertEquals(480, src.y)
    }

    @Test
    fun `loupe window near an edge is shifted fully inside, not shrunk`() {
        val src = loupeSource(5.0, 5.0, 1080, 1920, 120, 3f)!!
        assertEquals(40, src.size) // unchanged size
        assertEquals(0, src.x)
        assertEquals(0, src.y)
        val br = loupeSource(1079.0, 1919.0, 1080, 1920, 120, 3f)!!
        assertEquals(1080 - br.size, br.x)
        assertEquals(1920 - br.size, br.y)
    }

    @Test
    fun `loupe returns null for a degenerate bitmap`() {
        assertNull(loupeSource(10.0, 10.0, 0, 100, 120, 3f))
        assertNull(loupeSource(10.0, 10.0, 100, 100, 120, 0f))
    }
}
