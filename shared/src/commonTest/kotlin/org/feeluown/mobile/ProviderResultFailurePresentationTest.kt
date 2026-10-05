package org.feeluown.mobile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProviderResultFailurePresentationTest {
    @Test
    fun legacySearchDisplayErrorIsStructuredAtSharedBoundary() {
        val results = ProviderSearchResults(errorMessage = "网络请求失败，请检查网络后重试")

        assertEquals(ProviderFailureKind.Network, results.failure?.kind)
        assertNull(results.failure?.technicalMessage)
        assertEquals("网络请求失败，请检查网络后重试", results.errorMessage)
    }

    @Test
    fun legacyUnsupportedContentErrorDoesNotCrossApiAsDisplayText() {
        val feature = ProviderFeature(
            id = "test",
            providerId = "test-provider",
            providerName = "Test",
            title = "Test",
            category = ProviderFeatureCategory.Music,
            contentType = ProviderContentType.Songs,
            requiresLogin = false,
        )
        val section = ProviderContentSection(
            feature = feature,
            errorMessage = "当前音源暂不支持该内容",
        )

        assertEquals(ProviderFailureKind.UnsupportedOperation, section.failure?.kind)
        assertEquals("test-provider", section.failure?.providerId)
        assertNull(section.failure?.technicalMessage)
        assertEquals("当前音源暂不支持该操作或内容", section.errorMessage)
    }
}
