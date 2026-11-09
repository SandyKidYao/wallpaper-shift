package com.wallpapershift.app.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Keeps exactly one pending alarm: the next switch boundary. Each firing re-plans the following one,
 * so there is no long-lived repeating alarm to drift or go stale after edits.
 */
class SwitchAlarmScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    /** Exact alarms need user-granted access on Android 12+ (denied by default for new installs on 14+). */
    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    /** Arms the alarm; returns true if it is exact. */
    fun schedule(atMillis: Long): Boolean {
        val intent = pendingIntent(atMillis)
        try {
            if (canScheduleExact()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
                return true
            }
        } catch (_: SecurityException) {
            // Exact-alarm access can be revoked between the check and the call.
        }
        // Fallback: a bounded window. Plain inexact alarms may land up to an hour late; this keeps
        // the delay within [FALLBACK_WINDOW_MS] while the screen is on. In Doze it waits for the screen to
        // wake up, which is fine: a wallpaper only matters while someone is looking at it.
        alarms.setWindow(AlarmManager.RTC_WAKEUP, atMillis, FALLBACK_WINDOW_MS, intent)
        return false
    }

    fun cancel() = alarms.cancel(pendingIntent(0))

    private fun pendingIntent(atMillis: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, SwitchAlarmReceiver::class.java)
            .setAction(ACTION_SWITCH)
            .putExtra(EXTRA_PLANNED_AT, atMillis),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_SWITCH = "com.wallpapershift.action.SWITCH"
        const val EXTRA_PLANNED_AT = "plannedAt"

        /** Android 12+ extends shorter windows to 10 minutes anyway. */
        private const val FALLBACK_WINDOW_MS = 10 * 60 * 1000L
    }
}
