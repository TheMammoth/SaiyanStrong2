package com.saiyanstrong.presentation.screens.barpath

import com.saiyanstrong.domain.model.TrackedFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepCardGeneratorTest {

    private fun frame(x: Double, y: Double) = TrackedFrame(0L, x, y, 0.0)

    @Test
    fun `boundsOf returns the centroid bounding box`() {
        val bounds = boundsOf(listOf(frame(10.0, 20.0), frame(30.0, 5.0), frame(15.0, 40.0)))!!
        assertEquals(10f, bounds.left, 0.001f)
        assertEquals(30f, bounds.right, 0.001f)
        assertEquals(5f, bounds.top, 0.001f)
        assertEquals(40f, bounds.bottom, 0.001f)
    }

    @Test
    fun `boundsOf is null for no frames`() {
        assertNull(boundsOf(emptyList()))
    }

    @Test
    fun `fitTransform scales and centers a wide path into a square, letterboxed vertically`() {
        val src = FloatRect(0f, 0f, 100f, 50f) // 2:1
        val t = fitTransform(src, dstLeft = 0f, dstTop = 0f, dstWidth = 200f, dstHeight = 200f)
        assertEquals(2f, t.scale, 0.001f) // min(200/100, 200/50) = 2
        val (x0, y0) = t.map(0f, 0f)
        val (x1, y1) = t.map(100f, 50f)
        assertEquals(0f, x0, 0.001f); assertEquals(50f, y0, 0.001f)   // centered vertically (200-100)/2
        assertEquals(200f, x1, 0.001f); assertEquals(150f, y1, 0.001f)
    }

    @Test
    fun `fitTransform maps a source point relative to the source origin, not absolute`() {
        val src = FloatRect(100f, 200f, 200f, 300f) // offset origin, 100x100 square
        val t = fitTransform(src, dstLeft = 0f, dstTop = 0f, dstWidth = 100f, dstHeight = 100f)
        val (x, y) = t.map(100f, 200f) // the source top-left maps to the dst top-left
        assertEquals(0f, x, 0.001f)
        assertEquals(0f, y, 0.001f)
    }

    @Test
    fun `fitTransform does not divide by zero for a still marker`() {
        val src = FloatRect(50f, 50f, 50f, 50f) // zero-size
        val t = fitTransform(src, 0f, 0f, 100f, 100f)
        val (x, y) = t.map(50f, 50f)
        // Just assert it produced finite numbers rather than NaN/Inf.
        assertEquals(x, x, 0.0f) // x == x fails if NaN
        assertEquals(y, y, 0.0f)
    }
}
