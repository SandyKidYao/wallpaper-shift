package com.wallpapershift.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.edit
import com.wallpapershift.app.schedule.DaySchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The bundled "one day" sample: the app logo on six gradients, from dawn to deep night. The pictures ship
 * in every build (assets/demo, from the repo's shared/demo-assets). File names end in the time of day
 * ("sky-01-dawn.jpg"), which picks the picture's start time.
 */
class DemoSet(private val context: Context, private val library: WallpaperLibrary) {
    private val assets = context.assets
    private val prefs = context.getSharedPreferences("demo", Context.MODE_PRIVATE)

    val count: Int get() = files().size

    /** True until [setUpOnFirstLaunch] has completed on this install. */
    val needsFirstLaunchSetup: Boolean get() = !prefs.getBoolean(KEY_SET_UP, false) && count > 0

    /**
     * Runs once per install. Creates the demo set, unless the user already has sets, and leaves it
     * off: the user decides when their wallpaper starts changing. Also copies the pictures into the photo
     * library, so after deleting the set the user can still find them in the gallery and add them back.
     */
    suspend fun setUpOnFirstLaunch(onProgress: (Int) -> Unit = {}) {
        if (!needsFirstLaunchSetup) return
        if (library.state.value.sets.isEmpty()) install(activate = false, onProgress = onProgress)
        // Marked done only once the gallery copy worked, so a failure (e.g. full storage) retries next launch.
        if (withContext(Dispatchers.IO) { copyToPhotoLibrary() }) prefs.edit { putBoolean(KEY_SET_UP, true) }
    }

    /**
     * Creates the demo set and (by default) activates it; returns its id. [starts] overrides the natural
     * times — the debug receiver uses it to compress a whole day into a few minutes. [copies] repeats
     * the pictures (spread evenly over the day) to test sets larger than the demo.
     */
    suspend fun install(
        starts: List<Int>? = null,
        copies: Int = 1,
        activate: Boolean = true,
        name: String = NAME,
        onProgress: (Int) -> Unit = {},
    ): String {
        val files = List(copies.coerceAtLeast(1)) { files() }.flatten()
        val setId = library.createSet(name)
        val sources = files.map { file -> ImportSource(file.substringBeforeLast('.')) { ImageDecoder.createSource(assets, "$DIR/$file") } }
        val natural = files.map { NATURAL_STARTS[it.substringBeforeLast('.').substringAfterLast('-')] }
        val planned = starts ?: natural.takeIf { list -> copies <= 1 && list.all { it != null } }?.filterNotNull()
            ?: DaySchedule.suggestStarts(emptyList(), files.size)
        // Only the natural day follows the system theme; compressed or repeated test sets keep fixed times.
        val anchors = if (starts == null && copies <= 1) files.map { THEME_ANCHORS[it.substringBeforeLast('.').substringAfterLast('-')] ?: ThemeAnchor.NONE } else null
        library.addImages(setId, sources, planned, onProgress, anchors)
        if (activate) library.activate(setId)
        return setId
    }

    /**
     * Adds the demo pictures to Pictures/壁纸时移 in the shared photo library. Android 10+ lets an app add
     * its own media without any permission; on Android 9 that would need storage access, so it is
     * skipped there. Pictures this app already added (still visible to it) are not added twice.
     * Returns false if any copy failed.
     */
    private fun copyToPhotoLibrary(): Boolean {
        if (Build.VERSION.SDK_INT < 29) return true
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val folder = "${Environment.DIRECTORY_PICTURES}/$ALBUM/"
        var ok = true
        for (file in files()) {
            val exists = resolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND ${MediaStore.Images.Media.DISPLAY_NAME} = ?",
                arrayOf(folder, file),
                null,
            )?.use { it.count > 0 } ?: false
            if (exists) continue
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file)
                put(MediaStore.Images.Media.MIME_TYPE, if (file.endsWith(".png")) "image/png" else "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, folder)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values)
            if (uri == null) {
                ok = false
                continue
            }
            try {
                resolver.openOutputStream(uri)!!.use { out -> assets.open("$DIR/$file").use { it.copyTo(out) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                // After a reinstall the previous install's copies are still in the album but invisible to
                // this one, so the query above misses them. MediaStore then publishes ours under a new name
                // ("01-dawn (1).jpg"); seeing that, keep the existing picture and drop the duplicate.
                val published = resolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
                if (published != null && published != file) resolver.delete(uri, null, null)
            } catch (error: Exception) {
                Log.w(TAG, "Could not add $file to the photo library", error)
                resolver.delete(uri, null, null)
                ok = false
            }
        }
        return ok
    }

    private fun files(): List<String> = assets.list(DIR)?.filter { it.endsWith(".jpg") || it.endsWith(".png") }?.sorted().orEmpty()

    companion object {
        const val NAME = "一日光影（示例）"
        /** Album (folder under Pictures) the demo pictures are copied to. */
        const val ALBUM = "壁纸时移"
        private const val DIR = "demo"
        private const val TAG = "DemoSet"
        private const val KEY_SET_UP = "firstLaunchSetUp"
        /** Morning starts when the phone turns light, night when it turns dark; the natural times are the fallback. */
        private val THEME_ANCHORS = mapOf("morning" to ThemeAnchor.LIGHT_START, "night" to ThemeAnchor.DARK_START)
        private val NATURAL_STARTS = mapOf(
            "dawn" to 4 * 60,
            "morning" to 6 * 60,
            "noon" to 11 * 60,
            "afternoon" to 14 * 60,
            "night" to 18 * 60,
            "deepnight" to 23 * 60,
        )
    }
}
