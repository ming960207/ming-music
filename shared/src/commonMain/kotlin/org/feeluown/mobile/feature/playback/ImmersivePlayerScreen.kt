package org.feeluown.mobile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.feeluown.mobile.playback.api.PlaybackSession
import kotlin.math.abs

/**
 * Native Compose recreation of a cinema/lyrics stage. It shares the existing PlaybackSession,
 * lyric parser, word-timing painter and transport; no WebView, new playback engine or network
 * service is involved. All playback/lyric associations survive switching back to normal view.
 */
@Composable
internal fun ImmersivePlayerScreen(
    playbackSession: PlaybackSession,
    state: PlaybackState,
    onExit: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val presentation = LocalPlaybackPresentationPort.current
    val accent = MaterialTheme.colorScheme.primary
    val image = rememberPlatformCoverImage(state.currentTrack?.coverUrl, maxSizePx = 512)
    // Keep text and native transport readable regardless of the user's light/dark preference.
    val stageColors = MaterialTheme.colorScheme.copy(
        primary = Color(0xFFB9DEFF),
        onPrimary = Color(0xFF0B1322),
        surface = Color(0xFF0B1322),
        onSurface = Color.White,
        surfaceContainer = Color(0xFF17253A),
        onSurfaceVariant = Color(0xFFBFCBDC),
        background = Color(0xFF0B1322),
        onBackground = Color.White,
    )

    MaterialTheme(colorScheme = stageColors) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B1322)),
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(72.dp)
                        .graphicsLayer { alpha = 0.25f },
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF0B1322).copy(alpha = 0.72f),
                                accent.copy(alpha = 0.25f),
                                Color(0xFF080D18).copy(alpha = 0.96f),
                            ),
                        ),
                    ),
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                val landscape = maxWidth > maxHeight
                val compactLandscape = landscape && maxHeight < 520.dp
                val compactPortrait = !landscape && maxHeight < 720.dp
                Column(modifier = Modifier.fillMaxSize()) {
                    ImmersivePlaybackHeader(
                        track = state.currentTrack,
                        onExit = onExit,
                        onOpenQueue = onOpenQueue,
                    )
                    if (landscape) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(0.42f)
                                    .widthIn(max = 420.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                if (compactLandscape) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ImmersiveAlbumCover(state, modifier = Modifier.size(72.dp))
                                        Box(modifier = Modifier.weight(1f)) {
                                            ImmersiveTrackTitle(state.currentTrack)
                                        }
                                    }
                                } else {
                                    ImmersiveAlbumCover(
                                        state,
                                        modifier = Modifier.widthIn(max = 220.dp).fillMaxWidth().aspectRatio(1f),
                                    )
                                    ImmersiveTrackTitle(state.currentTrack)
                                }
                                RuntimePlayerTransport(
                                    playbackSession = playbackSession,
                                    state = state,
                                    dense = true,
                                )
                            }
                            ImmersiveLyricsViewport(
                                state = state,
                                fontSize = presentation.lyricFontSize,
                                onReturnToMatch = onExit,
                                modifier = Modifier
                                    .weight(0.58f)
                                    .fillMaxSize(),
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ImmersiveAlbumCover(state, modifier = Modifier.size(62.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                ImmersiveTrackTitle(state.currentTrack)
                            }
                        }
                        ImmersiveLyricsViewport(
                            state = state,
                            fontSize = presentation.lyricFontSize,
                            onReturnToMatch = onExit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 14.dp),
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 24.dp, bottom = 10.dp),
                        ) {
                            RuntimePlayerTransport(
                                playbackSession = playbackSession,
                                state = state,
                                dense = compactPortrait,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImmersivePlaybackHeader(
    track: MusicTrack?,
    onExit: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExit) {
            Icon(Icons.Filled.Close, contentDescription = "退出沉浸播放")
        }
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                text = "沉浸播放",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = track?.title ?: "正在播放",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOpenQueue) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "播放队列")
        }
    }
}

@Composable
private fun ImmersiveAlbumCover(state: PlaybackState, modifier: Modifier) {
    val queue = LocalPlaybackQueueUiPort.current
    PlayerCover(
        track = state.currentTrack ?: emptyDisplayTrack(),
        transitionDirection = queue.trackChangeDirection,
        isLoading = state.status == PlayerStatus.Loading,
        cornerRadius = 18.dp,
        modifier = modifier,
    )
}

