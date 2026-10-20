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
    // Keep the foreground lyric stage over Keyguard only after explicit opt-in.
    // Never wake the display, dismiss authentication, or start an activity in background.
    val coverKeyguard = remember(context) {
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1 &&
            context.getSharedPreferences("ming_immersive_lockscreen", Context.MODE_PRIVATE)
                .getBoolean("enabled", false)
    }
    DisposableEffect(activity, coverKeyguard) {
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1 && coverKeyguard) {
            activity?.setShowWhenLocked(true)
        }
        onDispose {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1 && coverKeyguard) {
                activity?.setShowWhenLocked(false)
            }
            if (previousUi != null) window?.decorView?.systemUiVisibility = previousUi
            if (!wasFullscreen) window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }
}
