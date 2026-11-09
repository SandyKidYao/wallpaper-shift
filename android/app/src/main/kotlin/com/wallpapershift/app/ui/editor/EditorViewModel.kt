package com.wallpapershift.app.ui.editor

import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wallpapershift.app.AppContainer
import com.wallpapershift.app.data.ImportSource
import com.wallpapershift.app.data.SetExporter
import com.wallpapershift.app.data.ThemeAnchor
import com.wallpapershift.app.data.WallpaperSet
import com.wallpapershift.app.schedule.DaySchedule
import com.wallpapershift.app.schedule.ThemeTimes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportProgress(val done: Int, val total: Int)

class EditorViewModel(
    private val container: AppContainer,
    private val resolver: ContentResolver,
    val setId: String,
) : ViewModel() {
    private val library = container.library

    val set: StateFlow<WallpaperSet?> = library.state.map { it.set(setId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, library.state.value.set(setId))
    val isActive: StateFlow<Boolean> = library.state.map { it.activeSetId == setId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, library.state.value.activeSetId == setId)

    private val _importing = MutableStateFlow<ImportProgress?>(null)
    val importing: StateFlow<ImportProgress?> = _importing.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()
    private val _exported = MutableSharedFlow<SetExporter.Saved?>(extraBufferCapacity = 1)
    /** A finished export: the saved file, to offer for sharing, or null when there is nothing to share. */
    val exported: SharedFlow<SetExporter.Saved?> = _exported

    fun rename(name: String) = viewModelScope.launch { library.rename(setId, name) }

    fun setBlur(radius: Int) = viewModelScope.launch { library.setBlur(setId, radius) }

    fun setActive(active: Boolean) = viewModelScope.launch {
        if (!library.activate(if (active) setId else null)) _messages.emit("先添加图片再启用")
    }

    fun setStart(itemId: String, minute: Int) = viewModelScope.launch {
        if (!library.setStart(setId, itemId, minute)) _messages.emit("${DaySchedule.format(minute)} 已经有一张图了，换个时间吧")
    }

    fun setAnchor(itemId: String, anchor: ThemeAnchor) = viewModelScope.launch { library.setAnchor(setId, itemId, anchor) }

    /** The phone's current theme switch times, for showing anchored items at their real start. */
    fun themeTimes(): ThemeTimes = container.systemTheme.read()

    fun deleteItem(itemId: String) = viewModelScope.launch { library.deleteItem(setId, itemId) }

    fun applyNow(itemId: String) = viewModelScope.launch {
        container.switcher.applyNow(setId, itemId)
        val error = container.switcher.status.value.lastError
        _messages.emit(if (error == null) "已应用，到下一个切换点恢复按时间表" else "设置壁纸失败：$error")
    }

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty() || _importing.value != null) return
        viewModelScope.launch {
            _importing.value = ImportProgress(0, uris.size)
            try {
                val sources = withContext(Dispatchers.IO) {
                    uris.map { uri -> ImportSource(displayName(uri)) { ImageDecoder.createSource(resolver, uri) } }
                }
                val failed = library.addImages(setId, sources, onProgress = { done -> _importing.value = ImportProgress(done, uris.size) })
                if (failed > 0) _messages.emit("有 $failed 张图片无法读取，已跳过")
            } finally {
                _importing.value = null
            }
        }
    }

    /** Saves the set to Download/壁纸时移 (Android 10+). */
    @RequiresApi(29)
    fun export() = runExport { container.exporter.exportToDownloads(it) }

    /** Saves the set to the document the user chose in 「保存到…」 (Android 9). */
    fun exportTo(uri: Uri) = runExport {
        container.exporter.exportTo(uri, it)
        null
    }

    private fun runExport(save: suspend (WallpaperSet) -> SetExporter.Saved?) {
        val current = set.value ?: return
        if (_exporting.value) return
        _exporting.value = true
        viewModelScope.launch {
            try {
                _exported.emit(save(current))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Export failed", error)
                _messages.emit("导出失败：${error.message ?: error.javaClass.simpleName}")
            } finally {
                _exporting.value = false
            }
        }
    }

    fun deleteSet(onDeleted: () -> Unit) = viewModelScope.launch {
        library.deleteSet(setId)
        onDeleted()
    }

    /** The file name without extension, or "" when the provider hides it (the photo picker reports media ids). */
    private fun displayName(uri: Uri): String = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.substringBeforeLast('.')?.takeUnless { name -> name.all(Char::isDigit) }.orEmpty()

    private companion object {
        const val TAG = "EditorViewModel"
    }
}
