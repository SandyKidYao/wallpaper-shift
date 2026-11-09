package com.wallpapershift.app.data

import android.graphics.ImageDecoder
import android.util.Log
import com.wallpapershift.app.schedule.DaySchedule
import com.wallpapershift.app.schedule.MINUTES_PER_DAY
import com.wallpapershift.app.schedule.WallpaperSwitcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** A picture waiting to be imported, with the name to show for it. */
class ImportSource(val label: String, val open: () -> ImageDecoder.Source)

/**
 * Everything the UI can do to sets. Any change that touches the active set re-syncs the wallpaper,
 * so callers never have to remember to reschedule.
 */
class WallpaperLibrary(
    private val repository: SetRepository,
    private val images: ImageStore,
    private val switcher: WallpaperSwitcher,
    /** Outlives screens: follow-up work like re-rendering blur must not die with a ViewModel. */
    private val appScope: CoroutineScope,
) {
    val state: StateFlow<AppState> = repository.state

    suspend fun createSet(name: String, blurRadius: Int = WallpaperSet.DEFAULT_BLUR): String {
        val id = UUID.randomUUID().toString()
        repository.update { it.copy(sets = it.sets + WallpaperSet(id = id, name = name, blurRadius = blurRadius)) }
        return id
    }

    /**
     * Creates a new, inactive set from an unpacked [SetPackage]. Returns the set's id and how many
     * pictures could not be decoded; if none could, the empty set is removed again and the id is null.
     */
    suspend fun importSet(content: SetPackageContent, onProgress: (done: Int) -> Unit = {}): Pair<String?, Int> {
        val setId = createSet(content.name, content.blur)
        val sources = content.items.map { item -> ImportSource(item.label) { ImageDecoder.createSource(item.file) } }
        val failed = addImages(
            setId, sources, SetPackage.planStarts(content.items.map { it.startMinute }), onProgress,
            anchors = content.items.map { it.anchor },
        )
        if (failed == sources.size) {
            deleteSet(setId)
            return null to failed
        }
        return setId to failed
    }

    /** Names don't affect the schedule, so this skips the re-sync that [updateSet] does. */
    suspend fun rename(setId: String, name: String) {
        repository.update { s -> s.copy(sets = s.sets.map { if (it.id == setId) it.copy(name = name) else it }) }
    }

    suspend fun deleteSet(setId: String) {
        val set = state.value.set(setId) ?: return
        repository.update { s -> s.copy(sets = s.sets.filterNot { it.id == setId }) }
        images.delete(set.items.map { it.id })
        switcher.sync()
    }

    /** Makes [setId] drive the wallpaper (or turns switching off for null). False if the set has no pictures. */
    suspend fun activate(setId: String?): Boolean {
        if (setId != null && state.value.set(setId)?.items.isNullOrEmpty()) return false
        repository.update { it.copy(activeSetId = setId) }
        switcher.sync(releaseHold = true)
        return true
    }

    suspend fun setBlur(setId: String, radius: Int) {
        updateSet(setId) { it.copy(blurRadius = radius.coerceIn(0, WallpaperSet.MAX_BLUR)) }
        // Render every picture now so later alarm-time switches only copy files.
        appScope.launch {
            val set = state.value.set(setId) ?: return@launch
            set.items.forEach { item ->
                runCatching { images.homeFile(item.id, set.blurRadius) }
                    .onFailure { Log.w(TAG, "Pre-render failed for ${item.id}", it) }
            }
        }
    }

    /** Returns false if another picture in the set already starts at [minute]. */
    suspend fun setStart(setId: String, itemId: String, minute: Int): Boolean {
        val set = state.value.set(setId) ?: return false
        if (set.items.any { it.id != itemId && it.startMinute == minute }) return false
        // A time picked by hand replaces any theme anchor.
        updateSet(setId) { s ->
            s.copy(items = s.items.map { if (it.id == itemId) it.copy(startMinute = minute, anchor = ThemeAnchor.NONE) else it })
        }
        return true
    }

    /** Starts [itemId] when the phone's theme flips; its fixed start stays as the fallback. */
    suspend fun setAnchor(setId: String, itemId: String, anchor: ThemeAnchor) {
        updateSet(setId) { s -> s.copy(items = s.items.map { if (it.id == itemId) it.copy(anchor = anchor) else it }) }
    }

    suspend fun deleteItem(setId: String, itemId: String) {
        updateSet(setId) { s -> s.copy(items = s.items.filterNot { it.id == itemId }) }
        images.delete(listOf(itemId))
        if (state.value.activeSet?.items?.isEmpty() == true) activate(null)
    }

    /**
     * Imports pictures one by one, adding each to the set as soon as it is ready so the list fills in
     * progressively. [starts] pins start minutes (demo data); otherwise they are spread automatically.
     * Returns the number of pictures that could not be decoded.
     */
    suspend fun addImages(
        setId: String,
        sources: List<ImportSource>,
        starts: List<Int>? = null,
        onProgress: (done: Int) -> Unit = {},
        anchors: List<ThemeAnchor>? = null,
    ): Int {
        val set = state.value.set(setId) ?: return sources.size
        val planned = starts ?: DaySchedule.suggestStarts(set.items.map { it.startMinute }, sources.size)
        var failed = 0
        sources.forEachIndexed { index, source ->
            try {
                val itemId = images.import(source.open())
                images.homeFile(itemId, state.value.set(setId)?.blurRadius ?: set.blurRadius)
                updateSet(setId) { s ->
                    val taken = s.items.map { it.startMinute }.toSet()
                    var start = planned[index]
                    while (start in taken) start = (start + 1) % MINUTES_PER_DAY
                    val label = source.label.ifBlank { "图片 ${set.items.size + index + 1}" }
                    s.copy(items = s.items + WallpaperItem(id = itemId, startMinute = start, label = label, anchor = anchors?.get(index) ?: ThemeAnchor.NONE))
                }
            } catch (error: Exception) {
                Log.w(TAG, "Import failed: ${source.label}", error)
                failed++
            }
            onProgress(index + 1)
        }
        return failed
    }

    private suspend fun updateSet(setId: String, transform: (WallpaperSet) -> WallpaperSet) {
        val updated = repository.update { s -> s.copy(sets = s.sets.map { if (it.id == setId) transform(it) else it }) }
        if (updated.activeSetId == setId) switcher.sync(releaseHold = true)
    }

    private companion object {
        const val TAG = "WallpaperLibrary"
    }
}
