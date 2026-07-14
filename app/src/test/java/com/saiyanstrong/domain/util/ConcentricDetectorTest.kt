package com.saiyanstrong.domain.util

import com.saiyanstrong.domain.model.BarPathSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConcentricDetectorTest {

    /** yPx increases downward, so a bigger yPx = physically lower. Each sample 100ms apart. */
    private fun series(vararg yPx: Double): List<BarPathSample> =
        yPx.mapIndexed { i, y -> BarPathSample(timestampMs = i * 100L, xPx = 0.0, yPx = y) }

    @Test
    fun `full squat down-then-up returns the ascent, not the whole clip`() {
        // Stand(10) -> descend -> bottom(50) -> ascend -> stand(10). Bottom is index 4 (400ms),
        // top of the ascent is the last sample (index 8, 800ms).
        val samples = series(10.0, 25.0, 40.0, 48.0, 50.0, 40.0, 25.0, 12.0, 10.0)
        val (startMs, endMs) = ConcentricDetector.detect(samples)!!
        assertEquals("concentric should start at the bottom", 400L, startMs)
        assertEquals("concentric should end at lockout (highest point after bottom)", 800L, endMs)
    }

    @Test
    fun `monotonic upward pull (deadlift) returns approximately the whole clip`() {
        // Floor start(50) straight up to lockout(10) — the bottom is the first sample, so the
        // window is essentially the whole clip.
        val samples = series(50.0, 42.0, 33.0, 25.0, 16.0, 10.0)
        val (startMs, endMs) = ConcentricDetector.detect(samples)!!
        assertEquals(0L, startMs)
        assertEquals(500L, endMs)
    }

    @Test
    fun `net displacement over the detected window is genuinely upward for a full rep`() {
        val samples = series(10.0, 30.0, 50.0, 30.0, 10.0)
        val (startMs, endMs) = ConcentricDetector.detect(samples)!!
        val startY = samples.first { it.timestampMs == startMs }.yPx
        val endY = samples.first { it.timestampMs == endMs }.yPx
        // Up = yPx decreasing, so the end must be higher (smaller yPx) than the start.
        assertTrue("window should have real upward displacement", endY < startY)
    }

    @Test
    fun `pure descent falls back to the whole clip (no ascent to isolate)`() {
        // Bar only ever goes down — there is no concentric to find, so don't return a 1-sample
        // window; fall back to the whole clip and let the caller/analyzer handle it.
        val samples = series(10.0, 20.0, 30.0, 40.0, 50.0)
        val (startMs, endMs) = ConcentricDetector.detect(samples)!!
        assertEquals(0L, startMs)
        assertEquals(400L, endMs)
    }

    @Test
    fun `too-short an ascent falls back to the whole clip`() {
        // Bottom at index 3, then only a single higher sample after it (window size 2 < min 3).
        val samples = series(10.0, 30.0, 45.0, 50.0, 48.0)
        val (startMs, endMs) = ConcentricDetector.detect(samples)!!
        assertEquals(0L, startMs)
        assertEquals(400L, endMs)
    }

    @Test
    fun `fewer than two samples returns null`() {
        assertNull(ConcentricDetector.detect(emptyList()))
        assertNull(ConcentricDetector.detect(series(10.0)))
    }

    @Test
    fun `unordered input is sorted by timestamp before detection`() {
        val shuffled = listOf(
            BarPathSample(400L, 0.0, 10.0),
            BarPathSample(0L, 0.0, 10.0),
            BarPathSample(200L, 0.0, 50.0),
            BarPathSample(100L, 0.0, 30.0),
            BarPathSample(300L, 0.0, 30.0)
        )
        val (startMs, endMs) = ConcentricDetector.detect(shuffled)!!
        assertEquals("bottom is the 50.0 sample at 200ms", 200L, startMs)
        assertEquals("ascent ends at the last (highest) sample, 400ms", 400L, endMs)
    }
}
