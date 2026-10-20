package org.feeluown.mobile

import androidx.compose.runtime.Composable

/** Enter a dedicated show-when-locked Activity *while this stage is foreground*. */
@Composable
internal expect fun OpenLockscreenStageWhenEnabled(enabled: Boolean)
