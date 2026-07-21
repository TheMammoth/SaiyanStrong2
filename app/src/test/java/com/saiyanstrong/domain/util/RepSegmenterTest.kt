package com.saiyanstrong.domain.util

import com.saiyanstrong.domain.model.BarPathSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepSegmenterTest {

    // yPx from "height above floor" (bar higher => smaller yPx). Samples 100ms apart.
    private fun samplesFromHeights(heights: List<Double>): List<BarPathSample> =
        heights.mapIndexed { i, h -> BarPathSample(i * 100L, 0.0, -h) }

    /** A triangle wave of [reps] reps, each rising 0→amp then falling amp→0, [stepsPerPhase] samples per phase. */
    private fun triangleReps(reps: Int, amp: Double = 100.0, stepsPerPhase: Int = 10): List<Double> {
        val h = ArrayList<Double>()
        for (r in 0 until reps) {
            for (s in 0..stepsPerPhase) h.add(amp * s / stepsPerPhase)        // up (concentric)
            for (s in 1..stepsPerPhase) h.add(amp * (stepsPerPhase - s) / stepsPerPhase) // down (eccentric)
        }
        return h
    }

    @Test
    fun `three clean reps produce three concentric windows in time order`() {
        val windows = RepSegmenter.segment(samplesFromHeights(triangleReps(3)))
        assertEquals(3, windows.size)
        // Each window starts at a bottom and ends at the following top, and windows are ordered.
        assertTrue(windows[0].first < windows[0].second)
        assertTrue(windows[0].second < windows[1].first)
        assertTrue(windows[1].second < windows[2].first)
    }

    @Test
    fun `a small bounce between reps is not counted as a rep`() {
        // Two real reps (amp 100) with a tiny 10-unit bump wedged between them.
        val h = ArrayList<Double>()
        h += triangleReps(1, amp = 100.0)
        for (s in 0..5) h.add(10.0 * s / 5) // tiny bounce up
        for (s in 1..5) h.add(10.0 * (5 - s) / 5) // and down
        h += triangleReps(1, amp = 100.0)
        val windows = RepSegmenter.segment(samplesFromHeights(h))
        assertEquals("the 10-unit bounce is below the ROM threshold", 2, windows.size)
    }

    @Test
    fun `a single rep yields exactly one window`() {
        assertEquals(1, RepSegmenter.segment(samplesFromHeights(triangleReps(1))).size)
    }

    @Test
    fun `a clip that only rises (deadlift floor-to-lockout) yields one window`() {
        val rising = (0..20).map { it * 5.0 }
        assertEquals(1, RepSegmenter.segment(samplesFromHeights(rising)).size)
    }

    @Test
    fun `a flat clip falls back to at most one window`() {
        val flat = List(10) { 50.0 }
        assertTrue(RepSegmenter.segment(samplesFromHeights(flat)).size <= 1)
    }

    @Test
    fun `too few samples fall back rather than crash`() {
        val windows = RepSegmenter.segment(samplesFromHeights(listOf(0.0, 50.0, 100.0)))
        assertTrue(windows.size <= 1)
    }
}
