package com.saiyanstrong.util.barpath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VitBarTrackerSupportTest {

    @Test
    fun `init box is centred on the tap and sized to the shorter side`() {
        val box = VitBarTrackerSupport.initBoxFromTap(500.0, 900.0, 1080, 1920, boxFraction = 0.18)!!
        val expectedSide = (1080 * 0.18).toInt() // shorter side = width
        assertEquals(expectedSide, box.width)
        assertEquals(expectedSide, box.height)
        // Centre lands on the tap (within rounding of the integer top-left).
        assertEquals(500.0, box.centerX, expectedSide / 2.0)
        assertEquals(900.0, box.centerY, expectedSide / 2.0)
    }

    @Test
    fun `init box near the top-left corner is shifted fully inside the frame, not shrunk`() {
        val box = VitBarTrackerSupport.initBoxFromTap(2.0, 3.0, 1080, 1920)!!
        val expectedSide = (1080 * VitBarTrackerSupport.DEFAULT_BOX_FRACTION).toInt()
        assertEquals(expectedSide, box.width) // same size — shifted, not clipped
        assertEquals(0, box.x)
        assertEquals(0, box.y)
    }

    @Test
    fun `init box near the bottom-right corner stays fully inside the frame`() {
        val w = 1080; val h = 1920
        val box = VitBarTrackerSupport.initBoxFromTap((w - 1).toDouble(), (h - 1).toDouble(), w, h)!!
        assertTrue(box.x + box.width <= w)
        assertTrue(box.y + box.height <= h)
        assertEquals(w - box.width, box.x)
        assertEquals(h - box.height, box.y)
    }

    @Test
    fun `degenerate frame returns null`() {
        assertNull(VitBarTrackerSupport.initBoxFromTap(10.0, 10.0, 0, 100))
        assertNull(VitBarTrackerSupport.initBoxFromTap(10.0, 10.0, 100, 0))
    }

    @Test
    fun `initBox uses the explicit side and centres on the point`() {
        val box = VitBarTrackerSupport.initBox(500.0, 900.0, 200, 1080, 1920)!!
        assertEquals(200, box.width)
        assertEquals(200, box.height)
        assertEquals(400, box.x) // 500 - 100
        assertEquals(800, box.y) // 900 - 100
    }

    @Test
    fun `initBox floors a too-small side and caps at the shorter frame side`() {
        val tiny = VitBarTrackerSupport.initBox(500.0, 500.0, 4, 1080, 1920)!!
        assertEquals(VitBarTrackerSupport.MIN_BOX_SIDE, tiny.width)
        val huge = VitBarTrackerSupport.initBox(500.0, 900.0, 5000, 1080, 1920)!!
        assertEquals(1080, huge.width) // capped at shorter side (width)
    }

    @Test
    fun `initBox near an edge is shifted fully inside, not shrunk`() {
        val box = VitBarTrackerSupport.initBox(5.0, 5.0, 200, 1080, 1920)!!
        assertEquals(200, box.width) // unchanged size
        assertEquals(0, box.x)
        assertEquals(0, box.y)
    }

    @Test
    fun `initBox returns null for a degenerate frame`() {
        assertNull(VitBarTrackerSupport.initBox(10.0, 10.0, 100, 0, 100))
    }

    @Test
    fun `box centre is the geometric centre`() {
        val (cx, cy) = VitBarTrackerSupport.boxCenter(100, 200, 40, 60)
        assertEquals(120.0, cx, 1e-9)
        assertEquals(230.0, cy, 1e-9)
    }

    @Test
    fun `plate scale is box width over the plate diameter in metres`() {
        // A 90px-wide box on a standard 0.45m plate → 200 px/m.
        assertEquals(200.0, VitBarTrackerSupport.plateScalePpm(90)!!, 1e-9)
        assertEquals(
            100.0,
            VitBarTrackerSupport.plateScalePpm(50, plateDiameterM = 0.5)!!,
            1e-9
        )
    }

    @Test
    fun `plate scale is null for a non-positive box width`() {
        assertNull(VitBarTrackerSupport.plateScalePpm(0))
        assertNull(VitBarTrackerSupport.plateScalePpm(-10))
    }

    @Test
    fun `a valid tap yields a non-null box`() {
        assertNotNull(VitBarTrackerSupport.initBoxFromTap(540.0, 960.0, 1080, 1920))
    }
}
