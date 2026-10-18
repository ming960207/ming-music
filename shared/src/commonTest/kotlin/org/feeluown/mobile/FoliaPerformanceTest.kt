package org.feeluown.mobile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoliaPerformanceTest {
    @Test
    fun keepsExactYrcWordTiming() {
        val line = LyricLine(
            timeMs = 1_000,
            text = "唱歌",
            words = listOf(LyricWord(1_000, 200, "唱"), LyricWord(1_200, 300, "歌")),
        )
        assertEquals(line.words, foliaPerformanceTokens(line, 2_000))
    }

    @Test
    fun createsPredictableTimelineForOrdinaryLrc() {
        val tokens = foliaPerformanceTokens(LyricLine(1_000, "你好 世界"), 3_000)
        assertEquals("你好 世界", tokens.joinToString("") { it.text })
        assertEquals(1_000L, tokens.first().startMs)
        assertTrue(tokens.zipWithNext().all { (a, b) -> a.startMs < b.startMs })
    }

    @Test
    fun shortOrMissingDurationStillHasPositiveWordTimings() {
        val tokens = foliaPerformanceTokens(LyricLine(10_000, "等待"), 10_000)
        assertTrue(tokens.all { it.durationMs > 0 })
    }

    @Test
    fun timeLabelsClampNegativeValues() {
        assertEquals("0:00", foliaTimeLabel(-10))
        assertEquals("5:20", foliaTimeLabel(320_000))
    }
}
