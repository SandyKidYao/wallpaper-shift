package com.wallpapershift.app.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Width the blur is computed at. Fixing it makes a radius mean the same thing on a 360px thumbnail
 * preview and on the full-size wallpaper, so the editor preview matches what lands on the home screen.
 */
private const val BLUR_WORK_WIDTH = 240

/** A blurred copy of [source] scaled to [outWidth]x[outHeight]. [source] is left untouched. */
fun frostedCopy(source: Bitmap, radius: Int, outWidth: Int, outHeight: Int): Bitmap {
    val workW = min(BLUR_WORK_WIDTH, source.width)
    val workH = max(1, (source.height * workW / source.width.toFloat()).roundToInt())
    val small = source.scale(workW, workH)
    val pixels = IntArray(workW * workH)
    small.getPixels(pixels, 0, workW, 0, 0, workW, workH)
    if (small !== source) small.recycle()
    BoxBlur.blur(pixels, workW, workH, radius)
    val blurredSmall = Bitmap.createBitmap(pixels, workW, workH, Bitmap.Config.ARGB_8888)
    if (workW == outWidth && workH == outHeight) return blurredSmall
    return blurredSmall.scale(outWidth, outHeight).also { blurredSmall.recycle() }
}

/** Decodes [file] with a power-of-two sample size that keeps its width >= [minWidth]. */
fun decodeSampled(file: File, minWidth: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= minWidth) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("Cannot decode ${file.path}")
}
