package com.wallpapershift.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Imports a set package (a zip the user picked) as a new, inactive set. */
class SetImporter(private val context: Context, private val library: WallpaperLibrary) {

    data class Result(val setId: String, val name: String, val failed: Int, val overLimit: Int)

    /**
     * Unpacks into a private cache folder, imports, then deletes the folder.
     * [onProgress] gets the set's name and the pictures done so far, out of the total.
     *
     * @throws SetPackageException with a message for the user.
     */
    suspend fun import(uri: Uri, onProgress: (name: String, done: Int, total: Int) -> Unit = { _, _, _ -> }): Result {
        val work = File(context.cacheDir, "import-${UUID.randomUUID()}")
        try {
            val content = withContext(Dispatchers.IO) {
                SetPackage.read(
                    open = { context.contentResolver.openInputStream(uri) ?: throw SetPackageException(SetPackage.UNREADABLE) },
                    fallbackName = displayName(uri),
                    workDir = work,
                )
            }
            val total = content.items.size
            onProgress(content.name, 0, total)
            val (setId, failed) = library.importSet(content) { done -> onProgress(content.name, done, total) }
            if (setId == null) throw SetPackageException("图片都无法读取，没有导入")
            return Result(setId, content.name, failed + content.missing, content.overLimit)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { work.deleteRecursively() }
        }
    }

    /** The file name without extension ("菲伦.zip" → "菲伦"), or "" when the provider doesn't say. */
    private fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.substringBeforeLast('.').orEmpty()
}
