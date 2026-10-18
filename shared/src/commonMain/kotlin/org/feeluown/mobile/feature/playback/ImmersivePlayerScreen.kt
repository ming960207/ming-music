package org.feeluown.mobile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.feeluown.mobile.playback.api.PlaybackSession
import kotlin.math.abs

/** The stage is a presentation of the existing session, parser and lyric association. */
@Composable
internal fun ImmersivePlayerScreen(
    playbackSession: PlaybackSession,
    state: PlaybackState,
    style: ImmersiveStageStyle,
    onStyleChange: (ImmersiveStageStyle) -> Unit,
    onExit: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val presentation = LocalPlaybackPresentationPort.current
    FoliaSystemBarsEffect()
    val association by LocalPlaybackLyricsPort.current.associationState.collectAsStateWithLifecycle()
    val lyrics = remember(state.lyrics) { parseLyrics(state.lyrics) }
    val position = key(state.currentTrack?.id) {
        rememberKaraokePositionMs(state.positionMs, state.status == PlayerStatus.Playing)
    }
    val offset = association.alignmentOffsetMs.takeIf { association.trackId == state.currentTrack?.id } ?: 0L
    val alignedPosition = remember(position, offset) { derivedStateOf { (position.value - offset).coerceAtLeast(0L) } }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var extrasVisible by rememberSaveable { mutableStateOf(true) }
    var stageMotion by rememberSaveable { mutableStateOf(true) }
    var settingsOpen by remember { mutableStateOf(false) }
    var interacting by remember { mutableStateOf(false) }
    LaunchedEffect(chromeVisible, settingsOpen, interacting, style, state.status, state.currentTrack?.id) {
        if (!chromeVisible) interacting = false
        if (chromeVisible && !settingsOpen && !interacting && state.status == PlayerStatus.Playing) {
            delay(5_000)
            chromeVisible = false
        }
    }
    val motionEnabled = stageMotion && !presentation.waveformAnimationDisabled
    val clock = rememberStageClock(state.status == PlayerStatus.Playing, motionEnabled)
    val image = rememberPlatformCoverImage(state.currentTrack?.coverUrl, maxSizePx = 512)
    val accent = rememberStageAccent(image, MaterialTheme.colorScheme.primary)
    val colors = MaterialTheme.colorScheme.copy(
        primary = accent, onPrimary = Color(0xFF10131B),
        surface = Color(0xFF10131B), onSurface = Color.White,
        surfaceContainer = Color(0xFF1B2330), onSurfaceVariant = Color(0xFFBFC9D6),
        background = Color(0xFF080B12), onBackground = Color.White,
    )
    MaterialTheme(colorScheme = colors) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { chromeVisible = !chromeVisible } }) {
            if (style.foliaStyle != null) FoliaMovingBackdrop(image, accent, style.foliaStyle, clock)
            else ImmersiveStageBackdrop(image, accent, clock, style)
            BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                val landscape = maxWidth > maxHeight
                val stageHeight = maxHeight
                Column(Modifier.fillMaxSize().padding(horizontal = if (landscape) 24.dp else 12.dp)) {
                    AnimatedVisibility(chromeVisible) {
                    if (landscape) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { StageHeader(style, chromeVisible, onExit, onOpenQueue, { chromeVisible = !chromeVisible }) }
                        Box(Modifier.weight(1f)) { StageStyleSelector(style, onStyleChange, extrasVisible, { extrasVisible = !extrasVisible },
                            motionEnabled, { stageMotion = !stageMotion }, { settingsOpen = it }) }
                    } else {
                        StageHeader(style, chromeVisible, onExit, onOpenQueue, { chromeVisible = !chromeVisible })
                        StageStyleSelector(style, onStyleChange, extrasVisible, { extrasVisible = !extrasVisible },
                            motionEnabled, { stageMotion = !stageMotion }, { settingsOpen = it })
                    }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            lyrics.isEmpty() -> Column(Modifier.fillMaxSize()) {
                                StageTrackStrip(state, compact = false)
                                StageNoLyrics(onExit, Modifier.weight(1f))
                            }
                            style.foliaStyle != null -> FoliaWordStage(lyrics, alignedPosition, state.currentTrack?.id,
                                style.foliaStyle, accent, motionEnabled, extrasVisible, presentation.lyricFontSize,
                                onSeek = { time -> immersiveLyricSeekTarget(time, offset, state.durationMs)?.let(presentation::seekTo) },
                                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp))
                            style == ImmersiveStageStyle.Focus -> Column(Modifier.fillMaxSize()) {
                                if (chromeVisible && !landscape) StageTrackStrip(state, compact = true)
                                StageFocusLyrics(lyrics, alignedPosition, extrasVisible, landscape,
                                    presentation.lyricFontSize, accent, motionEnabled, Modifier.weight(1f),
                                    onSeek = { time -> immersiveLyricSeekTarget(time, offset, state.durationMs)?.let(presentation::seekTo) })
                            }
                            landscape -> Row(Modifier.fillMaxSize().padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                                Column(Modifier.weight(0.40f), horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                                        val size = minOf(maxWidth, maxHeight, 360.dp)
                                        StageCover(state, image, accent, clock, style, Modifier.size(size).align(Alignment.Center))
                                    }
                                    if (chromeVisible) StageTrackTitle(state.currentTrack, centered = true)
                                }
                                StageFlowLyrics(lyrics, alignedPosition, extrasVisible, offset, state.durationMs,
                                    presentation.lyricFontSize, accent, motionEnabled, state.currentTrack?.id,
                                    presentation::seekTo, Modifier.weight(0.60f).fillMaxHeight())
                            }
                            else -> Column(Modifier.fillMaxSize()) {
                                if (style == ImmersiveStageStyle.Vinyl) {
                                    Box(Modifier.fillMaxWidth().height((stageHeight * 0.33f).coerceIn(110.dp, 240.dp)), contentAlignment = Alignment.Center) {
                                        StageCover(state, image, accent, clock, style, Modifier.fillMaxHeight().aspectRatio(1f))
                                    }
                                    if (chromeVisible) StageTrackTitle(state.currentTrack, centered = true)
                                } else if (chromeVisible) StageTrackStrip(state, compact = false)
                                StageFlowLyrics(lyrics, alignedPosition, extrasVisible, offset, state.durationMs,
                                    presentation.lyricFontSize, accent, motionEnabled, state.currentTrack?.id,
                                    presentation::seekTo, Modifier.weight(1f).fillMaxWidth())
                            }
                        }
                    }
                    AnimatedVisibility(chromeVisible) {
                        Surface(color = Color.White.copy(alpha = 0.045f), shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.widthIn(max = 740.dp).fillMaxWidth().align(Alignment.CenterHorizontally)) {
                            Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) interacting = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
                                }
                            }) {
                                RuntimePlayerTransport(playbackSession, state, dense = true)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun StageHeader(style: ImmersiveStageStyle, chromeVisible: Boolean, onExit: () -> Unit, onQueue: () -> Unit, onChrome: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onExit) { Icon(Icons.Filled.Close, "退出沉浸播放") }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text("沉浸舞台", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(style.subtitle, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.50f))
        }
        IconButton(onClick = onChrome) {
            Icon(if (chromeVisible) Icons.Filled.Fullscreen else Icons.Filled.FullscreenExit,
                if (chromeVisible) "隐藏播放控制" else "显示播放控制")
        }
        IconButton(onClick = onQueue) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "播放队列") }
    }
}

