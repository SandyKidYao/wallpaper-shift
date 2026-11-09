package com.wallpapershift.app

import android.content.Context
import android.os.PowerManager
import com.wallpapershift.app.data.DemoSet
import com.wallpapershift.app.data.ImageStore
import com.wallpapershift.app.data.SetExporter
import com.wallpapershift.app.data.SetImporter
import com.wallpapershift.app.data.SetRepository
import com.wallpapershift.app.data.WallpaperLibrary
import com.wallpapershift.app.imaging.ImageProcessor
import com.wallpapershift.app.schedule.BackgroundSettings
import com.wallpapershift.app.schedule.Reliability
import com.wallpapershift.app.schedule.StepStatus
import com.wallpapershift.app.schedule.SystemThemeSource
import com.wallpapershift.app.schedule.SwitchAlarmScheduler
import com.wallpapershift.app.schedule.SwitchStatusStore
import com.wallpapershift.app.schedule.WallpaperApplier
import com.wallpapershift.app.schedule.WallpaperSwitcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Process-wide singletons, shared by the UI and the broadcast receivers. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val imageProcessor by lazy { ImageProcessor(appContext) }
    val images by lazy { ImageStore(File(appContext.filesDir, "images"), imageProcessor) }
    val alarms by lazy { SwitchAlarmScheduler(appContext) }
    private val repository by lazy { SetRepository(File(appContext.filesDir, "sets.json")) }
    val systemTheme by lazy { SystemThemeSource(appContext) }
    val switcher by lazy {
        WallpaperSwitcher(repository, images, WallpaperApplier(appContext), alarms, SwitchStatusStore(appContext), systemTheme::read)
    }
    val library by lazy { WallpaperLibrary(repository, images, switcher, scope) }
    val demo by lazy { DemoSet(appContext, library) }
    val importer by lazy { SetImporter(appContext, library) }
    val exporter by lazy { SetExporter(appContext, images) }
    val backgroundSettings by lazy { BackgroundSettings(appContext) }

    fun reliability() = Reliability(
        exactAlarms = alarms.canScheduleExact(),
        batteryUnrestricted = appContext.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(appContext.packageName),
        oemSteps = backgroundSettings.steps().keys.associateWith { step ->
            val detected = backgroundSettings.detect(step)
            StepStatus(done = detected ?: backgroundSettings.isConfirmed(step), detected = detected != null)
        },
    )
}
