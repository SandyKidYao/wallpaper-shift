package com.wallpapershift.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * All sets live in one small JSON document. Every write goes through [update], which serialises
 * writers, keeps items sorted, and replaces the file atomically so a crash never leaves half a file.
 */
class SetRepository(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    suspend fun update(transform: (AppState) -> AppState): AppState = mutex.withLock {
        val next = normalize(transform(_state.value))
        if (next != _state.value) {
            withContext(Dispatchers.IO) { persist(next) }
            _state.value = next
        }
        next
    }

    private fun normalize(state: AppState): AppState = state.copy(
        sets = state.sets.map { set -> set.copy(items = set.items.sortedBy { it.startMinute }) },
        activeSetId = state.activeSetId?.takeIf { id -> state.sets.any { it.id == id } },
    )

    private fun load(): AppState = try {
        if (file.exists()) normalize(json.decodeFromString(AppState.serializer(), file.readText())) else AppState()
    } catch (error: Exception) {
        Log.e(TAG, "State file unreadable, starting empty", error)
        AppState()
    }

    private fun persist(state: AppState) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.encodeToString(AppState.serializer(), state))
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "Could not replace ${file.name}" }
        }
    }

    private companion object {
        const val TAG = "SetRepository"
    }
}
