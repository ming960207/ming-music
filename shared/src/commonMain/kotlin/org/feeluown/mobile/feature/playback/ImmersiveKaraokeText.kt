package org.feeluown.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp

private data class StageWordGeometry(val word: LyricWord, val runs: List<Rect>, val width: Float)

/** Measure the real wrapped layout, not separate words: kerning and line breaks remain exact. */
@Composable
internal fun ImmersiveKaraokeText(
    words: List<LyricWord>,
    position: State<Long>,
    style: TextStyle,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val text = remember(words) { words.joinToString("") { it.text } }
    var layout by remember(text, style) { mutableStateOf<TextLayoutResult?>(null) }
    val geometry = remember(words, layout) {
        val measured = layout ?: return@remember emptyList()
        var offset = 0
        words.map { word ->
            val runs = mutableListOf<Rect>()
            word.text.indices.forEach { local ->
                val rect = measured.getBoundingBox(offset + local)
                if (rect.width > 0) {
                    val previous = runs.lastOrNull()
                    if (previous != null && previous.top == rect.top && rect.left <= previous.right + 1 && rect.right >= previous.left - 1) {
                        runs[runs.lastIndex] = Rect(minOf(previous.left, rect.left), rect.top, maxOf(previous.right, rect.right), rect.bottom)
                    } else runs += rect
                }
            }
            offset += word.text.length
            StageWordGeometry(word, runs, runs.sumOf { it.width.toDouble() }.toFloat())
        }
    }
    Box(modifier.drawWithContent {
        drawContent()
        val measured = layout ?: return@drawWithContent
        val time = position.value // Invalidate drawing only; no frame-by-frame layout/composition.
        for (part in geometry) {
            val progress = immersiveWordProgress(part.word, time)
            if (progress <= 0f) continue
            var remaining = part.width * progress
            val color = if (progress < 1f) Color.White else accent
            for (rect in part.runs) {
                val width = minOf(rect.width, remaining)
                if (width > 0f) clipRect(rect.left, rect.top - 2.dp.toPx(), rect.left + width, rect.bottom + 2.dp.toPx()) {
                    drawText(measured, color = color, shadow = Shadow(color.copy(alpha = 0.35f), blurRadius = 10.dp.toPx()))
                }
                remaining -= rect.width
                if (remaining <= 0f) break
            }
        }
    }) {
        Text(text, style = style, color = Color.White.copy(alpha = 0.30f), modifier = Modifier.fillMaxWidth(), onTextLayout = { layout = it })
    }
}
