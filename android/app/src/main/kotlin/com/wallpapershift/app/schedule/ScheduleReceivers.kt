package com.wallpapershift.app.schedule

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wallpapershift.app.AppContainer
import com.wallpapershift.app.WallpaperShiftApplication
import kotlinx.coroutines.launch

/** Fires at each switch boundary. Images are pre-rendered, so the work fits in the goAsync budget. */
class SwitchAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SwitchAlarmScheduler.ACTION_SWITCH) return
        val plannedAt = intent.getLongExtra(SwitchAlarmScheduler.EXTRA_PLANNED_AT, 0)
        runAsync(context, "Alarm switch") { it.switcher.sync(plannedAt = plannedAt) }
    }
}

/** Alarms don't survive reboots, and wall-clock alarms go stale when the time or zone changes. */
class RestoreScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESTORE_ACTIONS) return
        runAsync(context, "Restore after ${intent.action}") { it.switcher.sync() }
    }

    private companion object {
        val RESTORE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}

private fun BroadcastReceiver.runAsync(context: Context, what: String, block: suspend (AppContainer) -> Unit) {
    val pending = goAsync()
    val container = (context.applicationContext as WallpaperShiftApplication).container
    container.scope.launch {
        try {
            block(container)
        } catch (error: Exception) {
            Log.e("WallpaperShift", "$what failed", error)
        } finally {
            pending.finish()
        }
    }
}
