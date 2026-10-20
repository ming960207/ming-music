package org.feeluown.mobile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The immersive player is ALREADY the correct Folia surface and has the resolved lyric session.
 * Never launch a second Activity merely because the lock-screen option is enabled.
 * Instead, let the current foreground player Activity stay visible over Keyguard.
 *
 * This avoids replacing the real stage with a second, uninitialized presentation on HyperOS.
 * Background Activity launches and Keyguard dismissal are intentionally not used.
 */
@Composable
internal actual fun OpenLockscreenStageWhenEnabled(enabled: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) {
        var candidate: Context? = context
        var result: Activity? = null
        while (candidate != null) {
            if (candidate is Activity) {
                result = candidate
                break
            }
            candidate = (candidate as? ContextWrapper)?.baseContext
        }
        result
    }
    DisposableEffect(activity, enabled) {
        val shouldShow = enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
            activity != null
        if (shouldShow) {
            activity?.setShowWhenLocked(true)
            Log.i("MingLockscreenStage", "Retain existing immersive player over Keyguard")
        }
        onDispose {
            if (shouldShow) {
                activity?.setShowWhenLocked(false)
                Log.i("MingLockscreenStage", "Restored regular Keyguard behavior")
            }
        }
    }
}
