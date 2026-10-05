package org.feeluown.mobile

import androidx.compose.runtime.Composable

internal data class ThemeBackgroundSettingsAction(
    val customBackgroundEnabled: Boolean,
    val importBackground: () -> Unit,
    val restoreDefault: () -> Unit,
)

@Composable
internal expect fun rememberThemeBackgroundSettingsAction(): ThemeBackgroundSettingsAction?
