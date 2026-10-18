package org.feeluown.mobile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.feeluown.mobile.playback.api.PlaybackSession
import kotlin.math.abs

/** Independent native Compose stage inspired by Folia's three-phase performance design. */
internal enum class FoliaStageStyle(val title: String) {
    Classic("经典"), Light("流光"), Poetry("浮名"), Quiet("静谧")
}

@Composable
internal fun FoliaPerformancePlayer(
    playbackSession: PlaybackSession,
    state: PlaybackState,
    onExit: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val presentation = LocalPlaybackPresentationPort.current
    val queue = LocalPlaybackQueueUiPort.current
    val accent = MaterialTheme.colorScheme.primary
    val image = rememberPlatformCoverImage(state.currentTrack?.coverUrl, maxSizePx = 512)
    var styleName by rememberSaveable { mutableStateOf(FoliaStageStyle.Classic.name) }
    val style = FoliaStageStyle.entries.firstOrNull { it.name == styleName } ?: FoliaStageStyle.Classic
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var styleMenu by remember { mutableStateOf(false) }
    val isPlaying = state.status == PlayerStatus.Playing
    FoliaSystemBarsEffect()
    LaunchedEffect(chromeVisible, styleMenu, isPlaying, styleName, state.currentTrack?.id) {
        if (chromeVisible && !styleMenu && isPlaying) {
            delay(5_000)
            chromeVisible = false
        }
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF070B15))
            .pointerInput(Unit) { detectTapGestures { chromeVisible = !chromeVisible } },
    ) {
        FoliaMovingBackdrop(image, accent, style)
        FoliaWordStage(
            state = state,
            style = style,
            accent = accent,
            onSeek = presentation::seekTo,
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 56.dp),
        )
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(230)),
            exit = fadeOut(tween(350)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xC9000000), Color.Transparent)))
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onExit) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "退出沉浸播放", tint = Color.White)
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        state.currentTrack?.title ?: "沉浸播放",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        state.currentTrack?.let(::artistAlbumLabel).orEmpty(),
                        color = Color.White.copy(alpha = 0.67f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    TextButton(onClick = { styleMenu = true }) {
                        Icon(Icons.Filled.Tune, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(3.dp))
                        Text(style.title, color = Color.White)
                    }
                    DropdownMenu(expanded = styleMenu, onDismissRequest = { styleMenu = false }) {
                        FoliaStageStyle.entries.forEach { candidate ->
                            DropdownMenuItem(text = { Text(candidate.title) }, onClick = {
                                styleName = candidate.name
                                styleMenu = false
                                chromeVisible = true
                            })
                        }
                    }
                }
                IconButton(onClick = onOpenQueue) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, "播放队列", tint = Color.White)
                }
            }
        }
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(260)),
            exit = fadeOut(tween(350)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            FoliaCompactTransport(
                playbackSession = playbackSession,
                state = state,
                onSeek = presentation::seekTo,
                onPrevious = playbackSession::previous,
                onNext = playbackSession::next,
                onToggle = playbackSession::toggle,
            )
        }
    }
}

@Composable
private fun FoliaMovingBackdrop(
    image: androidx.compose.ui.graphics.ImageBitmap?,
    accent: Color,
    style: FoliaStageStyle,
) {
    val drift by rememberInfiniteTransition(label = "slow moving aurora").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "aurora drift",
    )
    Box(Modifier.fillMaxSize()) {
        if (image != null && style != FoliaStageStyle.Poetry) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(82.dp).graphicsLayer { alpha = 0.27f },
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(
                Color(0xCE070A13), Color(0xAA0B1020), Color(0xF7070A11),
            )))
            val radius = size.maxDimension * 0.72f
            val top = Offset(size.width * (0.22f + 0.20f * drift), size.height * 0.27f)
            drawCircle(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = if (style == FoliaStageStyle.Quiet) 0.08f else 0.24f), Color.Transparent),
                    center = top, radius = radius,
                ),
                radius = radius, center = top,
            )
            val bottom = Offset(size.width * (0.82f - 0.16f * drift), size.height * 0.75f)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFF9675C9).copy(alpha = 0.20f), Color.Transparent),
                    center = bottom, radius = radius * 0.85f,
                ),
                radius = radius * 0.85f, center = bottom,
            )
            if (style == FoliaStageStyle.Light) drawFoliaMotes(drift, accent)
        }
    }
}

