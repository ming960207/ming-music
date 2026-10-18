package org.feeluown.mobile

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Keep surrogate pairs, combining marks and joined emoji together. */
private fun stageGraphemes(text: String): List<String> {
    val result = mutableListOf<String>()
    var offset = 0
    var regionalCount = 0
    while (offset < text.length) {
        val first = text[offset]
        val length = if (first.isHighSurrogate() && text.getOrNull(offset + 1)?.isLowSurrogate() == true) 2 else 1
        val unit = text.substring(offset, offset + length)
        val code = if (length == 2) ((first.code - 0xD800) shl 10) + text[offset + 1].code - 0xDC00 + 0x10000 else first.code
        val regional = code in 0x1F1E6..0x1F1FF
        val attached = first.category in setOf(CharCategory.NON_SPACING_MARK, CharCategory.COMBINING_SPACING_MARK, CharCategory.ENCLOSING_MARK) ||
            code in 0xFE00..0xFE0F || code in 0x1F3FB..0x1F3FF || code == 0x200D ||
            result.lastOrNull()?.endsWith('\u200D') == true || (regional && regionalCount % 2 == 1)
        if (attached && result.isNotEmpty()) result[result.lastIndex] += unit else result += unit
        regionalCount = if (regional) regionalCount + 1 else 0
        offset += length
    }
    return result
}

/** Preserve source word boundaries; derive character entrances within each timed word. */
internal fun stageGlyphTokens(line: LyricLine, nextStartMs: Long): List<LyricWord> {
    if (line.timeMs < 0L || line.timeMs == Long.MAX_VALUE) return emptyList()
    val words = line.words?.takeIf { it.isNotEmpty() } ?: listOf(
        LyricWord(line.timeMs, (nextStartMs - line.timeMs).coerceIn(600L, 9_000L), line.text),
    )
    return words.flatMap { word ->
        val glyphs = stageGraphemes(word.text).filterNot(String::isBlank)
        val count = glyphs.size
        if (count == 0) emptyList() else {
            val duration = word.durationMs.coerceAtLeast(0L)
            fun boundary(index: Int): Long = duration / count * index + minOf(index.toLong(), duration % count)
            glyphs.mapIndexed { index, text ->
                val start = boundary(index)
                LyricWord(word.startMs + start, boundary(index + 1) - start, text)
            }
        }
    }
}

internal fun stageGlyphPageAt(units: List<LyricWord>, positionMs: Long, pageSize: Int): Int {
    require(pageSize > 0)
    var low = 0
    var high = units.lastIndex
    var active = 0
    while (low <= high) {
        val middle = (low + high).ushr(1)
        if (units[middle].startMs <= positionMs) { active = middle; low = middle + 1 } else high = middle - 1
    }
    return active / pageSize
}

internal data class StageGlyphMetric(val width: Float, val height: Float, val rotation: Float)

internal data class StageGlyphPoint(val x: Float, val y: Float)
internal data class StageGlyphFlight(
    val from: StageGlyphPoint, val control1: StageGlyphPoint, val control2: StageGlyphPoint,
    val to: StageGlyphPoint, val leadMs: Long,
)

/** Seeded paths are part of the scene; playback time only evaluates them. */
internal fun stageGlyphFlight(landing: StageGlyphPlacement, width: Float, height: Float, seed: Int, index: Int): StageGlyphFlight {
    val random = Random(seed xor (index * 7_919 + 0x65A31))
    val from = StageGlyphPoint(if (random.nextBoolean()) -width * 0.12f else width * 1.12f, height * (0.15f + random.nextFloat() * 0.7f))
    val to = StageGlyphPoint(landing.centerX, landing.centerY)
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
    val bend = height * (0.12f + random.nextFloat() * 0.16f) * if (random.nextBoolean()) 1 else -1
    val nx = -dy / length * bend
    val ny = dx / length * bend
    return StageGlyphFlight(from, StageGlyphPoint(from.x + dx * 0.28f + nx, from.y + dy * 0.28f + ny),
        StageGlyphPoint(from.x + dx * 0.72f + nx, from.y + dy * 0.72f + ny), to, 520L + random.nextInt(240))
}

internal fun stageGlyphEntryProgress(glyph: LyricWord, timeMs: Long, flight: StageGlyphFlight): Float =
    ((timeMs.toDouble() - glyph.startMs + flight.leadMs) / flight.leadMs).toFloat().coerceIn(0f, 1f)

/** De Casteljau evaluation keeps replay and seeking independent of frame history. */
internal fun stageGlyphFlightPoint(flight: StageGlyphFlight, progress: Float): StageGlyphPoint {
    if (progress <= 0) return flight.from
    if (progress >= 1) return flight.to
    val t = progress * progress * (3 - 2 * progress)
    fun coordinate(a: Float, b: Float, c: Float, d: Float): Float {
        val ab = a + (b - a) * t
        val bc = b + (c - b) * t
        val cd = c + (d - c) * t
        val left = ab + (bc - ab) * t
        val right = bc + (cd - bc) * t
        return left + (right - left) * t
    }
    return StageGlyphPoint(coordinate(flight.from.x, flight.control1.x, flight.control2.x, flight.to.x),
        coordinate(flight.from.y, flight.control1.y, flight.control2.y, flight.to.y))
}

internal data class StageGlyphPlacement(
    val centerX: Float,
    val centerY: Float,
    val scale: Float,
    val rotation: Float,
    val width: Float,
    val height: Float,
) {
    val left: Float get() = centerX - width / 2
    val top: Float get() = centerY - height / 2
    val right: Float get() = centerX + width / 2
    val bottom: Float get() = centerY + height / 2
}

/** Seeded best-candidate scatter, with rotated bounds and room for the entrance animation. */
internal fun layoutStageGlyphs(metrics: List<StageGlyphMetric>, width: Float, height: Float, seed: Int): List<StageGlyphPlacement> {
    if (metrics.isEmpty() || !width.isFinite() || !height.isFinite() || width <= 0 || height <= 0) return emptyList()
    val edge = min(width, height) * 0.045f
    var scale = 1f
    repeat(18) { attempt ->
        val random = Random(seed + attempt * 101)
        val placed = mutableListOf<StageGlyphPlacement>()
        for (metric in metrics) {
            val radians = metric.rotation * (kotlin.math.PI / 180).toFloat()
            val padding = metric.height * 0.20f * scale
            val w = (abs(cos(radians)) * metric.width + abs(sin(radians)) * metric.height) * scale * 1.16f + padding
            val h = (abs(sin(radians)) * metric.width + abs(cos(radians)) * metric.height) * scale * 1.16f + padding
            val spanX = width - edge * 2 - w
            val spanY = height - edge * 2 - h
            if (spanX < 0 || spanY < 0) break
            var best: StageGlyphPlacement? = null
            var bestScore = -1f
            repeat(90) candidate@ {
                val x = edge + w / 2 + random.nextFloat() * spanX
                val y = edge + h / 2 + random.nextFloat() * spanY
                val candidate = StageGlyphPlacement(x, y, scale, metric.rotation, w, h)
                if (placed.any { candidate.left < it.right && candidate.right > it.left && candidate.top < it.bottom && candidate.bottom > it.top }) return@candidate
                val score = placed.minOfOrNull {
                    val dx = (x - it.centerX) / width
                    val dy = (y - it.centerY) / height
                    dx * dx + dy * dy
                } ?: 1f
                if (score > bestScore) { best = candidate; bestScore = score }
            }
            if (best == null) break else placed += best
        }
        if (placed.size == metrics.size) return placed
        scale *= 0.84f
    }
    return emptyList()
}
