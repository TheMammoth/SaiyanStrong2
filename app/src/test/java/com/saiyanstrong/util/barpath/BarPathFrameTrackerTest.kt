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

private fun uniformWeights(mask: BooleanArray): DoubleArray = DoubleArray(mask.size) { 1.0 }

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
        val blobs = findBlobs(mask, uniformWeights(mask), 10, 5)
        assertEquals(2, blobs.size)
    }

    @Test
    fun `a single contiguous blob with uniform weight reports the plain centroid, size, and diameter`() {
        val mask = grid(4, listOf("##", "##").map { it.padEnd(4, '.') })
        val blobs = findBlobs(mask, uniformWeights(mask), 4, 2)
        assertEquals(1, blobs.size)
        assertEquals(4, blobs.single().size)
        assertEquals(0.5, blobs.single().centroidX, 0.0001)
        assertEquals(0.5, blobs.single().centroidY, 0.0001)
        assertEquals(2.0, blobs.single().diameterPx, 0.0001) // 2x2 bounding box
    }

    @Test
    fun `blob diameter is the larger of bounding-box width and height`() {
        // A 5-wide, 2-tall blob -- diameter should be 5 (the larger dimension), not 2.
        val mask = grid(5, listOf("#####", "#####"))
        val blob = findBlobs(mask, uniformWeights(mask), 5, 2).single()
        assertEquals(5.0, blob.diameterPx, 0.0001)
    }

    @Test
    fun `no matches produce no blobs`() {
        val mask = BooleanArray(100)
        assertTrue(findBlobs(mask, uniformWeights(mask), 10, 10).isEmpty())
    }

    @Test
    fun `a heavily-weighted pixel pulls the centroid toward itself`() {
        // Two matching pixels side by side, x=0 and x=1 — uniform weight would centroid at 0.5.
        val mask = grid(2, listOf("##"))
        val weights = doubleArrayOf(0.01, 10.0) // pixel at x=1 scores far better than x=0
        val blob = findBlobs(mask, weights, 2, 1).single()
        assertTrue("expected centroid pulled toward x=1, got ${blob.centroidX}", blob.centroidX > 0.9)
    }

    @Test
    fun `a blob where every pixel has zero weight falls back to the unweighted centroid`() {
        val mask = grid(2, listOf("##"))
        val weights = doubleArrayOf(0.0, 0.0)
        val blob = findBlobs(mask, weights, 2, 1).single()
        assertEquals(0.5, blob.centroidX, 0.0001) // unweighted fallback: (0+1)/2
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

    // ── colour-anchored anti-drift guard ───────────────────────────────────────────────

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun filled(w: Int, h: Int, color: Int) = IntArray(w * h) { color }

    @Test
    fun `dominant colour of a saturated blue region is bluish`() {
        val w = 10; val h = 10
        val dom = regionDominantColor(filled(w, h, rgb(30, 60, 200)), w, h, 0, 0, w, h)!!
        assertTrue("blue channel should dominate, got ${dom.toList()}", dom[2] > dom[0] && dom[2] > dom[1])
    }

    @Test
    fun `a grey region has no colour anchor`() {
        val w = 10; val h = 10
        assertNull(regionDominantColor(filled(w, h, rgb(128, 128, 128)), w, h, 0, 0, w, h))
        // A near-white region (chrome hub / window) likewise gives no anchor.
        assertNull(regionDominantColor(filled(w, h, rgb(235, 235, 240)), w, h, 0, 0, w, h))
    }

    @Test
    fun `colour fraction is high on the anchor colour and low off it`() {
        val w = 10; val h = 10
        val profile = MarkerColorProfile.sample(30, 60, 200) // blue plate
        assertTrue(regionColorFraction(filled(w, h, rgb(30, 60, 200)), w, h, 0, 0, w, h, profile) > 0.8)
        // Skin tone (drift onto the lifter) should barely match blue.
        assertTrue(regionColorFraction(filled(w, h, rgb(210, 160, 120)), w, h, 0, 0, w, h, profile) < 0.2)
    }

    // ── choosePlateBlob (drift-free re-detection) ───────────────────────────────────────

    @Test
    fun `choosePlateBlob picks the in-band blob nearest the previous position`() {
        val near = Blob(12.0, 12.0, 100, 20.0)
        val far = Blob(50.0, 50.0, 100, 20.0)
        assertEquals(near, choosePlateBlob(listOf(far, near), 10.0 to 10.0, expectedDiameter = 20.0))
    }

    @Test
    fun `choosePlateBlob rejects a size-mismatched distractor even if it is nearer`() {
        // Band for expected diameter 20 is 8..50; a 4px speck right on the previous point is excluded.
        val nearTiny = Blob(10.0, 10.0, 8, 4.0)
        val farRight = Blob(40.0, 40.0, 100, 20.0)
        assertEquals(farRight, choosePlateBlob(listOf(nearTiny, farRight), 10.0 to 10.0, 20.0))
    }

    @Test
    fun `choosePlateBlob returns null for no blobs`() {
        assertNull(choosePlateBlob(emptyList(), 10.0 to 10.0, 20.0))
    }
}