private fun DrawScope.drawFoliaMotes(drift: Float, accent: Color) {
    repeat(64) { index ->
        val x = ((index * 73 + 17) % 103) / 103f
        val y = ((index * 47 + 11) % 97) / 97f
        val shift = (drift - 0.5f) * ((index % 7) + 1) * 9f
        drawCircle(
            color = (if (index % 3 == 0) Color.White else accent).copy(alpha = 0.15f + (index % 4) * 0.04f),
            radius = if (index % 6 == 0) 2.4f else 1.3f,
            center = Offset(x * size.width + shift, y * size.height - shift),
        )
    }
}

@Composable
private fun FoliaWordStage(
    state: PlaybackState,
    style: FoliaStageStyle,
    accent: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
) {
    val lyricsPort = LocalPlaybackLyricsPort.current
    val association by lyricsPort.associationState.collectAsStateWithLifecycle()
    val lines = remember(state.lyrics) { parseLyrics(state.lyrics).filter { it.text.isNotBlank() } }
    val position = rememberKaraokePositionMs(
        state.positionMs,
        state.status == PlayerStatus.Playing,
    )
    val offset = if (association.trackId == state.currentTrack?.id) association.alignmentOffsetMs else 0L
    val aligned = remember(position, offset) {
        derivedStateOf { (position.value - offset).coerceAtLeast(0L) }
    }
    val current by remember(lines, aligned) {
        derivedStateOf { currentLyricIndex(lines, aligned.value) }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        if (lines.isEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("♪", color = Color.White.copy(alpha = 0.6f), fontSize = 64.sp)
                Text("聆听此刻", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                Text("暂无同步歌词", color = Color.White.copy(alpha = 0.55f))
            }
        } else {
            val index = current.coerceIn(0, lines.lastIndex)
            val active = lines[index]
            val previous = lines.getOrNull(index - 1)
            val next = lines.getOrNull(index + 1)
            val font = when {
                maxHeight < 430.dp -> 27.sp
                maxWidth < 430.dp -> 35.sp
                else -> 54.sp
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (style == FoliaStageStyle.Poetry) previous?.let { previousLine ->
                    Text(
                        previousLine.text,
                        modifier = Modifier.clickable {
                            immersiveLyricSeekTarget(previousLine.timeMs, offset, state.durationMs)?.let(onSeek)
                        },
                        color = Color.White.copy(alpha = 0.2f),
                        style = TextStyle(fontSize = font * 0.6f, lineHeight = font),
                        textAlign = TextAlign.Center,
                    )
                }
                AnimatedContent(
                    targetState = Pair(state.currentTrack?.id, index),
                    transitionSpec = {
                        (fadeIn(tween(600)) + scaleIn(tween(600), initialScale = 0.93f))
                            .togetherWith(fadeOut(tween(330)))
                    },
                    label = "Folia line scene",
                ) { identity ->
                    val line = lines.getOrNull(identity.second) ?: active
                    val nextTime = lines.getOrNull(identity.second + 1)?.timeMs
                        ?.takeIf { it != Long.MAX_VALUE } ?: line.timeMs + 4_500L
                    val tokens = remember(line, nextTime) { foliaPerformanceTokens(line, nextTime) }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FoliaAnimatedWords(
                            tokens = tokens,
                            position = aligned,
                            style = style,
                            accent = accent,
                            fontSize = font,
                        )
                        line.romanization?.takeIf(String::isNotBlank)?.let {
                            Text(it, color = Color.White.copy(alpha = 0.65f),
                                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        }
                        line.translation?.takeIf(String::isNotBlank)?.let {
                            Text(it, color = Color.White.copy(alpha = 0.76f),
                                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        }
                    }
                }
                if (style != FoliaStageStyle.Quiet) next?.let { nextLine ->
                    Text(
                        nextLine.text,
                        modifier = Modifier.clickable {
                            immersiveLyricSeekTarget(nextLine.timeMs, offset, state.durationMs)?.let(onSeek)
                        },
                        color = Color.White.copy(alpha = 0.28f),
                        style = TextStyle(fontSize = if (maxHeight < 430.dp) 15.sp else 19.sp, lineHeight = 25.sp),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoliaAnimatedWords(
    tokens: List<LyricWord>,
    position: State<Long>,
    style: FoliaStageStyle,
    accent: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    val shadowColor = if (style == FoliaStageStyle.Quiet) Color.Transparent else accent.copy(alpha = 0.8f)
    val letterStyle = TextStyle(
        fontSize = fontSize,
        lineHeight = fontSize * 1.34f,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        shadow = Shadow(shadowColor, blurRadius = if (style == FoliaStageStyle.Light) 26f else 13f),
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.Center,
    ) {
        tokens.forEach { word ->
            Text(
                text = word.text,
                style = letterStyle,
                modifier = Modifier.graphicsLayer {
                    val time = position.value
                    val start = word.startMs
                    val duration = word.durationMs.coerceAtLeast(1L)
                    val motion = ((time - start).toFloat() / duration).coerceIn(0f, 1f)
                    val entered = ((time - start + 180L).toFloat() / 320f).coerceIn(0f, 1f)
                    val active = time in start..(start + duration)
                    val after = ((time - start - duration).toFloat() / 700f).coerceIn(0f, 1f)
                    alpha = when {
                        time < start -> if (style == FoliaStageStyle.Quiet) 0.25f else 0.12f + entered * 0.28f
                        active -> 0.68f + motion * 0.32f
                        else -> 1f - after * 0.48f
                    }
                    translationY = if (style == FoliaStageStyle.Quiet) 0f else (1f - entered) * 12f
                    scaleX = if (style == FoliaStageStyle.Quiet) 1f else 0.94f + entered * 0.06f
                    scaleY = scaleX
                },
            )
        }
    }
}

/**
 * Input timestamps remain in the source track's frame. For LRC, schedule readable
 * graphemes evenly until the next line; real YRC/QRC word timings always take precedence.
 */
internal fun foliaPerformanceTokens(line: LyricLine, nextStartMs: Long): List<LyricWord> {
    if (!line.words.isNullOrEmpty()) return line.words
    val text = line.text
    if (text.isBlank()) return emptyList()
    val codeUnits = text.map(Char::toString)
    val count = codeUnits.size
    val gap = (nextStartMs - line.timeMs).coerceIn(500L, 6_000L)
    val per = (gap / count).coerceAtLeast(1L)
    return codeUnits.mapIndexed { index, s ->
        LyricWord(line.timeMs + index * per, per, s)
    }
}

@Composable
private fun FoliaCompactTransport(
    playbackSession: PlaybackSession,
    state: PlaybackState,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggle: () -> Unit,
) {
    var seeking by remember(state.currentTrack?.id) { mutableStateOf(false) }
    var seekFraction by remember(state.currentTrack?.id) { mutableFloatStateOf(0f) }
    val position = rememberKaraokePositionMs(
        positionMs = state.positionMs,
        isPlaying = state.status == PlayerStatus.Playing,
    )
    val fraction = if (seeking) seekFraction else {
        if (state.durationMs > 0L) (position.value.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    }
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE8000000))))
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Slider(
            value = fraction,
            onValueChange = { value -> seeking = true; seekFraction = value },
            onValueChangeFinished = {
                if (seeking && state.durationMs > 0L) onSeek((seekFraction * state.durationMs).toLong())
                seeking = false
            },
            enabled = state.durationMs > 0L,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(foliaTimeLabel((fraction * state.durationMs).toLong()),
                color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
            Text(foliaTimeLabel(state.durationMs),
                color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.Filled.SkipPrevious, "上一首", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(30.dp))
            IconButton(onClick = onToggle, modifier = Modifier.size(64.dp)) {
                Icon(
                    if (state.status == PlayerStatus.Playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (state.status == PlayerStatus.Playing) "暂停" else "播放",
                    tint = Color.White, modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.width(30.dp))
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.SkipNext, "下一首", tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

internal fun foliaTimeLabel(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1_000L
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
