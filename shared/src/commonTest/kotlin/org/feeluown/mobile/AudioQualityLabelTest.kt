package org.feeluown.mobile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AudioQualityLabelTest {
    @Test
    fun keepsProviderQualityTiers() {
        assertEquals("LQ", normalizedAudioQualityLabel("lq", null))
        assertEquals("SQ", normalizedAudioQualityLabel("sq", null))
        assertEquals("HQ", normalizedAudioQualityLabel("HQ", null))
        assertEquals("SHQ", normalizedAudioQualityLabel("shq", null))
    }

    @Test
    fun usesProviderCodecAwareQualityBeforeBitrate() {
        assertEquals("LQ", normalizedAudioQualityLabel("AUDIO_QUALITY_LOW", AudioFormatInfo(averageBitrate = 96_000)))
        assertEquals("SQ", normalizedAudioQualityLabel("AUDIO_QUALITY_MEDIUM", AudioFormatInfo(averageBitrate = 128_000)))
        assertEquals("HQ", normalizedAudioQualityLabel("AUDIO_QUALITY_HIGH", AudioFormatInfo(averageBitrate = 192_000)))
        assertEquals("LQ", normalizedAudioQualityLabel("AUDIO_QUALITY_ULTRALOW", null))
    }

    @Test
    fun classifiesLosslessFormatsAsShq() {
        assertEquals("SHQ", normalizedAudioQualityLabel("flac", null))
        assertEquals("SHQ", normalizedAudioQualityLabel(null, AudioFormatInfo(format = "audio/flac")))
        assertEquals("SHQ", normalizedAudioQualityLabel("hq", AudioFormatInfo(codec = "alac")))
    }

    @Test
    fun classifiesLossyBitrateUsingCommonTiers() {
        assertEquals("LQ", audioQualityLabelForBitrate(128_000))
        assertEquals("SQ", audioQualityLabelForBitrate(128_001))
        assertEquals("SQ", audioQualityLabelForBitrate(192_000))
        assertEquals("SQ", audioQualityLabelForBitrate(255_999))
        assertEquals("HQ", audioQualityLabelForBitrate(256_000))
        assertEquals("HQ", audioQualityLabelForBitrate(320_000))
    }

    @Test
    fun usesActualBitrateForCodecOnlyProviderValues() {
        assertEquals("LQ", normalizedAudioQualityLabel("mp3", AudioFormatInfo(averageBitrate = 128_000)))
        assertEquals("SQ", normalizedAudioQualityLabel("mp3", AudioFormatInfo(averageBitrate = 192_000)))
        assertEquals("HQ", normalizedAudioQualityLabel("mp3", AudioFormatInfo(averageBitrate = 320_000)))
    }

    @Test
    fun supportsNumericProviderBitrateAndHidesCodecOnlyValues() {
        assertEquals("SQ", normalizedAudioQualityLabel("192000", null))
        assertEquals("HQ", normalizedAudioQualityLabel("320000", null))
        assertNull(normalizedAudioQualityLabel("mp3", null))
        assertNull(normalizedAudioQualityLabel("m4a", null))
        assertNull(normalizedAudioQualityLabel(null, AudioFormatInfo(format = "audio/mpeg")))
    }
}
