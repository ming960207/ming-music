package org.feeluown.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Standalone Keyguard-safe presentation of the existing FuoPlaybackService state.
 * No second player is created; time, seek and transport all belong to the playback service.
 */
@Composable
fun LockscreenFoliaPlayer(
    state: PlaybackState,
    alignmentOffsetMs: Long = 0L,
    fontSize: LyricFontSize = LyricFontSize.Medium,
    onClose: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
) {
    val lines = remember(state.lyrics) { parseLyrics(state.lyrics) }
    val playing = state.status == PlayerStatus.Playing
    val position = key(state.currentTrack?.id) {
        rememberKaraokePositionMs(state.positionMs, playing)
    }
    val alignedPosition = remember(position, alignmentOffsetMs) {
        derivedStateOf { (position.value - alignmentOffsetMs).coerceAtLeast(0L) }
    }
    val cover = rememberPlatformCoverImage(state.currentTrack?.coverUrl, maxSizePx = 512)
    val accent = rememberStageAccent(cover, Color(0xFFE2ECCD))
    val clock = rememberStageClock(playing, true)
    var controlsVisible by remember { mutableStateOf(true) }

    MaterialTheme(colorScheme = darkColorScheme(primary = accent)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF080B12))) {
            val foliaStyle = ImmersiveStageStyle.Glyph.foliaStyle
            if (foliaStyle != null) FoliaMovingBackdrop(cover, accent, foliaStyle, clock)
            else ImmersiveStageBackdrop(cover, accent, clock, ImmersiveStageStyle.Glyph)
            if (lines.isNotEmpty()) {
                ImmersiveGlyphStage(
                    lyrics = lines,
                    position = alignedPosition,
                    trackId = state.currentTrack?.id,
                    accent = accent,
                    fontSize = fontSize,
                    motion = true,
                    extras = true,
                    playing = playing,
                    onSeek = { value ->
                        immersiveLyricSeekTarget(value, alignmentOffsetMs, state.durationMs)?.let(onSeek)
                    },
                    onBlankTap = { controlsVisible = !controlsVisible },
                    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 110.dp),
                )
            } else {
                Text(
                    text = if (state.currentTrack == null) "请先播放音乐"
                        else "当前歌曲暂未获取到同步歌词，请返回播放器搜索或关联歌词",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )
            }
            if (controlsVisible) {
                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("锁屏沉浸舞台", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        Text(state.currentTrack?.title.orEmpty(), color = Color.White,
                            maxLines = 1, style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭锁屏舞台", tint = Color.White)
                    }
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                        .padding(bottom = 30.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playing) "暂停" else "播放",
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}
