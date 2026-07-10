package com.saiyanstrong.util.barpath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only the pure pixel-grid centroid detection is unit-testable; the ImageProxy/CameraX plumbing needs a device. */
class BarPathLiveAnalyzerTest {

    private val magenta = (0xFF shl 24) or (230 shl 16) or (20 shl 8) or 210
    private val black = 0xFF shl 24

    /** width x height ARGB grid, black everywhere except the given magenta rectangles. */
    private fun grid(width: Int, height: Int, vararg rects: IntArray): IntArray {
        val px = IntArray(width * height) { black }
        for (r in rects) {
            val (x0, y0, x1, y1) = r
            for (y in y0..y1) for (x in x0..x1) px[y * width + x] = magenta
        }
        return px
    }

    @Test
    fun `detects the centroid of a single magenta blob`() {
        // 3x3 magenta block spanning cols 2..4, rows 2..4 -> centroid (3,3).
        val pixels = grid(10, 10, intArrayOf(2, 2, 4, 4))
        val result = detectMarkerCentroidInPixels(pixels, 10, 10, MarkerColorProfile.default(), previousCentroid = null)
        assertNotNull(result)
        assertEquals(3.0, result!!.first, 0.5)
        assertEquals(3.0, result.second, 0.5)
    }

    @Test
    fun `returns null when there is no matching marker`() {
        val pixels = grid(10, 10) // all black
        assertNull(detectMarkerCentroidInPixels(pixels, 10, 10, MarkerColorProfile.default(), previousCentroid = null))
    }

    @Test
    fun `returns null for a blob below the minimum pixel count`() {
        // a single magenta pixel (< minMarkerPixels)
        val pixels = grid(10, 10, intArrayOf(5, 5, 5, 5))
        assertNull(detectMarkerCentroidInPixels(pixels, 10, 10, MarkerColorProfile.default(), previousCentroid = null))
    }

    @Test
    fun `with a previous position, tracks the nearer of two blobs`() {
        val pixels = grid(
            10, 10,
            intArrayOf(1, 1, 3, 3),  // blob A, centroid ~(2,2)
            intArrayOf(6, 6, 8, 8)   // blob B, centroid ~(7,7)
        )
        val nearB = detectMarkerCentroidInPixels(pixels, 10, 10, MarkerColorProfile.default(), previousCentroid = 7.0 to 7.0)
        assertNotNull(nearB)
        assertTrue("expected the (7,7) blob, got $nearB", nearB!!.first > 5.0 && nearB.second > 5.0)

        val nearA = detectMarkerCentroidInPixels(pixels, 10, 10, MarkerColorProfile.default(), previousCentroid = 2.0 to 2.0)
        assertTrue("expected the (2,2) blob, got $nearA", nearA!!.first < 5.0 && nearA.second < 5.0)
    }
}
