package org.feeluown.mobile

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
internal actual fun rememberThemeBackgroundSettingsAction(): ThemeBackgroundSettingsAction? {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ming_theme_background", Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(File(context.filesDir, "ming_theme_background").exists()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    File(context.filesDir, "ming_theme_background").outputStream().use(input::copyTo)
                }
                prefs.edit().putBoolean("custom_enabled", true).apply()
                enabled = true
            }
        }
    }
    return ThemeBackgroundSettingsAction(
        customBackgroundEnabled = enabled,
        importBackground = { launcher.launch(arrayOf("image/jpeg", "image/png", "image/webp")) },
        restoreDefault = {
            File(context.filesDir, "ming_theme_background").delete()
            prefs.edit().clear().apply()
            enabled = false
        },
    )
}
