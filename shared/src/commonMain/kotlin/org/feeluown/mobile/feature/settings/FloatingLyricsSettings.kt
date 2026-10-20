package org.feeluown.mobile

import androidx.compose.runtime.Composable

internal data class FloatingLyricsSettingsAction(
    val enabled: Boolean,
    val available: Boolean,
    val setEnabled: (Boolean) -> Unit,
)

@Composable
internal expect fun rememberFloatingLyricsSettingsAction(): FloatingLyricsSettingsAction?

/** Opt-in: keep the foreground immersive player visible when the device is locked. */
internal data class LockscreenStageSettingsAction(
    val enabled: Boolean,
    val setEnabled: (Boolean) -> Unit,
    val vendorPermissionSummary: String? = null,
    val openVendorPermissionSettings: (() -> Unit)? = null,
    val lastStageDiagnostic: String? = null,
)

@Composable
internal expect fun rememberLockscreenStageSettingsAction(): LockscreenStageSettingsAction?
