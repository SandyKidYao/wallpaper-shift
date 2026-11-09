package com.wallpapershift.app.schedule

import android.app.WallpaperManager
import android.content.Context
import android.os.SystemClock
import java.io.File

/** The only place that touches [WallpaperManager]. Blocking; call from a background dispatcher. */
class WallpaperApplier(context: Context) {
    private val manager = WallpaperManager.getInstance(context)

    /** Returns a short timing summary for the log. */
    fun apply(lock: File, home: File): String {
        check(manager.isWallpaperSupported && manager.isSetWallpaperAllowed) { "系统不允许本应用设置壁纸" }
        // Lock first: it is what the user sees when the screen wakes, and once the lock wallpaper is
        // separate, setting the home one can't leak onto it.
        val t0 = SystemClock.elapsedRealtime()
        val lockId = lock.inputStream().buffered().use { manager.setStream(it, null, true, WallpaperManager.FLAG_LOCK) }
        val t1 = SystemClock.elapsedRealtime()
        val homeId = home.inputStream().buffered().use { manager.setStream(it, null, true, WallpaperManager.FLAG_SYSTEM) }
        val t2 = SystemClock.elapsedRealtime()
        check(lockId != 0 && homeId != 0) { "系统拒绝了壁纸设置（lock=$lockId, home=$homeId）" }
        return "lock ${t1 - t0} ms, home ${t2 - t1} ms"
    }
}
