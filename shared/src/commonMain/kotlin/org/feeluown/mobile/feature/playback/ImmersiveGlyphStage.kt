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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
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

private data class GlyphScene(val line: LyricLine, val glyphs: List<LyricWord>, val seed: Int, val page: Int, val nextLine: LyricLine?)
private data class GlyphVisual(val scale: Float, val rotation: Float, val weight: FontWeight)

/** A canvas of independent glyph actors, never a Row/FlowRow of the lyric sentence. */
@Composable
internal fun ImmersiveGlyphStage(
    lyrics: List<LyricLine>, position: State<Long>, clock: State<Float>, trackId: String?,
    accent: Color, fontSize: LyricFontSize, motion: Boolean, extras: Boolean,
    onSeek: (Long) -> Unit, onBlankTap: () -> Unit, modifier: Modifier,
) {
    val current by remember(lyrics, position) { derivedStateOf { immersiveActiveLineIndex(lyrics, position.value) } }
    val index = if (current >= 0) current else lyrics.indexOfFirst { it.timeMs >= 0 && it.timeMs != Long.MAX_VALUE }
    val line = lyrics.getOrNull(index)
    if (line == null) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("聆听此刻", color = Color.White.copy(alpha = 0.65f)) }
        return
    }
    val next = lyrics.getOrNull(index + 1)?.timeMs?.takeIf { it != Long.MAX_VALUE } ?: line.timeMs + 5_000
    val tokens = remember(line, next) { stageGlyphTokens(line, next) }
    val page by remember(tokens, position) { derivedStateOf { stageGlyphPageAt(tokens, position.value, GLYPHS_PER_SCENE) } }
    val scene = remember(trackId, line, page, tokens) {
        GlyphScene(line, tokens.drop(page * GLYPHS_PER_SCENE).take(GLYPHS_PER_SCENE),
            "$trackId|${line.timeMs}|${line.text}|$page".hashCode(), page, lyrics.getOrNull(index + 1))
    }
    AnimatedContent(targetState = scene, modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(if (motion) 520 else 0)) + scaleIn(tween(if (motion) 700 else 0), initialScale = 1.025f)) togetherWith
                (fadeOut(tween(if (motion) 350 else 0)) + scaleOut(tween(if (motion) 650 else 0), targetScale = 0.96f))
        }, label = "scattered glyph scene") { frozenScene ->
        GlyphSceneCanvas(frozenScene, position, clock, accent, fontSize, motion, extras, onSeek, onBlankTap)
    }
}

@Composable
private fun GlyphSceneCanvas(scene: GlyphScene, position: State<Long>, clock: State<Float>, accent: Color,
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
        val trails = remember(placements) {
            placements.zipWithNext().map { (a, b) -> Path().apply {
                moveTo(a.centerX, a.centerY)
                quadraticTo((a.centerX + b.centerX) / 2, minOf(a.centerY, b.centerY) - 24,
                    b.centerX, b.centerY)
            } }
        }
        Canvas(Modifier.fillMaxWidth().height(glyphAreaHeight).pointerInput(scene, placements) {
            detectTapGestures { tap ->
                val index = placements.indexOfFirst { tap.x in it.left..it.right && tap.y in it.top..it.bottom }
                if (index >= 0) scene.glyphs.getOrNull(index)?.let { latestSeek(it.startMs) } else latestBlankTap()
            }
        }) {
            val time = position.value
            val phase = if (motion) clock.value else 0f
            drawCircle(accent.copy(alpha = 0.045f), radius = size.maxDimension * 0.48f,
                center = Offset(size.width * 0.22f, size.height * 0.50f), style = Stroke(0.7.dp.toPx()))
            trails.forEachIndexed { index, path ->
                val age = time.toDouble() - scene.glyphs[index + 1].startMs
                if (motion && age in -160.0..650.0) {
                    val flight = ((age + 160) / 810).toFloat().coerceIn(0f, 1f)
                    drawPath(path, accent.copy(alpha = sin(flight * PI).toFloat() * 0.14f), style = Stroke(0.8.dp.toPx()))
                }
            }
            placements.forEachIndexed { index, placement ->
                val glyph = scene.glyphs[index]
                val layout = layouts[index]
                val age = time.toDouble() - glyph.startMs
                val rawEnter = ((age + 160) / 450).toFloat().coerceIn(0f, 1f)
                val inverse = 1f - rawEnter
                val entered = 1f - inverse * inverse * inverse
                val progress = immersiveWordProgress(glyph, time)
                val active = age >= 0 && age < glyph.durationMs.coerceAtLeast(160)
                val after = ((age - glyph.durationMs) / 1_200).toFloat().coerceIn(0f, 1f)
                val opacity = when {
                    age < 0 -> 0.14f + entered * 0.16f
                    active -> 0.72f + entered * 0.28f
                    else -> 0.74f - after * 0.36f
                }
                val pulse = if (active) sin(progress * PI).toFloat().coerceAtLeast(0f) else 0f
                val scale = placement.scale * if (motion) (0.90f + entered * 0.10f + pulse * 0.12f) else 1f
                val driftX = if (motion) sin(phase * 0.45f + index * 1.7f) * 1.8.dp.toPx() else 0f
                val driftY = if (motion) sin(phase * 0.35f + index * 2.1f) * 1.5.dp.toPx() + (1f - entered) * 6.dp.toPx() else 0f
                val color = (if (active) Color.White else lerp(accent, Color(0xFFFFE1B6), index % 3 * 0.12f)).copy(alpha = opacity)
                withTransform({
                    translate(placement.centerX + driftX, placement.centerY + driftY)
                    rotate(placement.rotation + if (motion) (1f - entered) * 8f else 0f, pivot = Offset.Zero)
                    scale(scale, scale, pivot = Offset.Zero)
                }) {
                    drawText(layout, color = color, topLeft = Offset(-layout.size.width / 2f, -layout.size.height / 2f),
                        shadow = Shadow(accent.copy(alpha = if (active) 0.55f else 0.10f), blurRadius = (if (active) 16.dp else 4.dp).toPx()))
                }
            }
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
            StageInterlude(listOfNotNull(scene.line, scene.nextLine), position, Modifier)
        }
    }
}
