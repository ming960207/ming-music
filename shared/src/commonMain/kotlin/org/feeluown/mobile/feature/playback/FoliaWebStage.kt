package org.feeluown.mobile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Provides the offline lyric art stage on Android; other platforms use native Compose fallback. */
@Composable
internal expect fun FoliaWebStage(
    state: PlaybackState,
    lyricOffsetMs: Long,
    style: FoliaStageStyle,
    onToggleControls: () -> Unit,
    modifier: Modifier = Modifier,
): Boolean
