package com.wallpapershift.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wallpapershift.app.ui.editor.EditorScreen
import com.wallpapershift.app.ui.editor.EditorViewModel
import com.wallpapershift.app.ui.home.HomeScreen
import com.wallpapershift.app.ui.home.HomeViewModel
import com.wallpapershift.app.ui.theme.WallpaperShiftTheme

class MainActivity : ComponentActivity() {
    /** A zip another app opened with us (「用其他应用打开」/ share), waiting to be imported on the home screen. */
    private var incomingPackage by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Not after a recreation: that intent was already handled.
        if (savedInstanceState == null) incomingPackage = packageUri(intent)
        val container = (application as WallpaperShiftApplication).container
        val thumbOf = container.images::thumbFile
        setContent {
            WallpaperShiftTheme {
                // Two screens don't warrant a navigation library: null = home, otherwise the set being edited.
                var editingSetId by rememberSaveable { mutableStateOf<String?>(null) }
                // An import reports its progress on the home screen.
                LaunchedEffect(incomingPackage) { if (incomingPackage != null) editingSetId = null }
                when (val setId = editingSetId) {
                    null -> HomeScreen(
                        viewModel = viewModel { HomeViewModel(container) },
                        thumbOf = thumbOf,
                        onOpenSet = { editingSetId = it },
                        incomingPackage = incomingPackage,
                        onIncomingHandled = { incomingPackage = null },
                    )
                    else -> {
                        BackHandler { editingSetId = null }
                        EditorScreen(
                            viewModel = viewModel(key = "editor/$setId") { EditorViewModel(container, contentResolver, setId) },
                            thumbOf = thumbOf,
                            onBack = { editingSetId = null },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        packageUri(intent)?.let { incomingPackage = it }
    }

    private fun packageUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        else -> null
    }
}