@Composable
private fun StageStyleSelector(style: ImmersiveStageStyle, onStyle: (ImmersiveStageStyle) -> Unit,
    extras: Boolean, onExtras: () -> Unit, motion: Boolean, onMotion: () -> Unit, onMenu: (Boolean) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
        ImmersiveStageStyle.entries.forEach { item ->
            TextButton(onClick = { onStyle(item) }, modifier = Modifier.widthIn(min = 68.dp).heightIn(min = 48.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.title, color = if (style == item) Color.White else Color.White.copy(alpha = 0.45f),
                        fontWeight = if (style == item) FontWeight.Bold else FontWeight.Normal)
                    Box(Modifier.size(width = 16.dp, height = 2.dp).background(
                        if (style == item) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape))
                }
            }
        }
        }
        Box {
            IconButton(onClick = { menu = true; onMenu(true) }) { Icon(Icons.Filled.MoreHoriz, "舞台设置") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false; onMenu(false) }) {
                DropdownMenuItem(text = { Text(if (extras) "隐藏译文与音译" else "显示译文与音译") }, onClick = { onExtras(); menu = false; onMenu(false) })
                DropdownMenuItem(text = { Text(if (motion) "关闭舞台动效" else "开启舞台动效") }, onClick = { onMotion(); menu = false; onMenu(false) })
            }
        }
    }
}

@Composable
private fun StageCover(state: PlaybackState, image: ImageBitmap?, accent: Color, clock: State<Float>, style: ImmersiveStageStyle, modifier: Modifier) {
    if (style == ImmersiveStageStyle.Vinyl) ImmersiveVinylCover(image, accent, clock, modifier)
    else PlayerCover(state.currentTrack ?: emptyDisplayTrack(),
        transitionDirection = LocalPlaybackQueueUiPort.current.trackChangeDirection,
        isLoading = state.status == PlayerStatus.Loading, cornerRadius = 22.dp, modifier = modifier)
}

