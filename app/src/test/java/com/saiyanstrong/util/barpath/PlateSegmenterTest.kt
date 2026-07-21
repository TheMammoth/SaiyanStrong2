package com.saiyanstrong.util.barpath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateSegmenterTest {

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `flood-fills the connected same-colour region and stops at the boundary`() {
        val w = 20; val h = 20
        val gray = argb(128, 128, 128); val blue = argb(30, 60, 200)
        val px = IntArray(w * h) { gray }
        for (y in 7..12) for (x in 7..12) px[y * w + x] = blue // 6×6 blue square

        val sel = PlateSegmenter.segment(px, w, h, 9, 9, minPixels = 10)!!
        assertEquals("only the blue square, not the grey background", 36, sel.pixelCount)
        assertEquals(9.5, sel.centroidX, 0.5) // centre of 7..12
        assertEquals(9.5, sel.centroidY, 0.5)
        assertEquals(6.0, sel.diameterPx, 0.01)
    }

    @Test
    fun `a too-small region returns null`() {
        val w = 20; val h = 20
        val px = IntArray(w * h) { argb(128, 128, 128) }
        for (y in 5..6) for (x in 5..6) px[y * w + x] = argb(30, 60, 200) // 2×2 blue
        assertNull(PlateSegmenter.segment(px, w, h, 5, 5, minPixels = 60))
    }

    @Test
    fun `an out-of-bounds tap is clamped and does not crash`() {
        val w = 12; val h = 12
        val allBlue = IntArray(w * h) { argb(30, 60, 200) }
        val sel = PlateSegmenter.segment(allBlue, w, h, 100, 100, minPixels = 10)!!
        assertEquals(w * h, sel.pixelCount) // clamped into the grid, floods everything
    }

    @Test
    fun `the selection colour model matches the plate colour`() {
        val w = 20; val h = 20
        val blue = argb(30, 60, 200)
        val px = IntArray(w * h) { argb(128, 128, 128) }
        for (y in 5..14) for (x in 5..14) px[y * w + x] = blue
        val sel = PlateSegmenter.segment(px, w, h, 10, 10, minPixels = 10)!!
        assertTrue("blue matches its own model", sel.colorProfile.matches(30, 60, 200))
        assertTrue("skin does not", !sel.colorProfile.matches(210, 160, 120))
    }
}
