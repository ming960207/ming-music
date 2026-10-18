package org.feeluown.mobile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImmersiveStageTimelineTest {
    @Test
    fun openingDoesNotHighlightAnUnstartedLineOrCredits() {
        val lines = listOf(LyricLine(5_000, "first"), LyricLine(10_000, "second"), LyricLine(Long.MAX_VALUE, "credit"))
        assertEquals(-1, immersiveActiveLineIndex(lines, 0))
        assertEquals(0, immersiveActiveLineIndex(lines, 5_000))
        assertEquals(1, immersiveActiveLineIndex(lines, Long.MAX_VALUE))
    }

    @Test
    fun wordProgressStopsAtWordBoundariesAndSurvivesZeroDuration() {
        val word = LyricWord(1_000, 2_000, "hello")
        assertEquals(0f, immersiveWordProgress(word, 999))
        assertEquals(0.5f, immersiveWordProgress(word, 2_000))
        assertEquals(1f, immersiveWordProgress(word, 9_000))
        assertEquals(1f, immersiveWordProgress(LyricWord(1_000, 0, "!"), 1_000))
    }

    @Test
    fun interludeUsesActualWordEndInsteadOfGuessingLrcTiming() {
        val lines = listOf(
            LyricLine(0, "first", words = listOf(LyricWord(0, 2_000, "first"))),
            LyricLine(14_000, "second"),
        )
        assertNull(immersiveInterlude(lines, 1_500))
        val gap = immersiveInterlude(lines, 8_000)!!
        assertEquals(6_000L, gap.remainingMs)
        assertEquals(0.5f, gap.progress)
        assertNull(immersiveInterlude(lines, 14_000))
        assertNull(immersiveInterlude(listOf(LyricLine(0, "no word timing"), LyricLine(14_000, "next")), 8_000))
    }

    @Test
    fun introCanCountDownButUntimedCreditsNeverBecomeAnInterlude() {
        assertEquals(5_000L, immersiveInterlude(listOf(LyricLine(10_000, "first")), 5_000)!!.remainingMs)
        assertNull(immersiveInterlude(listOf(LyricLine(Long.MAX_VALUE, "credit")), 1_000))
    }

    @Test
    fun overflowingPositiveAlignmentNeverWrapsToTheStart() {
        assertEquals(30_000L, immersiveLyricSeekTarget(Long.MAX_VALUE - 1, 5_000, 30_000))
    }
}
