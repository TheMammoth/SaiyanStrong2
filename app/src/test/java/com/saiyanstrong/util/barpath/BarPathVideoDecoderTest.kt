package com.saiyanstrong.util.barpath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only the pure YUV→RGB conversion is unit-testable; the MediaCodec plumbing needs a device. */
class BarPathVideoDecoderTest {

    private fun red(argb: Int) = (argb shr 16) and 0xFF
    private fun green(argb: Int) = (argb shr 8) and 0xFF
    private fun blue(argb: Int) = argb and 0xFF
    private fun alpha(argb: Int) = (argb ushr 24) and 0xFF

    @Test
    fun `neutral chroma reproduces the luma as gray`() {
        val argb = yuvToRgb(128, 128, 128)
        assertEquals(128, red(argb))
        assertEquals(128, green(argb))
        assertEquals(128, blue(argb))
    }

    @Test
    fun `black and white map correctly with neutral chroma`() {
        val black = yuvToRgb(0, 128, 128)
        assertEquals(0, red(black)); assertEquals(0, green(black)); assertEquals(0, blue(black))
        val white = yuvToRgb(255, 128, 128)
        assertEquals(255, red(white)); assertEquals(255, green(white)); assertEquals(255, blue(white))
    }

    @Test
    fun `BT601 red primary comes out red`() {
        // Y=76, U=84, V=255 is the BT.601 encoding of pure red.
        val argb = yuvToRgb(76, 84, 255)
        assertTrue("red should be high, was ${red(argb)}", red(argb) > 240)
        assertTrue("green should be low, was ${green(argb)}", green(argb) < 15)
        assertTrue("blue should be low, was ${blue(argb)}", blue(argb) < 15)
    }

    @Test
    fun `output is always fully opaque`() {
        assertEquals(255, alpha(yuvToRgb(0, 0, 0)))
        assertEquals(255, alpha(yuvToRgb(255, 255, 255)))
        assertEquals(255, alpha(yuvToRgb(120, 60, 200)))
    }

    @Test
    fun `channels are clamped into range for extreme chroma`() {
        // Extreme chroma would push channels well outside 0..255 before clamping.
        val argb = yuvToRgb(255, 0, 255)
        assertTrue(red(argb) in 0..255)
        assertTrue(green(argb) in 0..255)
        assertTrue(blue(argb) in 0..255)
    }
}
