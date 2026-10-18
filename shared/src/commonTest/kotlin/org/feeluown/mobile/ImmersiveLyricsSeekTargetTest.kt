package org.feeluown.mobile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImmersiveLyricsSeekTargetTest {
    @Test
    fun timedLinePreservesAlignmentOffset() {
        assertEquals(9_250L, immersiveLyricSeekTarget(9_000L, 250L, 30_000L))
    }

    @Test
    fun negativeOffsetNeverSeeksBeforeTheStart() {
        assertEquals(0L, immersiveLyricSeekTarget(100L, -3_000L, 30_000L))
    }

    @Test
    fun skipUntimedCreditsInsteadOfSeekingToLongMax() {
        assertNull(immersiveLyricSeekTarget(Long.MAX_VALUE, 0L, 30_000L))
        assertNull(immersiveLyricSeekTarget(-1L, 0L, 30_000L))
    }

    @Test
    fun durationCapsOutOfRangeLineAndUnknownDurationDoesNot() {
        assertEquals(30_000L, immersiveLyricSeekTarget(40_000L, 250L, 30_000L))
        assertEquals(40_250L, immersiveLyricSeekTarget(40_000L, 250L, 0L))
    }
}
