package org.feeluown.mobile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Suppress("DEPRECATION")
@Composable
internal actual fun FoliaSystemBarsEffect() {
    val context = LocalContext.current
    val activity = remember(context) {
        var current: Context? = context
        var found: Activity? = null
        while (current != null) {
            if (current is Activity) {
                found = current
                break
            }
            current = (current as? ContextWrapper)?.baseContext
        }
        found
    }
    // The normal player never occludes Keyguard. Only LockscreenFoliaActivity may do so.
    DisposableEffect(activity) {
        val window = activity?.window
        val previousUi = window?.decorView?.systemUiVisibility
        val wasFullscreen = window?.attributes?.flags?.and(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        ) != 0
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )
        window?.decorView?.systemUiVisibility = (previousUi ?: 0) or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        onDispose {
            if (previousUi != null) window?.decorView?.systemUiVisibility = previousUi
            if (!wasFullscreen) window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }
}
