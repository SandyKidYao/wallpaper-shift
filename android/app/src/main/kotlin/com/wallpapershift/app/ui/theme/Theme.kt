package com.wallpapershift.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback palette: dusk indigo with a sunrise accent, matching the launcher icon.
private val LightColors = lightColorScheme(
    primary = Color(0xFF3F4A8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF9A4F2B),
    secondaryContainer = Color(0xFFFFDBCC),
    onSecondaryContainer = Color(0xFF370E00),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF0E1D6B),
    primaryContainer = Color(0xFF263271),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFFFB693),
    secondaryContainer = Color(0xFF7B3816),
    onSecondaryContainer = Color(0xFFFFDBCC),
)

/**
 * Uses Material You colours where available. Since those colours are extracted from the wallpaper,
 * the app's own palette drifts through the day along with the pictures it sets.
 */
@Composable
fun WallpaperShiftTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