@Composable
private fun StageTrackStrip(state: PlaybackState, compact: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        PlayerCover(state.currentTrack ?: emptyDisplayTrack(), cornerRadius = 10.dp, modifier = Modifier.size(if (compact) 38.dp else 48.dp))
        Box(Modifier.weight(1f)) { StageTrackTitle(state.currentTrack) }
    }
}

@Composable
private fun StageTrackTitle(track: MusicTrack?, centered: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(track?.title ?: "未播放", maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(track?.let(::artistAlbumLabel).orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.50f))
    }
}

@Composable
private fun StageNoLyrics(onExit: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Icon(Icons.Filled.MusicNote, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.70f))
        Text("让音乐填满这一刻", style = MaterialTheme.typography.headlineSmall)
        Text("暂无同步歌词", color = Color.White.copy(alpha = 0.50f))
        TextButton(onClick = onExit) { Text("返回搜索歌词") }
    }
}

@Composable
internal fun StageFlowLyrics(lyrics: List<LyricLine>, position: State<Long>, extras: Boolean, offset: Long, duration: Long,
    fontSize: LyricFontSize, accent: Color, motion: Boolean, trackId: String?, onSeek: (Long) -> Unit, modifier: Modifier) {
    val focused by remember(lyrics, position) { derivedStateOf { immersiveActiveLineIndex(lyrics, position.value) } }
    val list = rememberLazyListState()
    var following by remember(trackId) { mutableStateOf(true) }
    val dragged by list.interactionSource.collectIsDraggedAsState()
    val viewportHeight by remember(list) { derivedStateOf { list.layoutInfo.viewportEndOffset - list.layoutInfo.viewportStartOffset } }
    LaunchedEffect(dragged) { if (dragged) following = false }
    LaunchedEffect(following, list.isScrollInProgress) {
        if (!following && !list.isScrollInProgress) { delay(6_000); following = true }
    }
    LaunchedEffect(focused, following, lyrics, trackId, viewportHeight, motion) {
        if (!following) return@LaunchedEffect
        if (focused < 0) { list.scrollToItem(0); return@LaunchedEffect }
        val target = focused + 1
        if (list.layoutInfo.visibleItemsInfo.none { it.index == target }) {
            list.scrollToItem(target)
            withFrameNanos { }
        }
        val layout = list.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == target } ?: return@LaunchedEffect
        val anchor = (layout.viewportEndOffset - layout.viewportStartOffset) * 0.42f
        val delta = item.offset + item.size / 2f - anchor
        if (abs(delta) > 1f) {
            if (motion) list.animateScrollBy(delta, spring(dampingRatio = 0.92f, stiffness = 95f)) else list.scrollBy(delta)
        }
    }
    BoxWithConstraints(modifier) {
        val textSize = when (fontSize) { LyricFontSize.Small -> 25; LyricFontSize.Medium -> 30; LyricFontSize.Large -> 36 }.sp
        val textStyle = MaterialTheme.typography.headlineLarge.copy(fontSize = textSize, lineHeight = textSize * 1.42f,
            fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
        val spacer = maxHeight * 0.40f
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            item(key = "stage-top") { Spacer(Modifier.height(spacer)) }
            itemsIndexed(lyrics, key = { i, line -> "$i-${line.timeMs}" }) { index, line ->
                val distance = abs(index - focused)
                val active = index == focused
                val opacity by animateFloatAsState(if (active) 1f else if (distance == 1) 0.48f else 0.22f, tween(if (motion) 500 else 0), label = "stage lyric depth")
                val scale by animateFloatAsState(if (active) 1f else 0.94f,
                    if (motion) spring(dampingRatio = 0.90f, stiffness = 130f) else tween(0), label = "stage lyric emphasis")
                val target = immersiveLyricSeekTarget(line.timeMs, offset, duration)
                Column(Modifier.fillMaxWidth().graphicsLayer {
                    alpha = opacity; scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0.5f)
                }.clickable(enabled = target != null) { target?.let(onSeek); following = true }
                    .padding(horizontal = 18.dp, vertical = 16.dp)
                    .blur(if (motion && distance > 1) 0.7.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StageLyricText(line, active, position, textStyle, accent, extras)
                }
            }
            item(key = "stage-bottom") { Spacer(Modifier.height(maxHeight * 0.58f)) }
        }
        StageInterlude(lyrics, position, Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 8.dp))
        AnimatedVisibility(!following, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
            FilledTonalButton(onClick = { following = true }) { Icon(Icons.Filled.Sync, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("跟随播放") }
        }
    }
}

