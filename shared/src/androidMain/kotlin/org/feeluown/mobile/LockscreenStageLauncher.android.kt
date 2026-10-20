package org.feeluown.mobile

import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun OpenLockscreenStageWhenEnabled(enabled: Boolean) {
    val context = LocalContext.current
    LaunchedEffect(enabled) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // User entered the immersive player, so this is a permitted foreground launch.
            // Do not attempt to start Activities from the media service or screen-off broadcasts.
            val prefs = context.getSharedPreferences(
                "ming_immersive_lockscreen", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putString("stage_last_phase", "发起启动")
                .putLong("stage_last_timestamp", System.currentTimeMillis())
                .apply()
            runCatching {
                context.startActivity(
                    Intent().setClassName(
                        context.packageName, "org.feeluown.mobile.LockscreenFoliaActivity")
                )
            }.onFailure { error ->
                android.util.Log.e("MingLockscreenStage", "Lockscreen stage launch failed", error)
                prefs.edit()
                    .putString("stage_last_phase", "启动失败：${error.javaClass.simpleName}")
                    .putLong("stage_last_timestamp", System.currentTimeMillis())
                    .apply()
            }
        }
    }
}
