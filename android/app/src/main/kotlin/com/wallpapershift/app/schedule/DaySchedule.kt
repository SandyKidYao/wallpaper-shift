package com.wallpapershift.app.schedule

import com.wallpapershift.app.data.ThemeAnchor
import com.wallpapershift.app.data.WallpaperItem
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

const val MINUTES_PER_DAY = 24 * 60

/**
 * Pure time arithmetic for a daily wallpaper rotation. No Android types, so it is unit-tested on the JVM.
 *
 * Model: items sorted by start minute partition the day into segments. Before the first start of the
 * day, the previous day's last item is still showing.
 */
object DaySchedule {

    /**
     * [items] with theme-anchored starts replaced by the system's switch times, re-sorted by start.
     * An anchored item whose system time is unknown keeps its own [WallpaperItem.startMinute].
     */
    fun resolve(items: List<WallpaperItem>, theme: ThemeTimes): List<WallpaperItem> {
        if (items.none { it.anchor != ThemeAnchor.NONE }) return items
        return items.map { item ->
            val minute = when (item.anchor) {
                ThemeAnchor.NONE -> null
                ThemeAnchor.DARK_START -> theme.darkStart
                ThemeAnchor.LIGHT_START -> theme.lightStart
            }
            if (minute == null) item else item.copy(startMinute = minute)
        }.sortedBy { it.startMinute }
    }

    /** The item that should be showing at [minuteOfDay]. With duplicate starts, the later one in the list wins. */
    fun activeItem(items: List<WallpaperItem>, minuteOfDay: Int): WallpaperItem? {
        if (items.isEmpty()) return null
        return items.lastOrNull { it.startMinute <= minuteOfDay } ?: items.last()
    }

    fun activeItem(items: List<WallpaperItem>, at: ZonedDateTime): WallpaperItem? =
        activeItem(items, at.hour * 60 + at.minute)

    /**
     * The next instant strictly after [now] at which the active item changes, or null if it never does
     * (fewer than two distinct start times). DST gaps are resolved by [ZonedDateTime] (shifted forward).
     */
    fun nextSwitch(items: List<WallpaperItem>, now: ZonedDateTime): ZonedDateTime? {
        val starts = items.map { it.startMinute }.distinct().sorted()
        if (starts.size < 2) return null
        val today = now.truncatedTo(ChronoUnit.DAYS)
        for (dayOffset in 0L..1L) {
            val day = today.toLocalDate().plusDays(dayOffset)
            for (start in starts) {
                val candidate = day.atTime(start / 60, start % 60).atZone(now.zone)
                if (candidate.isAfter(now)) return candidate
            }
        }
        return null
    }

    /** Minute at which [items]\[index] stops showing (the next item's start, wrapping to tomorrow). */
    fun endMinute(items: List<WallpaperItem>, index: Int): Int =
        if (index + 1 < items.size) items[index + 1].startMinute else items.first().startMinute

    /**
     * Start times for [count] new pictures. An empty set is spread evenly from 06:00 in pick order;
     * otherwise each new picture splits the largest remaining gap. Results are rounded to 5 minutes
     * and never collide with existing or other new starts.
     */
    fun suggestStarts(existing: List<Int>, count: Int): List<Int> {
        val taken = existing.toMutableSet()
        val result = mutableListOf<Int>()
        if (taken.isEmpty()) {
            for (i in 0 until count) {
                val start = freeSlot(roundTo5(6 * 60 + i * MINUTES_PER_DAY / count), taken)
                taken += start
                result += start
            }
            return result
        }
        repeat(count) {
            val sorted = taken.sorted()
            var bestStart = sorted.last()
            var bestGap = sorted.first() + MINUTES_PER_DAY - sorted.last()
            for (i in 0 until sorted.size - 1) {
                val gap = sorted[i + 1] - sorted[i]
                if (gap > bestGap) {
                    bestGap = gap
                    bestStart = sorted[i]
                }
            }
            val start = freeSlot(roundTo5((bestStart + bestGap / 2) % MINUTES_PER_DAY), taken)
            taken += start
            result += start
        }
        return result
    }

    fun format(minute: Int): String = "%02d:%02d".format((minute / 60) % 24, minute % 60)

    private fun roundTo5(minute: Int): Int = ((minute + 2) / 5 * 5) % MINUTES_PER_DAY

    private fun freeSlot(preferred: Int, taken: Set<Int>): Int {
        var candidate = preferred
        while (candidate in taken) candidate = (candidate + 1) % MINUTES_PER_DAY
        return candidate
    }
}
