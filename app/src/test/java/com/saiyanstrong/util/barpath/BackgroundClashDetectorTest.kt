package com.saiyanstrong.util.barpath

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundClashDetectorTest {

    private fun blob(cx: Double, cy: Double, size: Int, diameter: Double = 10.0) =
        Blob(cx, cy, size, diameter, (cx - diameter / 2).toInt(), (cy - diameter / 2).toInt(), (cx + diameter / 2).toInt(), (cy + diameter / 2).toInt())

    private val w = 320
    private val h = 240

    @Test
    fun `no blobs is clean`() {
        assertEquals(ClashVerdict.CLEAN, BackgroundClashDetector.classify(emptyList(), null, w, h))
    }

    @Test
    fun `a single marker blob is clean`() {
        val blobs = listOf(blob(20.0, 20.0, 60))
        assertEquals(ClashVerdict.CLEAN, BackgroundClashDetector.classify(blobs, 20.0 to 20.0, w, h))
    }

    @Test
    fun `a large blob far from the marker is a clash`() {
        val marker = blob(20.0, 20.0, 60)
        val background = blob(260.0, 200.0, 500, diameter = 40.0)
        val verdict = BackgroundClashDetector.classify(listOf(marker, background), 20.0 to 20.0, w, h)
        assertEquals(ClashVerdict.CLASH, verdict)
    }

    @Test
    fun `a small far blob is not a clash`() {
        val marker = blob(20.0, 20.0, 500)
        val speck = blob(260.0, 200.0, 20)
        val verdict = BackgroundClashDetector.classify(listOf(marker, speck), 20.0 to 20.0, w, h)
        assertEquals(ClashVerdict.CLEAN, verdict)
    }

    @Test
    fun `a large blob adjacent to the marker is not a clash`() {
        // The marker's own halo / a second contiguous patch right next to it — close, not "far".
        val marker = blob(100.0, 100.0, 100)
        val adjacent = blob(112.0, 104.0, 100)
        val verdict = BackgroundClashDetector.classify(listOf(marker, adjacent), 100.0 to 100.0, w, h)
        assertEquals(ClashVerdict.CLEAN, verdict)
    }
}
