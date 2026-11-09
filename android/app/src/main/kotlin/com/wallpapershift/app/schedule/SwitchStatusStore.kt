package com.wallpapershift.app.schedule

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SwitchStatus(
    /** `setId/itemId/bRadius` of what we last pushed to the system, so repeated syncs are no-ops. */
    val appliedKey: String? = null,
    val appliedAt: Long = 0,
    /** Epoch millis of the pending alarm, 0 when nothing is scheduled. */
    val nextSwitchAt: Long = 0,
    /** Whether that alarm is exact; only exact alarms can be judged "missed" by lateness. */
    val nextExact: Boolean = false,
    /** Planned time of an alarm that never arrived (the OS blocked our background start), 0 = none. */
    val missedAt: Long = 0,
    /** A manual "apply this one now" preview stays up until this time (the next boundary), 0 = none. */
    val holdUntil: Long = 0,
    val lastError: String? = null,
)

/** Settings outside the app that decide whether switches happen on time. */
data class Reliability(
    val exactAlarms: Boolean = true,
    val batteryUnrestricted: Boolean = true,
    /** Manufacturer-specific steps this phone needs (see [BackgroundSettings]) and where each stands. */
    val oemSteps: Map<BackgroundSettings.Step, StepStatus> = emptyMap(),
) {
    val oemUnconfirmed: Boolean get() = oemSteps.values.any { !it.done }
}

/**
 * [done]: the phone reports the setting on or, when it can't tell ([detected] false), the user said so.
 * Only an undetected step needs the user's 「我已设置」.
 */
data class StepStatus(val done: Boolean, val detected: Boolean)

/** [SwitchStatus] persisted in SharedPreferences, so it survives the process dying between alarms. */
class SwitchStatusStore(context: Context) {
    private val prefs = context.getSharedPreferences("switch_status", Context.MODE_PRIVATE)
    private val _status = MutableStateFlow(read())
    val status: StateFlow<SwitchStatus> = _status.asStateFlow()

    fun update(transform: (SwitchStatus) -> SwitchStatus) {
        val next = transform(_status.value)
        prefs.edit {
            putString(KEY_APPLIED, next.appliedKey)
            putLong(KEY_APPLIED_AT, next.appliedAt)
            putLong(KEY_NEXT, next.nextSwitchAt)
            putBoolean(KEY_NEXT_EXACT, next.nextExact)
            putLong(KEY_MISSED, next.missedAt)
            putLong(KEY_HOLD, next.holdUntil)
            putString(KEY_ERROR, next.lastError)
        }
        _status.value = next
    }

    private fun read() = SwitchStatus(
        appliedKey = prefs.getString(KEY_APPLIED, null),
        appliedAt = prefs.getLong(KEY_APPLIED_AT, 0),
        nextSwitchAt = prefs.getLong(KEY_NEXT, 0),
        nextExact = prefs.getBoolean(KEY_NEXT_EXACT, false),
        missedAt = prefs.getLong(KEY_MISSED, 0),
        holdUntil = prefs.getLong(KEY_HOLD, 0),
        lastError = prefs.getString(KEY_ERROR, null),
    )

    private companion object {
        const val KEY_APPLIED = "appliedKey"
        const val KEY_APPLIED_AT = "appliedAt"
        const val KEY_NEXT = "nextSwitchAt"
        const val KEY_NEXT_EXACT = "nextExact"
        const val KEY_MISSED = "missedAt"
        const val KEY_HOLD = "holdUntil"
        const val KEY_ERROR = "lastError"
    }
}
