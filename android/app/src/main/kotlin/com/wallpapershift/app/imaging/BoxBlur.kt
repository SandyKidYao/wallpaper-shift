package com.wallpapershift.app.imaging

/**
 * Separable box blur over packed ARGB pixels. Three passes approximate a Gaussian closely enough for
 * a frosted-glass wallpaper, and the running-sum window makes cost independent of the radius.
 * Pure Kotlin (no Bitmap) so it runs in JVM unit tests.
 */
object BoxBlur {

    fun blur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int = 3) {
        require(pixels.size == width * height) { "pixels.size != width * height" }
        if (radius <= 0 || width == 0 || height == 0) return
        val scratch = IntArray(pixels.size)
        repeat(passes) {
            pass(pixels, scratch, lines = height, length = width, lineStride = width, step = 1, radius)
            pass(scratch, pixels, lines = width, length = height, lineStride = 1, step = width, radius)
        }
    }

    /** Blurs every line of [src] into [dst]. A line starts at `line * lineStride`; samples are [step] apart. */
    private fun pass(src: IntArray, dst: IntArray, lines: Int, length: Int, lineStride: Int, step: Int, radius: Int) {
        val window = 2 * radius + 1
        val half = window / 2
        val last = length - 1
        for (line in 0 until lines) {
            val base = line * lineStride
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val p = src[base + i.coerceIn(0, last) * step]
                a += p ushr 24
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
            }
            for (i in 0 until length) {
                dst[base + i * step] = ((a + half) / window shl 24) or
                    ((r + half) / window shl 16) or
                    ((g + half) / window shl 8) or
                    ((b + half) / window)
                val leaving = src[base + (i - radius).coerceAtLeast(0) * step]
                val entering = src[base + (i + radius + 1).coerceAtMost(last) * step]
                a += (entering ushr 24) - (leaving ushr 24)
                r += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
                g += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
                b += (entering and 0xFF) - (leaving and 0xFF)
            }
        }
    }
}