@Composable
private fun ImmersiveTrackTitle(track: MusicTrack?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = track?.title ?: "未播放",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = track?.let(::artistAlbumLabel).orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The existing lyric parser and word painter remain the only source of lyric timing truth. */
@Composable
private fun ImmersiveLyricsViewport(
    state: PlaybackState,
    fontSize: LyricFontSize,
    onReturnToMatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = LocalPlaybackPresentationPort.current
    val lyricsPort = LocalPlaybackLyricsPort.current
    val association by lyricsPort.associationState.collectAsStateWithLifecycle()
    val lyrics = remember(state.lyrics) { parseLyrics(state.lyrics) }
    val position = rememberKaraokePositionMs(
        positionMs = state.positionMs,
        isPlaying = state.status == PlayerStatus.Playing,
    )
    val offsetMs = if (association.trackId == state.currentTrack?.id) {
        association.alignmentOffsetMs
    } else {
        0L
    }
    val alignedPosition = remember(position, offsetMs) {
        derivedStateOf { (position.value - offsetMs).coerceAtLeast(0L) }
    }
    val focusedLine by remember(lyrics, alignedPosition) {
        derivedStateOf { currentLyricIndex(lyrics, alignedPosition.value) }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(focusedLine, lyrics) {
        if (focusedLine < 0) return@LaunchedEffect
        val targetIndex = focusedLine + 1 // one leading spacer item
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == targetIndex }) {
            listState.scrollToItem(targetIndex)
            withFrameNanos { }
        }
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == targetIndex }
            ?: return@LaunchedEffect
        val viewportMiddle = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
        val itemMiddle = item.offset + item.size / 2f
        val delta = itemMiddle - viewportMiddle
        if (abs(delta) > 1f) listState.animateScrollBy(delta)
    }

    BoxWithConstraints(modifier = modifier) {
        if (lyrics.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("暂无同步歌词", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "播放仍可继续，返回普通播放页可搜索或关联歌词",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onReturnToMatch) { Text("返回普通播放") }
            }
        } else {
            val activeStyle = when (fontSize) {
                LyricFontSize.Small -> MaterialTheme.typography.headlineSmall
                LyricFontSize.Medium -> MaterialTheme.typography.headlineMedium
                LyricFontSize.Large -> MaterialTheme.typography.headlineLarge
            }
            val idleStyle = when (fontSize) {
                LyricFontSize.Small -> MaterialTheme.typography.titleMedium
                LyricFontSize.Medium -> MaterialTheme.typography.titleLarge
                LyricFontSize.Large -> MaterialTheme.typography.headlineSmall
            }
            val edgeSpacer = maxHeight / 2
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "immersive-lyrics-top") { Spacer(Modifier.height(edgeSpacer)) }
                itemsIndexed(lyrics, key = { index, line -> "immersive-" + index + "-" + line.timeMs }) { index, line ->
                    val active = index == focusedLine
                    val opacity by animateFloatAsState(
                        targetValue = if (active) 1f else 0.42f,
                        animationSpec = tween(durationMillis = 400),
                        label = "immersive lyric emphasis",
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (active) 1f else 0.975f,
                        animationSpec = tween(durationMillis = 400),
                        label = "immersive lyric scale",
                    )
                    val seekTarget = immersiveLyricSeekTarget(
                        lineTimeMs = line.timeMs,
                        offsetMs = offsetMs,
                        durationMs = state.durationMs,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = opacity
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                            .clickable(enabled = seekTarget != null) {
                                seekTarget?.let(presentation::seekTo)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        line.romanization?.takeIf(String::isNotBlank)?.let { romanization ->
                            if (active && !line.romanizationWords.isNullOrEmpty()) {
                                KaraokeLyricText(
                                    words = line.romanizationWords,
                                    positionMs = alignedPosition,
                                    style = MaterialTheme.typography.bodyLarge,
                                    activeColor = Color(0xFFD3EBFF),
                                    inactiveColor = Color.White.copy(alpha = 0.32f),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Text(
                                    text = romanization,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFD3EBFF),
                                )
                            }
                        }
                        if (active && !line.words.isNullOrEmpty()) {
                            KaraokeLyricText(
                                words = line.words,
                                positionMs = alignedPosition,
                                style = activeStyle,
                                activeColor = Color.White,
                                inactiveColor = Color.White.copy(alpha = 0.30f),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(
                                text = line.text,
                                style = if (active) activeStyle else idleStyle,
                                color = Color.White,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                        line.translation?.takeIf(String::isNotBlank)?.let { translation ->
                            Text(
                                text = translation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFE0EDFF),
                            )
                        }
                    }
                }
                item(key = "immersive-lyrics-bottom") { Spacer(Modifier.height(edgeSpacer)) }
            }
        }
    }
}

/** An untimed credit line must never accidentally seek to Long.MAX_VALUE. */
internal fun immersiveLyricSeekTarget(
    lineTimeMs: Long,
    offsetMs: Long,
    durationMs: Long,
): Long? {
    if (lineTimeMs < 0L || lineTimeMs == Long.MAX_VALUE) return null
    val time = (lineTimeMs + offsetMs).coerceAtLeast(0L)
    return if (durationMs > 0L) time.coerceAtMost(durationMs) else time
}
