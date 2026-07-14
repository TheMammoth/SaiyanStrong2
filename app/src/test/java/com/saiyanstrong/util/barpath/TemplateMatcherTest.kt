package com.saiyanstrong.util.barpath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateMatcherTest {

    /** A frameW×frameH gray image with a bright square patch centered at (px, py). */
    private fun imageWithPatch(frameW: Int, frameH: Int, px: Int, py: Int, patchSize: Int, bg: Int = 30, fg: Int = 220): IntArray {
        val img = IntArray(frameW * frameH) { bg }
        val half = patchSize / 2
        for (y in (py - half) until (py - half + patchSize)) {
            for (x in (px - half) until (px - half + patchSize)) {
                if (x in 0 until frameW && y in 0 until frameH) img[y * frameW + x] = fg
            }
        }
        return img
    }

    private fun patchOf(img: IntArray, w: Int, cx: Int, cy: Int, size: Int): IntArray {
        val half = size / 2
        val p = IntArray(size * size)
        var i = 0
        for (ty in 0 until size) for (tx in 0 until size) {
            p[i++] = img[(cy - half + ty) * w + (cx - half + tx)]
        }
        return p
    }

    @Test
    fun `finds the template at its exact location`() {
        val w = 40; val h = 40
        // A distinct 2D feature (bright square on dark bg) → the match location is unique. (A linear
        // gradient would NOT be: NCC ignores a constant offset, so a shifted gradient still scores 1.)
        val img = imageWithPatch(w, h, 20, 20, 8)
        val template = patchOf(img, w, 20, 20, 12) // includes the square's edges → has variance
        val match = TemplateMatcher.bestMatch(img, w, h, template, 12, 12, 20, 20, 4, 4)!!
        assertEquals(20, match.x)
        assertEquals(20, match.y)
        assertTrue("perfect match should score ~1", match.score > 0.99)
    }

    @Test
    fun `finds the template after it shifts within the search radius`() {
        val w = 60; val h = 60
        val template = imageWithPatch(30, 30, 15, 15, 6).let { patchOf(it, 30, 15, 15, 10) }
        // Real frame: the same bright square, now centered at (32, 28).
        val frame = imageWithPatch(w, h, 32, 28, 6)
        val match = TemplateMatcher.bestMatch(frame, w, h, template, 10, 10, 30, 30, 8, 8)!!
        assertEquals(32, match.x)
        assertEquals(28, match.y)
    }

    @Test
    fun `NCC is invariant to a brightness change`() {
        val w = 40; val h = 40
        val img = imageWithPatch(w, h, 20, 20, 8)
        val template = patchOf(img, w, 20, 20, 12)
        // Same structure/position, uniformly darker-contrast + brighter (a linear v -> 0.5v+100).
        val brighter = IntArray(w * h) { (img[it] / 2 + 100).coerceIn(0, 255) }
        val match = TemplateMatcher.bestMatch(brighter, w, h, template, 12, 12, 20, 20, 3, 3)!!
        assertEquals(20, match.x)
        assertEquals(20, match.y)
        assertTrue("a linear brightness change should still match strongly", match.score > 0.95)
    }

    @Test
    fun `a featureless (flat) template returns null`() {
        val w = 30; val h = 30
        val flat = IntArray(w * h) { 128 }
        val template = IntArray(64) { 128 } // 8x8 flat
        assertNull(TemplateMatcher.bestMatch(flat, w, h, template, 8, 8, 15, 15, 4, 4))
    }

    @Test
    fun `a template larger than the frame returns null`() {
        val frame = IntArray(4 * 4) { 10 }
        val template = IntArray(64) { 10 }
        assertNull(TemplateMatcher.bestMatch(frame, 4, 4, template, 8, 8, 2, 2, 1, 1))
    }

    @Test
    fun `matching structured template against pure noise-free wrong region scores low`() {
        val w = 50; val h = 50
        // Template: a sharp vertical edge.
        val template = IntArray(10 * 10) { idx -> if ((idx % 10) < 5) 20 else 230 }
        // Frame: a horizontal edge (very different structure) everywhere.
        val frame = IntArray(w * h) { idx -> if ((idx / w) < h / 2) 20 else 230 }
        val match = TemplateMatcher.bestMatch(frame, w, h, template, 10, 10, 25, 25, 6, 6)
        // It returns *a* best position, but the correlation should be weak — below a sane accept gate.
        assertTrue("a mismatched structure should score below the 0.4 accept threshold", (match?.score ?: 0.0) < 0.4)
    }
}
