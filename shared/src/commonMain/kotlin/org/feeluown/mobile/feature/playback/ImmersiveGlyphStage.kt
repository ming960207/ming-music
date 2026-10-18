package org.feeluown.mobile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val GLYPHS_PER_SCENE = 14

private data class GlyphScene(val line: LyricLine, val glyphs: List<LyricWord>, val seed: Int, val page: Int, val lyrics: List<LyricLine>)
private data class GlyphVisual(val scale: Float, val rotation: Float, val weight: FontWeight)

/**
 * Native glyph actors with cached scene geometry and a music-time frame driver.
 * Design references: Folia's fume/fumeStage.ts scene/driver separation and
 * lumiere/text/windowTiming.ts + windowGeometry.ts timed flight/afterglow model.
 * https://github.com/chthollyphile/folia-major/tree/main/src/components/visualizer
 */
@Composable
internal fun ImmersiveGlyphStage(
    lyrics: List<LyricLine>, position: State<Long>, trackId: String?,
    accent: Color, fontSize: LyricFontSize, motion: Boolean, extras: Boolean,
    onSeek: (Long) -> Unit, onBlankTap: () -> Unit, modifier: Modifier,
) {
    val cuePosition = remember(position) { derivedStateOf {
        val time = position.value
        if (time <= Long.MAX_VALUE - 700) time + 700 else Long.MAX_VALUE
    } }
    val current by remember(lyrics, cuePosition) { derivedStateOf { immersiveActiveLineIndex(lyrics, cuePosition.value) } }
    val index = if (current >= 0) current else lyrics.indexOfFirst { it.timeMs >= 0 && it.timeMs != Long.MAX_VALUE }
    val line = lyrics.getOrNull(index)
    if (line == null) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("聆听此刻", color = Color.White.copy(alpha = 0.65f)) }
        return
    }
    val next = lyrics.getOrNull(index + 1)?.timeMs?.takeIf { it != Long.MAX_VALUE } ?: line.timeMs + 5_000
    val tokens = remember(line, next) { stageGlyphTokens(line, next) }
    val page by remember(tokens, cuePosition) { derivedStateOf { stageGlyphPageAt(tokens, cuePosition.value, GLYPHS_PER_SCENE) } }
    val scene = remember(trackId, line, page, tokens) {
        GlyphScene(line, tokens.drop(page * GLYPHS_PER_SCENE).take(GLYPHS_PER_SCENE),
            "$trackId|${line.timeMs}|${line.text}|$page".hashCode(), page, lyrics)
    }
    AnimatedContent(targetState = scene, modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(if (motion) 520 else 0)) + scaleIn(tween(if (motion) 700 else 0), initialScale = 1.025f)) togetherWith
                (fadeOut(tween(if (motion) 700 else 0)) + scaleOut(tween(if (motion) 700 else 0), targetScale = 0.96f))
        }, label = "scattered glyph scene") { frozenScene ->
        GlyphSceneCanvas(frozenScene, position, accent, fontSize, motion, extras, onSeek, onBlankTap)
    }
}

