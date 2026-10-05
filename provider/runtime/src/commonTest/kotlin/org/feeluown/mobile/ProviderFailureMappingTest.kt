package org.feeluown.mobile

import kotlinx.serialization.SerializationException
import org.feeluown.mobile.provider.core.network.ProviderNetworkException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProviderFailureMappingTest {
    @Test
    fun mapsHttpAuthenticationFailure() {
        val failure = ProviderNetworkException.Http(401, "unauthorized").providerFailureOrNull("netease")

        assertEquals(ProviderFailureKind.LoginExpired, failure?.kind)
        assertEquals("netease", failure?.providerId)
    }

    @Test
    fun mapsRegionFailureBeforeGenericHttpFailure() {
        val failure = ProviderNetworkException.Http(403, "not available in your country")
            .providerFailureOrNull("ytmusic")

        assertEquals(ProviderFailureKind.RegionRestricted, failure?.kind)
    }

    @Test
    fun mapsSerializationToContractChange() {
        val failure = SerializationException("Unexpected JSON token").providerFailureOrNull("qqmusic")

        assertEquals(ProviderFailureKind.UpstreamContractChanged, failure?.kind)
    }

    @Test
    fun preservesStructuredBusinessFailure() {
        val exception = providerBusinessException("netease", 301, "login required")

        assertIs<ProviderOperationException>(exception)
        assertEquals(ProviderFailureKind.LoginExpired, exception.failure.kind)
    }

    @Test
    fun mapsTransportToNetworkFailure() {
        val failure = ProviderNetworkException.Transport(IllegalStateException("offline")).providerFailureOrNull()

        assertEquals(ProviderFailureKind.Network, failure?.kind)
    }
}
