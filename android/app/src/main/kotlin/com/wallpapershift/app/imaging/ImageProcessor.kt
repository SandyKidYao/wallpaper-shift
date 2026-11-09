package com.wallpapershift.app.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.graphics.scale
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Bitmap work: decoding imports, thumbnails, and the frosted home-screen variant. Call off the main thread. */
class ImageProcessor(private val context: Context) {

    /** Physical screen size in portrait orientation: (short side, long side) in px. */
    fun screenSize(): Pair<Int, Int> {
        val windows = context.getSystemService(WindowManager::class.java)
        val (w, h) = if (Build.VERSION.SDK_INT >= 30) {
            windows.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            @Suppress("DEPRECATION")
            DisplayMetrics().also { windows.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
        }
        return min(w, h) to max(w, h)
    }

    /**
     * Decodes [source] (EXIF orientation applied) and writes it to [dest] as the lock-screen image.
     * The picture is shrunk only as far as it still covers the screen, so nothing is upscaled and huge
     * photos don't cost hundreds of MB at switch time.
     */
    fun importImage(source: ImageDecoder.Source, dest: File) {
        val (screenW, screenH) = screenSize()
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            var scale = max(screenW.toFloat() / w, screenH.toFloat() / h)
            scale = min(scale, sqrt(MAX_PIXELS.toFloat() / (w.toFloat() * h)))
            if (scale < 1f) decoder.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
        }
        try {
            writeJpeg(bitmap, dest, LOCK_QUALITY)
        } finally {
            bitmap.recycle()
        }
    }

    fun writeThumbnail(src: File, dest: File) {
        val bitmap = decodeSampled(src, THUMB_WIDTH)
        val thumb = scaleToWidth(bitmap, THUMB_WIDTH)
        try {
            writeJpeg(thumb, dest, THUMB_QUALITY)
        } finally {
            if (thumb !== bitmap) thumb.recycle()
            bitmap.recycle()
        }
    }

    fun writeBlurred(src: File, dest: File, radius: Int) {
        val sharp = BitmapFactory.decodeFile(src.path) ?: error("Cannot decode ${src.path}")
        val blurred = frostedCopy(sharp, radius, sharp.width, sharp.height)
        try {
            writeJpeg(blurred, dest, HOME_QUALITY)
        } finally {
            blurred.recycle()
            sharp.recycle()
        }
    }

    private fun scaleToWidth(bitmap: Bitmap, width: Int): Bitmap {
        if (bitmap.width <= width) return bitmap
        val height = max(1, (bitmap.height * width / bitmap.width.toFloat()).roundToInt())
        return bitmap.scale(width, height)
    }

    /** Writes via a temp file + rename so readers (the alarm receiver) never see a partial JPEG. */
    private fun writeJpeg(bitmap: Bitmap, dest: File, quality: Int) {
        dest.parentFile?.mkdirs()
        val temp = File(dest.parentFile, dest.name + ".tmp")
        temp.outputStream().buffered().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "JPEG encode failed" }
        }
        if (!temp.renameTo(dest)) {
            dest.delete()
            check(temp.renameTo(dest)) { "Could not write ${dest.name}" }
        }
    }

    companion object {
        const val THUMB_WIDTH = 360
        private const val MAX_PIXELS = 16_000_000
        private const val LOCK_QUALITY = 95
        private const val HOME_QUALITY = 90
        private const val THUMB_QUALITY = 85
    }
}
