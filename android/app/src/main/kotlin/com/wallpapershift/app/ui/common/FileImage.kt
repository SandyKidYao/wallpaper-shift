package com.wallpapershift.app.ui.common

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.wallpapershift.app.imaging.decodeSampled
import com.wallpapershift.app.imaging.frostedCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

/**
 * Shows an image file, decoded off the main thread and cached in memory. With [blurRadius] > 0 the
 * same frosting as the real home wallpaper is applied, so previews are faithful. While a new radius
 * renders, the previous bitmap stays on screen (no flicker while dragging the slider).
 */
@Composable
fun FileImage(
    file: File,
    modifier: Modifier = Modifier,
    blurRadius: Int = 0,
    decodeWidth: Int = 360,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val key = "${file.path}#$decodeWidth#$blurRadius"
    val bitmap by produceState<ImageBitmap?>(cache.get(key), key) {
        value = cache.get(key) ?: withContext(Dispatchers.Default) { load(file, decodeWidth, blurRadius) }?.also { cache.put(key, it) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        bitmap?.let { Image(it, contentDescription, Modifier.matchParentSize(), contentScale = contentScale) }
    }
}

private fun load(file: File, width: Int, blurRadius: Int): ImageBitmap? = try {
    var bitmap: Bitmap = decodeSampled(file, width)
    if (blurRadius > 0) bitmap = frostedCopy(bitmap, blurRadius, bitmap.width, bitmap.height)
    bitmap.asImageBitmap()
} catch (error: Exception) {
    Log.w("FileImage", "Cannot show ${file.path}", error)
    null
}
