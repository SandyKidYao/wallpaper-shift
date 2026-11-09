package com.wallpapershift.app

import android.app.Application

class WallpaperShiftApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
