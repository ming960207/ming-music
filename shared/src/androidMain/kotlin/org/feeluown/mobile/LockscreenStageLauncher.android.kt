package org.feeluown.mobile

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Do not create a second "lockscreen player": it loses the actual Folia WebView and lyrics.
 * Show the existing immersive MainActivity over Keyguard. The user must already be in the
 * immersive mode and explicitly enable the switch; Xiaomi can additionally require AppOp 10020.
 */
@Composable
internal actual fun OpenLockscreenStageWhenEnabled(enabled: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
    DisposableEffect(activity, lifecycleOwner, enabled) {
        val shouldShow = enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
            activity != null
        val prefs = context.getSharedPreferences("ming_immersive_lockscreen", Context.MODE_PRIVATE)
        fun record(phase: String) {
            val locked = (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
                ?.isKeyguardLocked == true
            prefs.edit().putString("stage_last_phase", phase)
                .putBoolean("stage_last_keyguard", locked)
                .putLong("stage_last_timestamp", System.currentTimeMillis())
                .apply()
            Log.i("MingLockscreenStage", "$phase locked=$locked")
        }
        if (shouldShow) {
            activity?.setShowWhenLocked(true)
            record("原始 Folia 舞台保留显示")
        }
        val observer = LifecycleEventObserver { _, event ->
            if (shouldShow) when (event) {
                Lifecycle.Event.ON_RESUME -> record("Folia onResume")
                Lifecycle.Event.ON_PAUSE -> record("Folia onPause")
                Lifecycle.Event.ON_STOP -> record("Folia onStop")
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (shouldShow) {
                activity?.setShowWhenLocked(false)
                record("退出 Folia 锁屏舞台")
            }
        }
    }
}
