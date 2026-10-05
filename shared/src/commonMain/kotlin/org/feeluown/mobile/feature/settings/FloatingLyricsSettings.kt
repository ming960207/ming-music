package org.feeluown.mobile

import androidx.compose.runtime.Composable

internal data class FloatingLyricsSettingsAction(
    val enabled: Boolean,
    val available: Boolean,
    val setEnabled: (Boolean) -> Unit,
)

@Composable
internal expect fun rememberFloatingLyricsSettingsAction(): FloatingLyricsSettingsAction?
