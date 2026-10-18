package org.feeluown.mobile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

/** Existing Folia text styles share the same stage shell, clock and playback controls. */
internal enum class FoliaStageStyle(val title: String) {
    Classic("经典"), Light("流光"), Poetry("浮名"), Quiet("静谧")
}

@Composable
internal fun FoliaMovingBackdrop(image: ImageBitmap?, accent: Color, style: FoliaStageStyle, clock: State<Float>) {
    if (image != null && style != FoliaStageStyle.Poetry) Image(image, contentDescription = null,
        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(64.dp).graphicsLayer { alpha = 0.27f })
    Canvas(Modifier.fillMaxSize()) {
        val drift = (sin(clock.value / 16f) + 1f) * 0.5f
        drawRect(Brush.verticalGradient(listOf(Color(0xCE070A13), Color(0xAA0B1020), Color(0xF7070A11))))
        val radius = size.maxDimension * 0.72f
        val top = Offset(size.width * (0.22f + 0.20f * drift), size.height * 0.27f)
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = if (style == FoliaStageStyle.Quiet) 0.08f else 0.24f),
            Color.Transparent), center = top, radius = radius), radius, top)
        val bottom = Offset(size.width * (0.82f - 0.16f * drift), size.height * 0.75f)
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.18f), Color.Transparent),
            center = bottom, radius = radius * 0.85f), radius * 0.85f, bottom)
        if (style == FoliaStageStyle.Light) drawFoliaMotes(drift, accent)
    }
}

private fun DrawScope.drawFoliaMotes(drift: Float, accent: Color) {
    repeat(64) { index ->
        val shift = (drift - 0.5f) * ((index % 7) + 1) * 9f
        drawCircle((if (index % 3 == 0) Color.White else accent).copy(alpha = 0.15f + (index % 4) * 0.04f),
            radius = if (index % 6 == 0) 2.4f else 1.3f,
            center = Offset(((index * 73 + 17) % 103) / 103f * size.width + shift,
                ((index * 47 + 11) % 97) / 97f * size.height - shift))
    }
}

@Composable
internal fun FoliaWordStage(lines: List<LyricLine>, position: State<Long>, trackId: String?, style: FoliaStageStyle,
    accent: Color, motion: Boolean, extras: Boolean, fontSize: LyricFontSize, onSeek: (Long) -> Unit, modifier: Modifier) {
    val current by remember(lines, position) { derivedStateOf { immersiveActiveLineIndex(lines, position.value) } }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        if (current < 0) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("静候第一句", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.headlineSmall)
                StageInterlude(lines, position, Modifier)
            }
        } else {
            val compact = maxHeight < 280.dp
            val font = ((if (compact) 27 else if (maxWidth < 430.dp) 35 else 54) +
                when (fontSize) { LyricFontSize.Small -> -3; LyricFontSize.Medium -> 0; LyricFontSize.Large -> 5 }).sp
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 24.dp)) {
                if (!compact && style == FoliaStageStyle.Poetry) lines.getOrNull(current - 1)?.let { previous ->
                    Text(previous.text, modifier = Modifier.clickable { onSeek(previous.timeMs) },
                        color = Color.White.copy(alpha = 0.20f), textAlign = TextAlign.Center,
                        style = TextStyle(fontSize = font * 0.6f, lineHeight = font))
                }
                AnimatedContent(targetState = trackId to current, transitionSpec = {
                    (fadeIn(tween(if (motion) 600 else 0)) + scaleIn(tween(if (motion) 600 else 0), initialScale = 0.93f))
                        .togetherWith(fadeOut(tween(if (motion) 330 else 0)))
                }, label = "Folia line scene") { identity ->
                    val line = lines.getOrNull(identity.second)
                    if (line != null) {
                        val nextTime = lines.getOrNull(identity.second + 1)?.timeMs?.takeIf { it != Long.MAX_VALUE }
                            ?: (line.timeMs + 4_500L)
                        val tokens = remember(line, nextTime) { foliaPerformanceTokens(line, nextTime) }
                        Column(Modifier.fillMaxWidth().clickable { onSeek(line.timeMs) },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            FoliaAnimatedWords(tokens, position, style, accent, font, motion)
                            if (extras) {
                                line.romanization?.takeIf(String::isNotBlank)?.let {
                                    Text(it, color = Color.White.copy(alpha = 0.65f), textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                                line.translation?.takeIf(String::isNotBlank)?.let {
                                    Text(it, color = Color.White.copy(alpha = 0.76f), textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
                if (!compact && style != FoliaStageStyle.Quiet) lines.getOrNull(current + 1)?.let { next ->
                    Text(next.text, modifier = Modifier.clickable { onSeek(next.timeMs) },
                        color = Color.White.copy(alpha = 0.28f), textAlign = TextAlign.Center,
                        style = TextStyle(fontSize = 19.sp, lineHeight = 25.sp), maxLines = 2)
                }
                StageInterlude(lines, position, Modifier)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoliaAnimatedWords(tokens: List<LyricWord>, position: State<Long>, style: FoliaStageStyle, accent: Color,
    fontSize: androidx.compose.ui.unit.TextUnit, motionEnabled: Boolean) {
    val letterStyle = TextStyle(fontSize = fontSize, lineHeight = fontSize * 1.34f, fontWeight = FontWeight.SemiBold,
        color = Color.White, shadow = Shadow(if (style == FoliaStageStyle.Quiet) Color.Transparent else accent.copy(alpha = 0.8f),
            blurRadius = if (style == FoliaStageStyle.Light) 26f else 13f))
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.Center) {
        tokens.forEach { word ->
            Text(word.text, style = letterStyle, modifier = Modifier.graphicsLayer {
                val time = position.value
                val duration = word.durationMs.coerceAtLeast(1L)
                val progress = immersiveWordProgress(word, time)
                val entered = ((time - word.startMs + 180L).toFloat() / 320f).coerceIn(0f, 1f)
                val active = time >= word.startMs && time - word.startMs <= duration
                val after = ((time - word.startMs - duration).toFloat() / 700f).coerceIn(0f, 1f)
                alpha = when {
                    time < word.startMs -> if (style == FoliaStageStyle.Quiet) 0.25f else 0.12f + entered * 0.28f
                    active -> 0.68f + progress * 0.32f
                    else -> 1f - after * 0.48f
                }
                translationY = if (!motionEnabled || style == FoliaStageStyle.Quiet) 0f else (1f - entered) * 12f
                scaleX = if (!motionEnabled || style == FoliaStageStyle.Quiet) 1f else 0.94f + entered * 0.06f
                scaleY = scaleX
            })
        }
    }
}

/** Preserve the existing line-level fallback animation; real word timestamps take priority. */
internal fun foliaPerformanceTokens(line: LyricLine, nextStartMs: Long): List<LyricWord> {
    if (!line.words.isNullOrEmpty()) return line.words
    if (line.timeMs == Long.MAX_VALUE) return listOf(LyricWord(0L, 1L, line.text))
    if (line.text.isBlank()) return emptyList()
    val codeUnits = line.text.map(Char::toString)
    val gap = (nextStartMs - line.timeMs).coerceIn(500L, 6_000L)
    val per = (gap / codeUnits.size).coerceAtLeast(1L)
    return codeUnits.mapIndexed { index, text -> LyricWord(line.timeMs + index * per, per, text) }
}

internal fun foliaTimeLabel(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1_000L
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
