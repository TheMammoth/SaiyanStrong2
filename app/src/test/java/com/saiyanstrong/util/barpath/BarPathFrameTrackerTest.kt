package com.saiyanstrong.util.barpath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** width x height boolean grid from a row-major string, '#' = match, '.' = no match. */
private fun grid(width: Int, rows: List<String>): BooleanArray {
    val mask = BooleanArray(width * rows.size)
    rows.forEachIndexed { y, row ->
        row.forEachIndexed { x, c -> mask[y * width + x] = c == '#' }
    }
    return mask
}

class BarPathFrameTrackerTest {

    @Test
    fun `two separated objects produce two distinct blobs, not one merged centroid`() {
        val mask = grid(
            10, listOf(
                "##........",
                "##........",
                "..........",
                "........##",
                "........##"
            )
        )
        val blobs = findBlobs(mask, 10, 5)
        assertEquals(2, blobs.size)
    }

    @Test
    fun `a single contiguous blob reports the correct centroid and size`() {
        val mask = grid(4, listOf("##", "##").map { it.padEnd(4, '.') })
        val blobs = findBlobs(mask, 4, 2)
        assertEquals(1, blobs.size)
        assertEquals(4, blobs.single().size)
        assertEquals(0.5, blobs.single().centroidX, 0.0001)
        assertEquals(0.5, blobs.single().centroidY, 0.0001)
    }

    @Test
    fun `no matches produce no blobs`() {
        assertTrue(findBlobs(BooleanArray(100), 10, 10).isEmpty())
    }

    @Test
    fun `with no previous position, the largest blob is chosen`() {
        val small = Blob(centroidX = 0.0, centroidY = 0.0, size = 5)
        val large = Blob(centroidX = 50.0, centroidY = 50.0, size = 40)
        val chosen = chooseTrackedBlob(listOf(small, large), previousCentroid = null)
        assertEquals(large, chosen)
    }

    @Test
    fun `with a previous position, the nearest blob wins even if it's smaller`() {
        // This is the actual bug from real footage: a small nearby blob (the real marker,
        // moving a physically plausible distance) should beat a large but distant blob
        // (a stray pink object elsewhere in the room).
        val realMarker = Blob(centroidX = 12.0, centroidY = 10.0, size = 15)
        val strayObject = Blob(centroidX = 200.0, centroidY = 180.0, size = 60)
        val chosen = chooseTrackedBlob(listOf(strayObject, realMarker), previousCentroid = 10.0 to 10.0)
        assertEquals(realMarker, chosen)
    }

    @Test
    fun `no blobs returns null regardless of previous position`() {
        assertNull(chooseTrackedBlob(emptyList(), previousCentroid = 10.0 to 10.0))
    }
}
