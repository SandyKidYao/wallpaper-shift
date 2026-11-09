package com.wallpapershift.app.schedule

import com.wallpapershift.app.data.ThemeAnchor
import com.wallpapershift.app.data.WallpaperItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DayScheduleTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")

    /** The demo set: dawn 05:00, morning 07:30, noon 11:00, afternoon 14:00, sunset 17:30, night 19:30. */
    private val day = listOf(
        item("dawn", 5, 0), item("morning", 7, 30), item("noon", 11, 0),
        item("afternoon", 14, 0), item("sunset", 17, 30), item("night", 19, 30),
    )

    private fun item(id: String, h: Int, m: Int) = WallpaperItem(id = id, startMinute = h * 60 + m, label = id)
    private fun at(h: Int, m: Int, s: Int = 0, zone: ZoneId = shanghai, date: String = "2026-10-20") =
        LocalDateTime.parse("${date}T%02d:%02d:%02d".format(h, m, s)).atZone(zone)

    @Test fun `active item is the latest start at or before now`() {
        assertEquals("dawn", DaySchedule.activeItem(day, at(5, 0))?.id)
        assertEquals("dawn", DaySchedule.activeItem(day, at(7, 29, 59))?.id)
        assertEquals("morning", DaySchedule.activeItem(day, at(7, 30))?.id)
        assertEquals("night", DaySchedule.activeItem(day, at(23, 59))?.id)
    }

    @Test fun `before the first start the previous evening's last item is still showing`() {
        assertEquals("night", DaySchedule.activeItem(day, at(0, 0))?.id)
        assertEquals("night", DaySchedule.activeItem(day, at(4, 59))?.id)
    }

    @Test fun `empty set has no active item and never switches`() {
        assertNull(DaySchedule.activeItem(emptyList(), at(12, 0)))
        assertNull(DaySchedule.nextSwitch(emptyList(), at(12, 0)))
    }

    @Test fun `a single picture never schedules a switch`() {
        val one = listOf(item("only", 8, 0))
        assertEquals("only", DaySchedule.activeItem(one, at(3, 0))?.id)
        assertNull(DaySchedule.nextSwitch(one, at(3, 0)))
    }

    @Test fun `next switch is strictly after now`() {
        assertEquals(at(7, 30), DaySchedule.nextSwitch(day, at(5, 0)))
        // Fired exactly on a boundary: the following boundary is next, not the same one again.
        assertEquals(at(11, 0), DaySchedule.nextSwitch(day, at(7, 30)))
        assertEquals(at(11, 0), DaySchedule.nextSwitch(day, at(7, 30, 1)))
    }

    @Test fun `next switch wraps to tomorrow's first start after the last one`() {
        assertEquals(at(5, 0, date = "2026-10-21"), DaySchedule.nextSwitch(day, at(19, 30)))
        assertEquals(at(5, 0, date = "2026-10-21"), DaySchedule.nextSwitch(day, at(23, 59, 59)))
        assertEquals(at(5, 0), DaySchedule.nextSwitch(day, at(0, 0)))
    }

    @Test fun `DST gap shifts the switch forward instead of skipping it`() {
        // America/New_York springs forward 2026-03-08 02:00 -> 03:00; 02:30 does not exist.
        val ny = ZoneId.of("America/New_York")
        val items = listOf(item("a", 1, 0), item("b", 2, 30))
        val next = DaySchedule.nextSwitch(items, at(1, 30, zone = ny, date = "2026-03-08"))!!
        assertEquals(ZonedDateTime.of(2026, 3, 8, 3, 30, 0, 0, ny), next)
    }

    @Test fun `end minute is the next start, wrapping to the first`() {
        assertEquals(7 * 60 + 30, DaySchedule.endMinute(day, 0))
        assertEquals(5 * 60, DaySchedule.endMinute(day, day.lastIndex))
    }

    @Test fun `suggested starts for an empty set spread evenly from 6am in pick order`() {
        assertEquals(listOf(360, 600, 840, 1080, 1320, 120), DaySchedule.suggestStarts(emptyList(), 6))
        assertEquals(listOf(360), DaySchedule.suggestStarts(emptyList(), 1))
    }

    @Test fun `suggested starts for a non-empty set split the largest gap without collisions`() {
        // 06:00 and 12:00 -> the largest gap is 12:00..06:00 (18h), midpoint 21:00.
        assertEquals(listOf(21 * 60), DaySchedule.suggestStarts(listOf(360, 720), 1))
        val many = DaySchedule.suggestStarts(listOf(360), 30)
        assertEquals(30, many.toSet().size)
        assertTrue(many.none { it == 360 })
        assertTrue(many.all { it in 0 until MINUTES_PER_DAY })
    }

    private val theme = ThemeTimes(darkStart = 17 * 60 + 30, lightStart = 6 * 60 + 45)

    @Test fun `anchored items take the system switch time and the list is re-sorted`() {
        val items = listOf(
            WallpaperItem("day", 6 * 60, anchor = ThemeAnchor.LIGHT_START),
            WallpaperItem("noon", 12 * 60),
            WallpaperItem("night", 19 * 60 + 30, anchor = ThemeAnchor.DARK_START),
        )
        val resolved = DaySchedule.resolve(items, theme)
        assertEquals(listOf("day", "noon", "night"), resolved.map { it.id })
        assertEquals(listOf(6 * 60 + 45, 12 * 60, 17 * 60 + 30), resolved.map { it.startMinute })
        assertEquals("night", DaySchedule.activeItem(resolved, at(17, 30))?.id)
        assertEquals(at(17, 30), DaySchedule.nextSwitch(resolved, at(12, 0)))
    }

    @Test fun `an anchor moving past another item changes the order`() {
        val items = listOf(WallpaperItem("a", 8 * 60, anchor = ThemeAnchor.DARK_START), WallpaperItem("b", 12 * 60))
        assertEquals(listOf("b", "a"), DaySchedule.resolve(items, theme).map { it.id })
    }

    @Test fun `an anchored item keeps its own start when the system time is unknown`() {
        val items = listOf(WallpaperItem("night", 19 * 60 + 30, anchor = ThemeAnchor.DARK_START), WallpaperItem("day", 6 * 60))
        assertEquals(listOf(6 * 60, 19 * 60 + 30), DaySchedule.resolve(items, ThemeTimes.UNKNOWN).map { it.startMinute })
        // Only the dark side known: a light-anchored item still falls back.
        val light = listOf(WallpaperItem("day", 6 * 60, anchor = ThemeAnchor.LIGHT_START))
        assertEquals(6 * 60, DaySchedule.resolve(light, ThemeTimes(darkStart = 1050)).single().startMinute)
    }

    @Test fun `twilight times become dark at sunset and light at sunrise`() {
        // Values read from a vivo V2454A on 2026-11-05 18:08: sunset 17:04 today, sunrise 06:15 tomorrow.
        val times = ThemeTimes.fromTwilight(1793916932312, 1793869481518, 1793873304000, shanghai)
        assertEquals(ThemeTimes(darkStart = 17 * 60 + 4, lightStart = 6 * 60 + 15), times)
    }

    @Test fun `missing or stale twilight values are unknown`() {
        val now = 1793873304000
        assertEquals(ThemeTimes.UNKNOWN, ThemeTimes.fromTwilight(0, 0, now, shanghai))
        val week = 7 * 24 * 3600 * 1000L
        assertEquals(ThemeTimes.UNKNOWN, ThemeTimes.fromTwilight(1793916932312 - week, 1793869481518 - week, now, shanghai))
        assertEquals(17 * 60 + 4, ThemeTimes.fromTwilight(0, 1793869481518, now, shanghai).darkStart)
    }

    @Test fun `items without anchors are returned untouched`() {
        assertEquals(day, DaySchedule.resolve(day, theme))
    }

    @Test fun `format pads hours and minutes`() {
        assertEquals("05:00", DaySchedule.format(300))
        assertEquals("23:59", DaySchedule.format(1439))
    }
}
