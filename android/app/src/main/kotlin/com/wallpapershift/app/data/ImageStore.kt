package com.wallpapershift.app.data

import android.graphics.ImageDecoder
import com.wallpapershift.app.imaging.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Owns the pixels behind each [WallpaperItem]. Imports are copied into private storage because
 * photo-picker grants are temporary and the alarm receiver must be able to read them days later.
 *
 * Layout per item, under `files/images/<itemId>/`:
 *  - `lock.jpg`      sharp, screen-covering image (lock screen)
 *  - `thumb.jpg`     small preview for the UI
 *  - `home_bN.jpg`   blurred variant for blur radius N (home screen), generated on demand
 */
class ImageStore(private val root: File, private val processor: ImageProcessor) {

    fun lockFile(itemId: String) = File(dir(itemId), "lock.jpg")
    fun thumbFile(itemId: String) = File(dir(itemId), "thumb.jpg")

    /** Imports one picture and returns its new item id. */
    suspend fun import(source: ImageDecoder.Source): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        try {
            processor.importImage(source, lockFile(id))
            processor.writeThumbnail(lockFile(id), thumbFile(id))
        } catch (error: Throwable) {
            dir(id).deleteRecursively()
            throw error
        }
        id
    }

    /** The home-screen image for [radius], rendering and caching it if needed. Radius 0 means "no blur". */
    suspend fun homeFile(itemId: String, radius: Int): File = withContext(Dispatchers.IO) {
        if (radius <= 0) return@withContext lockFile(itemId)
        val file = File(dir(itemId), "home_b$radius.jpg")
        if (!file.exists()) {
            processor.writeBlurred(lockFile(itemId), file, radius)
            dir(itemId).listFiles { f -> f.name.startsWith("home_b") && f != file }?.forEach { it.delete() }
        }
        file
    }

    suspend fun delete(itemIds: Collection<String>) = withContext(Dispatchers.IO) {
        itemIds.forEach { dir(it).deleteRecursively() }
    }

    private fun dir(itemId: String) = File(root, itemId)
}
