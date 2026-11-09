package com.wallpapershift.app.schedule

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * When the phone's theme flips, as minutes of day. A null side means the system doesn't tell us
 * (see [SystemThemeSource]); items anchored to it then keep their own fixed start.
 */
data class ThemeTimes(val darkStart: Int? = null, val lightStart: Int? = null) {
    companion object {
        val UNKNOWN = ThemeTimes()

        /** A twilight value older (or further ahead) than this is a stale leftover, not today's sun. */
        private const val MAX_TWILIGHT_AGE_MS = 3 * 24 * 60 * 60 * 1000L

        /**
         * Dark starts at sunset and light at sunrise. The system keeps the latest sunset and the next
         * sunrise as instants, so only their time of day counts. A value that is missing or stale gives null.
         */
        fun fromTwilight(sunriseMillis: Long, sunsetMillis: Long, nowMillis: Long, zone: ZoneId): ThemeTimes {
            fun minuteOf(millis: Long): Int? {
                if (millis <= 0 || abs(millis - nowMillis) > MAX_TWILIGHT_AGE_MS) return null
                val time = Instant.ofEpochMilli(millis).atZone(zone)
                return time.hour * 60 + time.minute
            }
            return ThemeTimes(darkStart = minuteOf(sunsetMillis), lightStart = minuteOf(sunriseMillis))
        }
    }
}

/**
 * Where the phone's dark mode switches, as far as an ordinary app can tell:
 * - custom schedule: the start and end the user set ([UiModeManager], Android 11+);
 * - automatic (sunset to sunrise): AOSP has no public API for the times, but vivo (OriginOS) publishes
 *   them in system settings as `vivo_sunset_time_millis` / `vivo_sunrise_time_millis`, readable by any app;
 * - anything else (fixed light or dark, other makers' automatic mode): unknown, so anchored items use
 *   their own fixed start.
 */
class SystemThemeSource(private val context: Context) {
    private val uiMode = context.getSystemService(UiModeManager::class.java)

    fun read(): ThemeTimes {
        if (Build.VERSION.SDK_INT < 30) return ThemeTimes.UNKNOWN
        return when (uiMode.nightMode) {
            UiModeManager.MODE_NIGHT_CUSTOM ->
                ThemeTimes(minuteOf(uiMode.customNightModeStart), minuteOf(uiMode.customNightModeEnd))
            UiModeManager.MODE_NIGHT_AUTO -> {
                val resolver = context.contentResolver
                ThemeTimes.fromTwilight(
                    sunriseMillis = Settings.System.getLong(resolver, VIVO_SUNRISE, 0),
                    sunsetMillis = Settings.System.getLong(resolver, VIVO_SUNSET, 0),
                    nowMillis = System.currentTimeMillis(),
                    zone = ZoneId.systemDefault(),
                )
            }
            else -> ThemeTimes.UNKNOWN
        }
    }

    private fun minuteOf(time: LocalTime) = time.hour * 60 + time.minute

    private companion object {
        const val VIVO_SUNRISE = "vivo_sunrise_time_millis"
        const val VIVO_SUNSET = "vivo_sunset_time_millis"
    }
}
