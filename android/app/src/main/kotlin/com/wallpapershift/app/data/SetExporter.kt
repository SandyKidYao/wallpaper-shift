package com.wallpapershift.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Saves a set as a set package ([SetPackage]), so it can be backed up or moved to another phone. */
class SetExporter(private val context: Context, private val images: ImageStore) {

    /** A saved package: where it is, and its file name as saved (it may have gained a " (1)"). */
    data class Saved(val uri: Uri, val name: String)

    /**
     * Saves [set] as `Download/壁纸时移/<name>.zip` and returns the file, which can be shared.
     * Android 10+ lets an app add downloads without any permission. An existing file of the same name
     * is kept; MediaStore names the new one "<name> (1).zip".
     */
    @RequiresApi(29)
    suspend fun exportToDownloads(set: WallpaperSet): Saved = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName(set))
            put(MediaStore.Downloads.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/${DemoSet.ALBUM}/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: throw IOException("无法创建文件")
        try {
            write(uri, set, mode = "w")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
        val name = resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
        Saved(uri, name ?: fileName(set))
    }

    /** Writes [set] to a document the user chose in 「保存到…」 (Android 9, where downloads need a permission). */
    suspend fun exportTo(uri: Uri, set: WallpaperSet) = withContext(Dispatchers.IO) { write(uri, set, mode = "wt") }

    private fun write(uri: Uri, set: WallpaperSet, mode: String) {
        val out = context.contentResolver.openOutputStream(uri, mode) ?: throw IOException("无法写入文件")
        SetPackage.write(out, set) { item -> images.lockFile(item.id) }
    }

    companion object {
        const val MIME_TYPE = "application/zip"
        /** Where [exportToDownloads] puts the file, as the user sees it. */
        const val PLACE = "下载/壁纸时移"

        fun fileName(set: WallpaperSet) = "${SetPackage.safeFileName(set.name).ifEmpty { "壁纸集" }}.zip"
    }
}
