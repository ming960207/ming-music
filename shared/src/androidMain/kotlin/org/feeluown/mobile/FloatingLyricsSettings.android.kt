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
