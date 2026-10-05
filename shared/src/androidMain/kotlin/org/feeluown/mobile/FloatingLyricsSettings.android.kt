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
        context.getSharedPreferences(FloatingLyricsPermissionActivity.PREFS, android.content.Context.MODE_PRIVATE)
    }
    fun currentEnabled(): Boolean =
        Settings.canDrawOverlays(context) && prefs.getBoolean(FloatingLyricsPermissionActivity.KEY_ENABLED, false)

    var enabled by remember { mutableStateOf(currentEnabled()) }
    DisposableEffect(lifecycleOwner, prefs) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = currentEnabled()
        }
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == FloatingLyricsPermissionActivity.KEY_ENABLED) enabled = currentEnabled()
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
                prefs.edit().putBoolean(FloatingLyricsPermissionActivity.KEY_ENABLED, false).apply()
                enabled = false
            } else if (Settings.canDrawOverlays(context)) {
                prefs.edit().putBoolean(FloatingLyricsPermissionActivity.KEY_ENABLED, true).apply()
                enabled = true
            } else {
                context.startActivity(Intent(context, FloatingLyricsPermissionActivity::class.java).apply {
                    data = Uri.parse("mingmusic://floating-lyrics")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        },
    )
}
