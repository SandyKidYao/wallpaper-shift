package com.wallpapershift.app.ui.home

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wallpapershift.app.AppContainer
import com.wallpapershift.app.data.SetPackage
import com.wallpapershift.app.data.SetPackageException
import com.wallpapershift.app.data.WallpaperItem
import com.wallpapershift.app.data.WallpaperSet
import com.wallpapershift.app.schedule.BackgroundSettings
import com.wallpapershift.app.schedule.DaySchedule
import com.wallpapershift.app.schedule.Reliability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

data class HomeUiState(
    val sets: List<WallpaperSet> = emptyList(),
    val activeSet: WallpaperSet? = null,
    val currentItem: WallpaperItem? = null,
    val nextItem: WallpaperItem? = null,
    val nextSwitchAt: ZonedDateTime? = null,
    val now: ZonedDateTime = ZonedDateTime.now(),
    /** True while a manual preview from the editor is showing instead of [currentItem]. */
    val previewing: Boolean = false,
    /** What the preview put on screen (it may come from another set), shown in place of [currentItem]. */
    val previewItem: WallpaperItem? = null,
    val previewBlur: Int = 0,
    /** Planned time of a switch the system blocked, if one was detected. */
    val missedAt: ZonedDateTime? = null,
    val lastError: String? = null,
) {
    val nowMinute: Int get() = now.hour * 60 + now.minute
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val library = container.library

    /** Ticks on each wall-clock minute so "now" markers and the current picture stay fresh. */
    private val minutes = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(now)
            delay(60_000 - now % 60_000)
        }
    }

    val state: StateFlow<HomeUiState> = combine(library.state, container.switcher.status, minutes) { app, status, nowMillis ->
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        // Theme-anchored starts are resolved against the system's current switch times, like the switcher
        // does, for every set: the cards' time strips must show 06:18, not the 07:30 fallback.
        val theme = container.systemTheme.read()
        val sets = app.sets.map { it.copy(items = DaySchedule.resolve(it.items, theme)) }
        val active = sets.firstOrNull { it.id == app.activeSet?.id }
        val items = active?.items.orEmpty()
        val next = status.nextSwitchAt.takeIf { it > 0 }?.let { ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
        val previewing = nowMillis < status.holdUntil
        // appliedKey is "setId/itemId/bRadius" of what's on screen; during a preview that's the previewed picture.
        val preview = status.appliedKey?.split("/")?.takeIf { previewing && it.size >= 2 }?.let { (setId, itemId) ->
            app.set(setId)?.let { s -> s.items.firstOrNull { it.id == itemId }?.let { s to it } }
        }
        HomeUiState(
            // Stored oldest first; listed newest first, so a set just created or imported is on top.
            sets = sets.asReversed(),
            activeSet = active,
            currentItem = DaySchedule.activeItem(items, now),
            nextItem = next?.let { DaySchedule.activeItem(items, it) },
            nextSwitchAt = next,
            now = now,
            previewing = previewing,
            previewItem = preview?.second,
            previewBlur = preview?.first?.blurRadius ?: 0,
            missedAt = status.missedAt.takeIf { it > 0 }?.let { ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), zone) },
            lastError = status.lastError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _reliability = MutableStateFlow(container.reliability())
    val reliability: StateFlow<Reliability> = _reliability.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private val _busy = MutableStateFlow<String?>(null)
    /** A label while a long operation (first-launch demo setup, an import) runs, else null. */
    val busy: StateFlow<String?> = _busy.asStateFlow()
    /** Long operations run one at a time, so their progress labels don't fight. */
    private val longWork = Mutex()

    private val _imported = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** Id of a set that was just imported, so the list can scroll to it. */
    val imported: SharedFlow<String> = _imported

    init {
        viewModelScope.launch {
            if (container.demo.needsFirstLaunchSetup) longWork.withLock {
                val total = container.demo.count
                _busy.value = "正在准备示例壁纸…"
                try {
                    container.demo.setUpOnFirstLaunch { done -> _busy.value = "正在准备示例壁纸 $done/$total…" }
                } finally {
                    _busy.value = null
                }
            }
            // Self-heal on every launch: a force-stop clears alarms, and a blocked alarm stops the chain.
            // This re-arms it, and notices if a switch was missed.
            container.switcher.sync(checkMissed = true)
        }
    }

    fun refreshReliability() {
        _reliability.value = container.reliability()
        viewModelScope.launch { container.switcher.sync(checkMissed = true) }
    }

    fun settingsIntent(step: BackgroundSettings.Step): Intent? = container.backgroundSettings.steps()[step]

    /** The user says a step is done; we can't verify it, but the next delivered alarm will. */
    fun confirmStep(step: BackgroundSettings.Step) {
        container.backgroundSettings.confirm(step)
        container.switcher.clearMissed()
        _reliability.value = container.reliability()
    }

    fun createSet(onCreated: (String) -> Unit) = viewModelScope.launch {
        onCreated(library.createSet("壁纸集 ${state.value.sets.size + 1}"))
    }

    /** Imports a set package (zip) picked in the file chooser or opened from another app. */
    fun importPackage(uri: Uri) = viewModelScope.launch {
        var importedId: String? = null
        val message = longWork.withLock {
            _busy.value = "正在导入…"
            try {
                val result = container.importer.import(uri) { name, done, total -> _busy.value = "正在导入「$name」$done/$total…" }
                importedId = result.setId
                when {
                    result.overLimit > 0 -> "已导入「${result.name}」，一次最多导入 ${SetPackage.MAX_IMAGES} 张，其余已跳过"
                    result.failed > 0 -> "已导入「${result.name}」，有 ${result.failed} 张图片无法读取，已跳过"
                    else -> "已导入「${result.name}」"
                }
            } catch (error: SetPackageException) {
                error.message ?: SetPackage.NOT_A_SET
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // e.g. SecurityException once another app's read grant is gone.
                Log.w(TAG, "Import failed", error)
                SetPackage.UNREADABLE
            } finally {
                _busy.value = null
            }
        }
        importedId?.let { _imported.emit(it) }
        _messages.emit(message)
    }

    fun setActive(setId: String, active: Boolean) = viewModelScope.launch {
        val ok = library.activate(if (active) setId else null)
        if (!ok) _messages.emit("先给这个壁纸集添加图片")
    }

    /** After a failed switch: try the current picture again. */
    fun retry() = forceSync("壁纸已设置")

    /** Ends a manual preview: the scheduled picture goes back up right away. */
    fun resumeSchedule() = forceSync("已恢复按时间表")

    private fun forceSync(done: String) = viewModelScope.launch {
        container.switcher.sync(force = true)
        val error = container.switcher.status.value.lastError
        _messages.emit(if (error == null) done else "设置壁纸失败：$error")
    }

    private companion object {
        const val TAG = "HomeViewModel"
    }
}
