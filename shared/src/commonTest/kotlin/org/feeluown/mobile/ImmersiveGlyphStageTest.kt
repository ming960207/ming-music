package org.feeluown.mobile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImmersiveGlyphStageTest {
    @Test
    fun wordTimingIsSubdividedWithoutSplittingEmojiOrCombiningMarks() {
        val line = LyricLine(1_000, "你好🌙e\u0301", words = listOf(
            LyricWord(1_000, 1_200, "你好"), LyricWord(2_500, 600, "🌙e\u0301"),
        ))
        val units = stageGlyphTokens(line, 5_000)
        assertEquals(listOf("你", "好", "🌙", "e\u0301"), units.map { it.text })
        assertEquals(listOf(1_000L, 1_600L, 2_500L, 2_800L), units.map { it.startMs })
        assertEquals(3_100L, units.last().startMs + units.last().durationMs)
    }

    @Test
    fun familyEmojiIsOneGlyphAndWhitespaceNeverBecomesAVisibleTile() {
        val units = stageGlyphTokens(LyricLine(0, "夜 👨‍👩‍👧‍👦 色"), 4_000)
        assertEquals(listOf("夜", "👨‍👩‍👧‍👦", "色"), units.map { it.text })
        assertTrue(stageGlyphTokens(LyricLine(Long.MAX_VALUE, "credit"), 0).isEmpty())
    }

    @Test
    fun scatteredLayoutIsStableForTheSameSceneAndVariesWithTheSeed() {
        val metrics = List(12) { StageGlyphMetric(44f + it % 3 * 8, 58f, (it * 11 % 25 - 12).toFloat()) }
        val first = layoutStageGlyphs(metrics, 380f, 500f, 127)
        assertEquals(12, first.size)
        assertEquals(first, layoutStageGlyphs(metrics, 380f, 500f, 127))
        assertFalse(first == layoutStageGlyphs(metrics, 380f, 500f, 128))
        assertTrue(first.map { it.centerY.toInt() }.toSet().size > 6, "Glyphs must not become rows")
    }

    @Test
    fun glyphBoundsStayInsideTheViewportAndDoNotCollide() {
        val metrics = List(14) { StageGlyphMetric(65f, 80f, (it * 7 % 21 - 10).toFloat()) }
        for ((width, height) in listOf(360f to 460f, 780f to 180f, 140f to 120f)) {
            val placements = layoutStageGlyphs(metrics, width, height, 42)
            assertEquals(metrics.size, placements.size)
            placements.forEachIndexed { i, glyph ->
                assertTrue(glyph.left >= 0 && glyph.top >= 0 && glyph.right <= width && glyph.bottom <= height)
                placements.take(i).forEach { previous ->
                    assertFalse(glyph.left < previous.right && glyph.right > previous.left &&
                        glyph.top < previous.bottom && glyph.bottom > previous.top, "Glyphs overlap")
                }
            }
        }
    }

    @Test
    fun longLinesTurnPagesByTimingRatherThanRepositioningEveryFrame() {
        val units = (0 until 40).map { LyricWord(it * 500L, 500, "$it") }
        assertEquals(0, stageGlyphPageAt(units, -1, 14))
        assertEquals(0, stageGlyphPageAt(units, 6_999, 14))
        assertEquals(1, stageGlyphPageAt(units, 7_000, 14))
        assertEquals(2, stageGlyphPageAt(units, 99_000, 14))
    }
}
