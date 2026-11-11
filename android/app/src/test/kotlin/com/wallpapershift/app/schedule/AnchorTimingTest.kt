package com.wallpapershift.app.schedule

import com.wallpapershift.app.data.ThemeAnchor
import com.wallpapershift.app.data.WallpaperItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class AnchorTimingTest {
    private val morning = WallpaperItem("morning", 360, anchor = ThemeAnchor.LIGHT_START)
    private val night = WallpaperItem("night", 1080, anchor = ThemeAnchor.DARK_START)
    private val demo = listOf(
        WallpaperItem("dawn", 240), morning, WallpaperItem("noon", 660),
        WallpaperItem("afternoon", 840), night, WallpaperItem("deepnight", 1380),
    )

    @Test fun `sunrise and sunset clamp at both inclusive safety boundaries`() {
        for ((item, range) in listOf(morning to 270..570, night to 930..1320)) {
            for (minute in listOf(0, range.first - 1, range.first, range.first + 1,
                range.last - 1, range.last, range.last + 1, 1439)) {
                val theme = ThemeTimes(lightStart = minute, darkStart = minute)
                assertEquals(minute.coerceIn(range), AnchorTiming.startMinute(item, theme))
                assertEquals(360, morning.startMinute)
                assertEquals(1080, night.startMinute)
            }
        }
    }

    @Test fun `missing or invalid sources preserve each stored fallback independently`() {
        for (source in listOf(null, -1, 1440)) {
            val theme = ThemeTimes(lightStart = source, darkStart = source)
            assertEquals(360, AnchorTiming.startMinute(morning, theme))
            assertEquals(1080, AnchorTiming.startMinute(night, theme))
        }
        assertEquals(500, AnchorTiming.startMinute(morning.copy(startMinute = 500), ThemeTimes.UNKNOWN))
        assertEquals(360, AnchorTiming.startMinute(morning, ThemeTimes(darkStart = 1100)))
        assertEquals(1100, AnchorTiming.startMinute(night, ThemeTimes(darkStart = 1100)))
        assertEquals(120, AnchorTiming.startMinute(WallpaperItem("fixed", 120), ThemeTimes(0, 1439)))
    }

    @Test fun `default order survives every source minute and opposite seasonal extremes`() {
        for (minute in 0 until MINUTES_PER_DAY) {
            val resolved = DaySchedule.resolve(demo, ThemeTimes(lightStart = minute, darkStart = 1439 - minute))
            assertEquals(demo.map { it.id }, resolved.map { it.id })
            assertTrue(resolved.map { it.startMinute }.zipWithNext().all { (a, b) -> b - a >= 30 })
            assertEquals("deepnight", DaySchedule.activeItem(resolved, 0)?.id)
        }
        assertEquals(demo, DaySchedule.resolve(demo, ThemeTimes.UNKNOWN))
    }

    @Test fun `actual switch and next alarm use bounded times and sunset starts night`() {
        val resolved = DaySchedule.resolve(demo, ThemeTimes(lightStart = 250, darkStart = 1340))
        val beforeSunrise = ZonedDateTime.parse("2026-06-21T04:29:00+08:00[Asia/Shanghai]")
        assertEquals("dawn", DaySchedule.activeItem(resolved, beforeSunrise)?.id)
        assertEquals(beforeSunrise.plusMinutes(1), DaySchedule.nextSwitch(resolved, beforeSunrise))
        assertEquals("morning", DaySchedule.activeItem(resolved, 270)?.id)
        assertEquals("afternoon", DaySchedule.activeItem(resolved, 1319)?.id)
        assertEquals("night", DaySchedule.activeItem(resolved, 1320)?.id)
        assertEquals("deepnight", DaySchedule.activeItem(resolved, 1380)?.id)
    }
}
