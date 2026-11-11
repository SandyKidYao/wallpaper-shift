package com.wallpapershift.app.data

import kotlinx.serialization.Serializable

/**
 * One picture in a set. It becomes the active wallpaper at [startMinute] (minute of day, 0..1439)
 * and stays active until the next item's start — wrapping past midnight back to the first item.
 *
 * Pixels live on disk under `images/<id>/` (see [ImageStore]); the model only carries metadata.
 */
@Serializable
data class WallpaperItem(
    val id: String,
    /** Fixed start time. With an [anchor], the safely bounded system theme time wins and this is the fallback. */
    val startMinute: Int,
    val label: String = "",
    val anchor: ThemeAnchor = ThemeAnchor.NONE,
)

/** Ties an item's start to the moment the phone's own theme flips, instead of a fixed clock time. */
@Serializable
enum class ThemeAnchor { NONE, DARK_START, LIGHT_START }

@Serializable
data class WallpaperSet(
    val id: String,
    val name: String,
    /** Home-screen blur strength, 0 (none) .. [MAX_BLUR]. The lock screen always gets the sharp image. */
    val blurRadius: Int = DEFAULT_BLUR,
    /** Always kept sorted by [WallpaperItem.startMinute]. */
    val items: List<WallpaperItem> = emptyList(),
) {
    companion object {
        const val DEFAULT_BLUR = 10
        const val MAX_BLUR = 25
    }
}

@Serializable
data class AppState(
    val sets: List<WallpaperSet> = emptyList(),
    /** Only one set drives the wallpaper at a time; null means switching is off. */
    val activeSetId: String? = null,
) {
    val activeSet: WallpaperSet? get() = sets.firstOrNull { it.id == activeSetId }
    fun set(id: String): WallpaperSet? = sets.firstOrNull { it.id == id }
}
