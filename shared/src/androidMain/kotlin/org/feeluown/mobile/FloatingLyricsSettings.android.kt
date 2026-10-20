package org.feeluown.mobile

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal actual fun rememberFloatingLyricsSettingsAction(): FloatingLyricsSettingsAction? {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember(context) {
        context.getSharedPreferences("ming_floating_lyrics", android.content.Context.MODE_PRIVATE)
    }
    fun currentEnabled(): Boolean =
        Settings.canDrawOverlays(context) && prefs.getBoolean("enabled", false)

    var enabled by remember { mutableStateOf(currentEnabled()) }
    DisposableEffect(lifecycleOwner, prefs) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = currentEnabled()
        }
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "enabled") enabled = currentEnabled()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return FloatingLyricsSettingsAction(
        enabled = enabled,
        available = true,
        setEnabled = { value ->
            if (!value) {
                prefs.edit().putBoolean("enabled", false).apply()
                enabled = false
            } else if (Settings.canDrawOverlays(context)) {
                prefs.edit().putBoolean("enabled", true).apply()
                enabled = true
            } else {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("mingmusic://floating-lyrics")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        },
    )
}

@Composable
internal actual fun rememberLockscreenStageSettingsAction(): LockscreenStageSettingsAction? {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O_MR1) return null
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember(context) {
        context.getSharedPreferences("ming_immersive_lockscreen", android.content.Context.MODE_PRIVATE)
    }
    var enabled by remember(prefs) { mutableStateOf(prefs.getBoolean("enabled", false)) }
    var vendorStatus by remember(context) {
        mutableStateOf(LockscreenStageVendor.checkShowOnLockScreen(context))
    }
    var stageDiagnostic by remember(context) { mutableStateOf(LockscreenStageVendor.diagnostics(context)) }

    DisposableEffect(lifecycleOwner, prefs) {
        val refresh = {
            enabled = prefs.getBoolean("enabled", false)
            vendorStatus = LockscreenStageVendor.checkShowOnLockScreen(context)
            stageDiagnostic = LockscreenStageVendor.diagnostics(context)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "enabled" || key?.startsWith("stage_last_") == true) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    val xiaomi = LockscreenStageVendor.isXiaomiFamily()
    return LockscreenStageSettingsAction(
        enabled = enabled,
        setEnabled = { value ->
            prefs.edit().putBoolean("enabled", value).apply()
            enabled = value
        },
        vendorPermissionSummary = if (!xiaomi) null else when (vendorStatus) {
            LockscreenStageVendor.Permission.Granted -> "小米锁屏显示权限：检测为允许"
            LockscreenStageVendor.Permission.Denied -> "小米锁屏显示权限：检测为拒绝，请手动允许"
            LockscreenStageVendor.Permission.Unknown -> "小米锁屏显示权限：无法读取，请在系统设置中确认"
        },
        openVendorPermissionSettings = if (xiaomi) ({ LockscreenStageVendor.openPermissionPage(context) }) else null,
        lastStageDiagnostic = stageDiagnostic,
    )
}
