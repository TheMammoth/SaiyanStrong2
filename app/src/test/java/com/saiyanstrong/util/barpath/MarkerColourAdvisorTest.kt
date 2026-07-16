package com.saiyanstrong.util.barpath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerColourAdvisorTest {

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun fill(n: Int, r: Int, g: Int, b: Int) = IntArray(n) { argb(r, g, b) }

    private val green = Triple(20, 200, 60)
    private val orange = Triple(240, 140, 20)
    private val blue = Triple(30, 60, 230)
    private val red = Triple(230, 20, 20)

    private fun histOf(vararg colors: Pair<Triple<Int, Int, Int>, Int>): IntArray {
        val pixels = ArrayList<Int>()
        for ((c, count) in colors) repeat(count) { pixels.add(argb(c.first, c.second, c.third)) }
        val arr = pixels.toIntArray()
        return MarkerColourAdvisor.buildHueHistogram(arr, arr.size, 1)
    }

    @Test
    fun `histogram ignores grey and dark pixels`() {
        val grey = MarkerColourAdvisor.buildHueHistogram(fill(100, 120, 120, 120), 100, 1)
        assertEquals("grey has no saturated pixels", 0, grey.sum())
        val dark = MarkerColourAdvisor.buildHueHistogram(fill(100, 0, 0, 0), 100, 1)
        assertEquals(0, dark.sum())
    }

    @Test
    fun `histogram counts a saturated colour`() {
        val hist = MarkerColourAdvisor.buildHueHistogram(fill(100, 30, 60, 230), 100, 1)
        assertTrue("blue pixels should be counted", hist.sum() > 0)
    }

    @Test
    fun `a green and orange scene recommends blue or purple and avoids green and orange`() {
        val hist = histOf(green to 500, orange to 500)
        val advice = MarkerColourAdvisor.recommend(hist)
        val recNames = advice.recommended.map { it.name }
        val avoidNames = advice.avoid.map { it.name }
        assertTrue("recommends a scene-absent colour, got $recNames",
            recNames.contains("Blue") || recNames.contains("Purple"))
        assertTrue("avoids green, got $avoidNames", avoidNames.contains("Green"))
        assertTrue("avoids orange, got $avoidNames", avoidNames.contains("Orange"))
        assertTrue("does not recommend green", !recNames.contains("Green"))
    }

    @Test
    fun `grade is BAD for a colour present in the scene and GOOD for one absent`() {
        val hist = histOf(green to 800)
        assertEquals(MarkerGrade.BAD, MarkerColourAdvisor.grade(hist, 120.0)) // green — crowded
        assertEquals(MarkerGrade.GOOD, MarkerColourAdvisor.grade(hist, 220.0)) // blue — absent
    }

    @Test
    fun `red near the hue wrap is graded and avoided correctly`() {
        val hist = histOf(red to 800)
        assertEquals(MarkerGrade.BAD, MarkerColourAdvisor.grade(hist, 2.0))
        assertTrue(MarkerColourAdvisor.recommend(hist).avoid.map { it.name }.contains("Red"))
    }

    @Test
    fun `an empty scene returns a safe default recommendation without crashing`() {
        val advice = MarkerColourAdvisor.recommend(IntArray(24))
        assertEquals(2, advice.recommended.size)
        assertEquals("Blue", advice.recommended[0].name)
        assertEquals("Purple", advice.recommended[1].name)
        assertTrue(advice.avoid.isEmpty())
    }
}