@Composable
private fun GlyphSceneCanvas(scene: GlyphScene, position: State<Long>, accent: Color,
    fontSize: LyricFontSize, motion: Boolean, extras: Boolean, onSeek: (Long) -> Unit, onBlankTap: () -> Unit) {
    val latestSeek by rememberUpdatedState(onSeek)
    val latestBlankTap by rememberUpdatedState(onBlankTap)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var captionHeightPx by remember(scene, extras) { mutableIntStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val captionHeight = if (captionHeightPx > 0) with(density) { captionHeightPx.toDp() } + 16.dp else if (extras) 92.dp else 54.dp
        val glyphAreaHeight = (maxHeight - captionHeight).coerceAtLeast(1.dp)
        val baseFont = (sqrt(maxWidth.value * glyphAreaHeight.value / scene.glyphs.size.coerceAtLeast(1)) * 0.55f).coerceIn(28f, 84f) *
            when (fontSize) { LyricFontSize.Small -> 0.88f; LyricFontSize.Medium -> 1f; LyricFontSize.Large -> 1.15f }
        val visuals = remember(scene.seed, scene.glyphs.size) {
            val random = Random(scene.seed xor 0x46E63D)
            scene.glyphs.mapIndexed { index, _ ->
                GlyphVisual(0.78f + random.nextFloat() * 0.50f, -12f + random.nextFloat() * 24f,
                    if (index % 3 == 0) FontWeight.Black else FontWeight.SemiBold)
            }
        }
        val layouts = remember(scene.glyphs, visuals, baseFont, measurer) {
            scene.glyphs.mapIndexed { index, glyph ->
                measurer.measure(glyph.text, TextStyle(fontSize = (baseFont * visuals[index].scale).sp,
                    fontFamily = FontFamily.Serif, fontWeight = visuals[index].weight), softWrap = false, maxLines = 1)
            }
        }
        val areaWidthPx = constraints.maxWidth.toFloat()
        val areaHeightPx = with(density) { glyphAreaHeight.toPx() }
        val placements = remember(layouts, visuals, areaWidthPx, areaHeightPx, scene.seed) {
            layoutStageGlyphs(layouts.mapIndexed { i, layout ->
                StageGlyphMetric(layout.size.width.toFloat(), layout.size.height.toFloat(), visuals[i].rotation)
            }, areaWidthPx, areaHeightPx, scene.seed)
        }
        val flights = remember(placements, areaWidthPx, areaHeightPx, scene.seed) {
            placements.mapIndexed { index, landing -> stageGlyphFlight(landing, areaWidthPx, areaHeightPx, scene.seed, index) }
        }
        Canvas(Modifier.fillMaxWidth().height(glyphAreaHeight).clipToBounds().pointerInput(scene, placements) {
            detectTapGestures { tap ->
                val index = placements.indexOfFirst { tap.x in it.left..it.right && tap.y in it.top..it.bottom }
                if (index >= 0) scene.glyphs.getOrNull(index)?.let { latestSeek(it.startMs) } else latestBlankTap()
            }
        }) {
            val time = position.value
            val phase = if (motion) (time / 1_000.0).toFloat() else 0f
            drawCircle(accent.copy(alpha = 0.045f), radius = size.maxDimension * 0.48f,
                center = Offset(size.width * 0.22f, size.height * 0.50f), style = Stroke(0.7.dp.toPx()))
            if (motion) flights.forEachIndexed { index, flight ->
                val glyph = scene.glyphs[index]
                val head = stageGlyphEntryProgress(glyph, time, flight)
                val tail = stageGlyphEntryProgress(glyph, (time - 280).coerceAtLeast(0), flight)
                val fade = (1f - ((time.toDouble() - glyph.startMs).coerceAtLeast(0.0) / 320).toFloat()).coerceIn(0f, 1f)
                if (head > tail + 0.002f && fade > 0f) {
                    var previous = stageGlyphFlightPoint(flight, tail)
                    repeat(12) { sample ->
                        val next = stageGlyphFlightPoint(flight, tail + (head - tail) * (sample + 1) / 12)
                        drawLine(accent.copy(alpha = (sample + 1) / 12f * fade * 0.16f), Offset(previous.x, previous.y),
                            Offset(next.x, next.y), strokeWidth = 0.8.dp.toPx(), cap = StrokeCap.Round)
                        previous = next
                    }
                }
            }
            // Echoes stay behind the currently sung glyphs, preserving the active reading plane.
            repeat(2) { layer -> placements.forEachIndexed glyph@ { index, placement ->
                val glyph = scene.glyphs[index]
                val layout = layouts[index]
                val age = time.toDouble() - glyph.startMs
                val flight = flights[index]
                val entered = if (motion) stageGlyphEntryProgress(glyph, time, flight) else 1f
                val point = if (motion) stageGlyphFlightPoint(flight, entered) else flight.to
                val progress = immersiveWordProgress(glyph, time)
                val active = age >= 0 && age < glyph.durationMs.coerceAtLeast(160)
                if (active != (layer == 1)) return@glyph
                val after = ((age - glyph.durationMs - 350) / 1_800).toFloat().coerceIn(0f, 1f)
                val scatter = after * sqrt(after)
                val opacity = when {
                    age < 0 -> (0.14f + entered * 0.16f) * entered
                    active -> 0.72f + entered * 0.28f
                    else -> 0.74f - after * 0.50f
                }
                val pulse = if (active) sin(progress * PI).toFloat().coerceAtLeast(0f) else 0f
                val scale = placement.scale * if (motion) (0.90f + entered * 0.10f + pulse * 0.12f) else 1f
                val driftX = if (motion) sin(phase * 0.45f + index * 1.7f) * 1.8.dp.toPx() +
                    sin(index * 2.4f) * scatter * 7.dp.toPx() else 0f
                val driftY = if (motion) sin(phase * 0.35f + index * 2.1f) * 1.5.dp.toPx() +
                    sin(index * 1.3f) * scatter * 7.dp.toPx() else 0f
                val color = (if (active) Color.White else lerp(accent, Color(0xFFFFE1B6), index % 3 * 0.12f)).copy(alpha = opacity)
                withTransform({
                    translate(point.x + driftX, point.y + driftY)
                    rotate(placement.rotation + if (motion) (1f - entered) * 24f else 0f, pivot = Offset.Zero)
                    scale(scale, scale, pivot = Offset.Zero)
                }) {
                    drawText(layout, color = color, topLeft = Offset(-layout.size.width / 2f, -layout.size.height / 2f),
                        shadow = Shadow(accent.copy(alpha = if (active) 0.55f else 0.10f), blurRadius = (if (active) 16.dp else 4.dp).toPx()))
                }
            } }
        }
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 12.dp)
            .onSizeChanged { captionHeightPx = it.height }
            .clickable { latestSeek(scene.line.timeMs) }, horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(scene.line.text, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.56f),
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            if (extras) {
                scene.line.romanization?.takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = accent.copy(alpha = 0.6f), textAlign = TextAlign.Center,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                scene.line.translation?.takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.40f), textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            StageInterlude(scene.lyrics, position, Modifier)
        }
    }
}
