package org.feeluown.mobile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun FoliaWebStage(
    state: PlaybackState,
    lyricOffsetMs: Long,
    style: FoliaStageStyle,
    onToggleControls: () -> Unit,
    modifier: Modifier,
): Boolean = false