@Composable
private fun StageLyricText(line: LyricLine, active: Boolean, position: State<Long>, style: TextStyle, accent: Color, extras: Boolean) {
    if (extras) line.romanization?.takeIf(String::isNotBlank)?.let {
        if (active && !line.romanizationWords.isNullOrEmpty()) ImmersiveKaraokeText(line.romanizationWords, position,
            MaterialTheme.typography.bodyMedium.copy(textAlign = style.textAlign), accent, Modifier.fillMaxWidth())
        else Text(it, style = MaterialTheme.typography.bodyMedium, color = accent.copy(alpha = 0.75f), textAlign = style.textAlign,
            modifier = Modifier.fillMaxWidth())
    }
    if (active && !line.words.isNullOrEmpty()) ImmersiveKaraokeText(line.words, position, style, accent, Modifier.fillMaxWidth())
    else Text(line.text, style = style, color = Color.White, modifier = Modifier.fillMaxWidth())
    if (extras) line.translation?.takeIf(String::isNotBlank)?.let {
        Text(it, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.62f), textAlign = style.textAlign,
            modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun StageFocusLyrics(lyrics: List<LyricLine>, position: State<Long>, extras: Boolean, landscape: Boolean,
    fontSize: LyricFontSize, accent: Color, motion: Boolean, modifier: Modifier, onSeek: (Long) -> Unit) {
    val focused by remember(lyrics, position) { derivedStateOf { immersiveActiveLineIndex(lyrics, position.value) } }
    val line = lyrics.getOrNull(focused)
    val preferredSize = (if (landscape) 52 else 38) + when (fontSize) { LyricFontSize.Small -> -4; LyricFontSize.Medium -> 0; LyricFontSize.Large -> 6 }
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val compact = maxHeight < 240.dp
    val size = minOf(preferredSize.toFloat(), (maxHeight.value / 3.8f).coerceAtLeast(24f))
    Column(Modifier.fillMaxSize().padding(horizontal = if (landscape) 52.dp else 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (!compact) Text(lyrics.getOrNull(focused - 1)?.text.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.23f),
            modifier = Modifier.fillMaxWidth().clickable(enabled = focused > 0) { lyrics.getOrNull(focused - 1)?.let { onSeek(it.timeMs) } }, textAlign = TextAlign.Center)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            AnimatedContent(targetState = line, transitionSpec = {
                (fadeIn(tween(if (motion) 500 else 0)) + slideInVertically(tween(if (motion) 650 else 0)) { it / 5 }) togetherWith
                    (fadeOut(tween(if (motion) 240 else 0)) + slideOutVertically(tween(if (motion) 450 else 0)) { -it / 7 })
            }, label = "stage focused phrase") { current ->
                Column(Modifier.fillMaxWidth().clickable(enabled = current != null) { current?.let { onSeek(it.timeMs) } },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (current == null) Text("静候第一句", color = Color.White.copy(alpha = 0.35f), style = MaterialTheme.typography.headlineSmall)
                    else StageLyricText(current, true, position, MaterialTheme.typography.displaySmall.copy(
                        fontSize = size.sp, lineHeight = (size * 1.45f).sp, fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), accent, extras)
                }
            }
        }
        if (!compact) Text(lyrics.getOrNull(focused + 1)?.text.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.35f), textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(enabled = focused + 1 < lyrics.size) { lyrics.getOrNull(focused + 1)?.let { onSeek(it.timeMs) } })
        Box(Modifier.height(if (compact) 28.dp else 44.dp), contentAlignment = Alignment.Center) { StageInterlude(lyrics, position, Modifier) }
    }
    }
}

@Composable
internal fun StageInterlude(lyrics: List<LyricLine>, position: State<Long>, modifier: Modifier) {
    val seconds by remember(lyrics, position) { derivedStateOf { immersiveInterlude(lyrics, position.value)?.let { (it.remainingMs + 999) / 1_000 } } }
    if (seconds != null) Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val accent = MaterialTheme.colorScheme.primary
        Canvas(Modifier.size(width = 38.dp, height = 12.dp)) {
            val progress = immersiveInterlude(lyrics, position.value)?.progress ?: 0f
            repeat(3) { index -> drawCircle(accent.copy(alpha = if (progress * 3 >= index) 0.9f else 0.22f), radius = 3.dp.toPx(), center = Offset(5.dp.toPx() + index * 13.dp.toPx(), size.height / 2)) }
        }
        Text("间奏 · ${seconds}秒", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.52f))
    }
}
