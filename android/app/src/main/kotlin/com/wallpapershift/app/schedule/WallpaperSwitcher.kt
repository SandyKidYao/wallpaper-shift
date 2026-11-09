package com.wallpapershift.app.schedule

import android.util.Log
import com.wallpapershift.app.data.ImageStore
import com.wallpapershift.app.data.SetRepository
import com.wallpapershift.app.data.WallpaperItem
import com.wallpapershift.app.data.WallpaperSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Reconciles "what the schedule says should be showing now" with the system wallpaper, then arms the
 * alarm for the next boundary. Every entry point (alarm, boot, clock change, UI edit) just calls
 * [sync]; it is idempotent, so being called late, early, or twice is harmless.
 */
class WallpaperSwitcher(
    private val repository: SetRepository,
    private val images: ImageStore,
    private val applier: WallpaperApplier,
    private val alarms: SwitchAlarmScheduler,
    private val statusStore: SwitchStatusStore,
    private val themeTimes: () -> ThemeTimes = { ThemeTimes.UNKNOWN },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    val status: StateFlow<SwitchStatus> = statusStore.status

    /**
     * @param plannedAt when an alarm fired, its target time. The schedule is evaluated at
     *   max(now, plannedAt) so a slightly early wake-up can't pick the outgoing picture.
     * @param force re-apply even if we believe the current picture is already showing
     *   (e.g. the user changed the wallpaper in system settings).
     * @param releaseHold drop a manual preview from [applyNow] — used when the schedule itself changed.
     * @param checkMissed look for an overdue alarm that never arrived. Only meaningful when the user
     *   opens the app; after a reboot or clock change, overdue alarms are expected.
     */
    suspend fun sync(
        plannedAt: Long = 0,
        force: Boolean = false,
        releaseHold: Boolean = force,
        checkMissed: Boolean = false,
    ) = mutex.withLock {
        if (plannedAt > 0) {
            Log.i(TAG, "Alarm for ${localTime(plannedAt)} handled ${nowMillis() - plannedAt} ms late")
            // An alarm got through, so whatever blocked an earlier one no longer does.
            statusStore.update { it.copy(missedAt = 0) }
        } else if (checkMissed) {
            detectMissedAlarm()
        }
        val set = repository.state.value.activeSet
        if (set == null || set.items.isEmpty()) {
            alarms.cancel()
            statusStore.update { SwitchStatus() }
            return@withLock
        }
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(maxOf(nowMillis(), plannedAt)), ZoneId.systemDefault())
        val items = DaySchedule.resolve(set.items, themeTimes())
        val item = DaySchedule.activeItem(items, now)!!
        if (releaseHold) statusStore.update { it.copy(holdUntil = 0) }
        val status = statusStore.status.value
        val held = now.toInstant().toEpochMilli() < status.holdUntil
        if (force || (!held && keyOf(set, item) != status.appliedKey)) apply(set, item)
        val next = DaySchedule.nextSwitch(items, now)?.toInstant()?.toEpochMilli()
        val exact = if (next != null) {
            alarms.schedule(next)
        } else {
            alarms.cancel()
            false
        }
        statusStore.update { it.copy(nextSwitchAt = next ?: 0, nextExact = exact) }
    }

    fun clearMissed() = statusStore.update { it.copy(missedAt = 0) }

    /**
     * An exact alarm still undelivered long after its time was never going to arrive. Seen on vivo
     * (OriginOS): with "autostart" off the system refuses to start our killed process for it, and with
     * background power left on "智能控制" the frozen app's alarms are withheld (see [BackgroundSettings]).
     */
    private fun detectMissedAlarm() {
        val status = statusStore.status.value
        if (status.nextExact && status.nextSwitchAt > 0 && nowMillis() > status.nextSwitchAt + MISSED_GRACE_MS) {
            Log.w(TAG, "Alarm for ${localTime(status.nextSwitchAt)} never arrived; background start was likely blocked")
            statusStore.update { it.copy(missedAt = status.nextSwitchAt) }
        }
    }

    private fun localTime(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()

    /**
     * Shows [itemId] right away (a preview from the editor) and holds it until the active set's next
     * boundary, so a routine [sync] (e.g. reopening the app) doesn't immediately undo it.
     */
    suspend fun applyNow(setId: String, itemId: String) = mutex.withLock {
        val set = repository.state.value.set(setId) ?: return@withLock
        val item = set.items.firstOrNull { it.id == itemId } ?: return@withLock
        apply(set, item)
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis()), ZoneId.systemDefault())
        val until = repository.state.value.activeSet?.let { DaySchedule.nextSwitch(DaySchedule.resolve(it.items, themeTimes()), now) }?.toInstant()?.toEpochMilli()
        statusStore.update { it.copy(holdUntil = until ?: Long.MAX_VALUE) }
    }

    private suspend fun apply(set: WallpaperSet, item: WallpaperItem) {
        try {
            val started = nowMillis()
            val home = images.homeFile(item.id, set.blurRadius)
            val timing = withContext(Dispatchers.IO) { applier.apply(images.lockFile(item.id), home) }
            statusStore.update { it.copy(appliedKey = keyOf(set, item), appliedAt = nowMillis(), lastError = null) }
            Log.i(TAG, "Applied ${item.label.ifEmpty { item.id }} from '${set.name}' (blur ${set.blurRadius}) " +
                "in ${nowMillis() - started} ms [$timing]")
        } catch (error: Exception) {
            Log.e(TAG, "Applying wallpaper failed", error)
            statusStore.update { it.copy(lastError = error.message ?: error.javaClass.simpleName) }
        }
    }

    private fun keyOf(set: WallpaperSet, item: WallpaperItem) = "${set.id}/${item.id}/b${set.blurRadius}"

    private companion object {
        const val TAG = "WallpaperSwitcher"
        /** Exact alarms land within seconds; 15 minutes leaves room for slow wake-ups without false alarms. */
        const val MISSED_GRACE_MS = 15 * 60 * 1000L
    }
}
