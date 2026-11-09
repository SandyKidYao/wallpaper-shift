package com.wallpapershift.app.imaging

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BoxBlurTest {
    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun red(p: Int) = (p shr 16) and 0xFF

    @Test fun `radius zero leaves pixels untouched`() {
        val pixels = IntArray(12) { argb(255, it * 20, 0, 0) }
        val copy = pixels.copyOf()
        BoxBlur.blur(pixels, 4, 3, 0)
        assertArrayEquals(copy, pixels)
    }

    @Test fun `a flat colour stays exactly the same`() {
        val colour = argb(255, 12, 200, 99)
        val pixels = IntArray(50 * 30) { colour }
        BoxBlur.blur(pixels, 50, 30, 7)
        assertTrue(pixels.all { it == colour })
    }

    @Test fun `a hard edge becomes a monotonic ramp`() {
        val width = 40
        val pixels = IntArray(width * 3) { i -> if (i % width < width / 2) argb(255, 0, 0, 0) else argb(255, 255, 0, 0) }
        BoxBlur.blur(pixels, width, 3, 4)
        val row = (0 until width).map { red(pixels[width + it]) }
        assertEquals(0, row.first())
        assertEquals(255, row.last())
        assertTrue("ramp must not decrease: $row", row.zipWithNext().all { (a, b) -> b >= a })
        assertTrue("edge must be softened: $row", row[width / 2 - 1] in 1..254 && row[width / 2] in 1..254)
    }

    @Test fun `blurring preserves average brightness and keeps images opaque`() {
        val w = 64
        val h = 48
        val pixels = IntArray(w * h) { i -> argb(255, (i * 37) % 256, (i * 11) % 256, (i * 101) % 256) }
        val before = pixels.map(::red).average()
        BoxBlur.blur(pixels, w, h, 5)
        assertTrue(abs(before - pixels.map(::red).average()) < 3.0)
        assertTrue(pixels.all { it ushr 24 == 255 })
    }

    @Test fun `radius larger than the image is handled`() {
        val pixels = IntArray(3 * 2) { argb(255, it * 40, 0, 0) }
        BoxBlur.blur(pixels, 3, 2, 50)
        assertTrue(pixels.all { red(it) in 0..255 })
    }
}
