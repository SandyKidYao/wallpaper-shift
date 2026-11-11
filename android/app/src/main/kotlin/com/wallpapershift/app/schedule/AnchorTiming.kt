package com.wallpapershift.app.schedule

import com.wallpapershift.app.data.ThemeAnchor
import com.wallpapershift.app.data.WallpaperItem

/** Shared by the switcher and every time preview; stored fallback times are never overwritten. */
object AnchorTiming {
    fun safeRange(anchor: ThemeAnchor): IntRange? = when (anchor) {
        ThemeAnchor.LIGHT_START -> (4 * 60 + 30)..(9 * 60 + 30)
        ThemeAnchor.DARK_START -> (15 * 60 + 30)..(22 * 60)
        ThemeAnchor.NONE -> null
    }

    fun sourceMinute(anchor: ThemeAnchor, theme: ThemeTimes): Int? = when (anchor) {
        ThemeAnchor.LIGHT_START -> theme.lightStart
        ThemeAnchor.DARK_START -> theme.darkStart
        ThemeAnchor.NONE -> null
    }?.takeIf { it in 0 until MINUTES_PER_DAY }

    fun startMinute(item: WallpaperItem, theme: ThemeTimes): Int {
        val range = safeRange(item.anchor) ?: return item.startMinute
        return sourceMinute(item.anchor, theme)?.coerceIn(range) ?: item.startMinute
    }
}
