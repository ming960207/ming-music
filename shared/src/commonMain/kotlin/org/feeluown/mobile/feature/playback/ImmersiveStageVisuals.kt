package org.feeluown.mobile

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.materialkolor.ktx.themeColorOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun rememberStageClock(playing: Boolean, motionEnabled: Boolean): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing, motionEnabled) {
        if (!playing || !motionEnabled) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while ((coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f) {
            withFrameNanos { now ->
                seconds.floatValue = (seconds.floatValue + ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.1f)) % 3_600f
                previous = now
            }
        }
    }
    return seconds
}

@Composable
internal fun rememberStageAccent(image: ImageBitmap?, fallback: Color): Color {
    val seed by produceState(fallback, image) {
        if (image != null) value = withContext(Dispatchers.Default) { image.themeColorOrNull(maxColors = 12) } ?: fallback
    }
    val accent by animateColorAsState(lerp(seed, Color.White, 0.32f), tween(1_200), label = "stage cover palette")
    return accent
}

/** Three cached radial shaders; per-frame work stays in drawing rather than recomposition. */
@Composable
internal fun ImmersiveStageBackdrop(image: ImageBitmap?, accent: Color, clock: State<Float>, style: ImmersiveStageStyle) {
    Box(Modifier.fillMaxSize().background(Color(0xFF080B12))) {
        Crossfade(image, animationSpec = tween(1_200), label = "stage cover atmosphere") { cover ->
            if (cover != null) Image(
                bitmap = cover, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.35f; scaleY = 1.35f; alpha = 0.30f }.blur(52.dp),
            )
        }
        Box(Modifier.fillMaxSize().drawWithCache {
            val radius = size.maxDimension * 0.72f
            val warm = lerp(accent, Color(0xFFF4B77D), 0.30f)
            val brushes = listOf(accent, warm, lerp(accent, Color(0xFF445AAD), 0.36f)).map { color ->
                Brush.radialGradient(listOf(color.copy(alpha = if (style == ImmersiveStageStyle.Focus || style == ImmersiveStageStyle.Glyph) 0.16f else 0.30f), Color.Transparent), center = Offset.Zero, radius = radius)
            }
            onDrawBehind {
                val phase = clock.value / 14f
                brushes.forEachIndexed { index, brush ->
                    val x = size.width * (0.25f + index * 0.25f + sin(phase + index * 2f) * 0.18f)
                    val y = size.height * (0.30f + cos(phase * 0.7f + index) * 0.24f)
                    translate(x, y) { drawCircle(brush, radius, center = Offset.Zero) }
                }
            }
        })
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color(0xFF080B12).copy(alpha = 0.30f), Color.Transparent, Color(0xFF080B12).copy(alpha = 0.80f),
        ))))
    }
}

@Composable
internal fun ImmersiveVinylCover(image: ImageBitmap?, accent: Color, clock: State<Float>, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent)))
        }
        Box(Modifier.fillMaxSize(0.88f).graphicsLayer { rotationZ = (clock.value * 9f) % 360f }.clip(CircleShape)
            .background(Color(0xFF101116)).border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)) {
            Canvas(Modifier.fillMaxSize().padding(9.dp)) {
                repeat(18) { index ->
                    drawCircle(Color.White.copy(alpha = if (index % 3 == 0) 0.08f else 0.035f),
                        radius = size.minDimension * (0.29f + index * 0.011f), style = Stroke(0.7.dp.toPx()))
                }
                drawCircle(Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.09f), Color.Transparent, Color.White.copy(alpha = 0.04f), Color.Transparent)))
            }
            if (image != null) Image(bitmap = image, contentDescription = "专辑封面", contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(0.52f).align(Alignment.Center).clip(CircleShape))
            else Box(Modifier.fillMaxSize(0.52f).align(Alignment.Center).clip(CircleShape).background(accent.copy(alpha = 0.55f)))
            Box(Modifier.size(10.dp).align(Alignment.Center).clip(CircleShape).background(Color(0xFF080B12)).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape))
        }
    }
}
