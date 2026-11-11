package com.wallpapershift.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wallpapershift.app.WallpaperShiftApplication
import com.wallpapershift.app.data.DemoSet
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * adb hooks for on-device testing (debug builds only):
 *
 *   # Demo set with natural times (dawn 04:00, sunrise morning, sunset night, deep night 23:00)
 *   adb shell am broadcast -n com.wallpapershift.app.debug/com.wallpapershift.app.debug.DebugReceiver -a DEMO
 *   # Compressed day: first picture starts 1 min from now, then one every 2 min
 *   adb shell am broadcast -n ...DebugReceiver -a DEMO --ei startInMin 1 --ei everyMin 2
 *   # A larger, inactive set to test layouts (12 pictures), and deleting sets by name
 *   adb shell am broadcast -n ...DebugReceiver -a DEMO --ei copies 2 --ez activate false --es name 测试多图
 *   adb shell am broadcast -n ...DebugReceiver -a DELETE_SET --es name 测试多图
 *   # Re-run the reconcile step (optionally forcing a re-apply), or wipe all sets
 *   adb shell am broadcast -n ...DebugReceiver -a SYNC [--ez force true]
 *   adb shell am broadcast -n ...DebugReceiver -a RESET
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as WallpaperShiftApplication).container
        val pending = goAsync()
        container.scope.launch {
            try {
                when (intent.action) {
                    "DEMO" -> {
                        val startIn = intent.getIntExtra("startInMin", -1)
                        val every = intent.getIntExtra("everyMin", 2)
                        val starts = if (startIn < 0) null else {
                            val now = LocalTime.now().let { it.hour * 60 + it.minute }
                            List(container.demo.count) { (now + startIn + it * every) % (24 * 60) }
                        }
                        val id = container.demo.install(
                            starts = starts,
                            copies = intent.getIntExtra("copies", 1),
                            activate = intent.getBooleanExtra("activate", true),
                            name = intent.getStringExtra("name") ?: DemoSet.NAME,
                        )
                        Log.i(TAG, "Demo set $id installed, starts=$starts")
                    }
                    "SYNC" -> container.switcher.sync(force = intent.getBooleanExtra("force", false))
                    "DELETE_SET" -> {
                        val name = intent.getStringExtra("name")
                        container.library.state.value.sets.filter { it.name == name }.forEach { container.library.deleteSet(it.id) }
                    }
                    "RESET" -> container.library.state.value.sets.forEach { container.library.deleteSet(it.id) }
                    else -> Log.w(TAG, "Unknown action ${intent.action}")
                }
                Log.i(TAG, "${intent.action} done; status=${container.switcher.status.value}")
            } catch (error: Exception) {
                Log.e(TAG, "${intent.action} failed", error)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "WallpaperShiftDebug"
    }
}
