package org.feeluown.mobile

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import org.feeluown.mobile.provider.core.network.ProviderNetworkException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class ProviderFailureTest {
    @Test
    fun classifiesExpiredLoginFromHttpStatus() {
        val failure = ProviderNetworkException.Http(401, "unauthorized").providerFailureOrNull("netease")

        assertEquals(ProviderFailureKind.LoginExpired, failure?.kind)
        assertEquals("netease", failure?.providerId)
        assertEquals("登录状态已失效，请重新登录后重试", failure?.userMessage)
    }

    @Test
    fun classifiesExpiredLoginFromBusinessMessage() {
        val exception = providerBusinessException("qqmusic", 1000, "cookie expired, please login again")

        assertEquals(ProviderFailureKind.LoginExpired, exception.failure.kind)
    }

    @Test
    fun classifiesExpiredLoginFromProviderBusinessCode() {
        val exception = providerBusinessException("netease", 301, "")

        assertEquals(ProviderFailureKind.LoginExpired, exception.failure.kind)
    }

    @Test
    fun classifiesBlankHttp403AsExpiredLogin() {
        val failure = ProviderNetworkException.Http(403, "").providerFailureOrNull("qqmusic")

        assertEquals(ProviderFailureKind.LoginExpired, failure?.kind)
    }

    @Test
    fun classifiesRegionRestrictionBeforeGenericHttpFailure() {
        val failure = ProviderNetworkException.Http(403, "not available in your country")
            .providerFailureOrNull("ytmusic")

        assertEquals(ProviderFailureKind.RegionRestricted, failure?.kind)
    }

    @Test
    fun classifiesHttp451AsRegionRestriction() {
        val failure = ProviderNetworkException.Http(451, "")
            .providerFailureOrNull("bilibili")

        assertEquals(ProviderFailureKind.RegionRestricted, failure?.kind)
    }

    @Test
    fun exposesAllStableUserMessages() {
        val messages = ProviderFailureKind.entries.associateWith { kind ->
            ProviderFailure(kind).userMessage
        }

        assertEquals("登录状态已失效，请重新登录后重试", messages[ProviderFailureKind.LoginExpired])
        assertEquals("当前地区暂不支持此内容", messages[ProviderFailureKind.RegionRestricted])
        assertEquals("该内容因版权或资源限制不可用", messages[ProviderFailureKind.CopyrightUnavailable])
        assertEquals("音源接口响应已变化，请更新应用或稍后重试", messages[ProviderFailureKind.UpstreamContractChanged])
        assertEquals("网络请求失败，请检查网络后重试", messages[ProviderFailureKind.Network])
    }

    @Test
    fun classifiesUnavailableMediaAsCopyrightFailure() {
        val failure = IllegalStateException("media not found after smart replacement")
            .providerFailureOrNull("netease")

        assertEquals(ProviderFailureKind.CopyrightUnavailable, failure?.kind)
        assertEquals("该内容因版权或资源限制不可用", failure?.userMessage)
    }

    @Test
    fun classifiesSerializationAsUpstreamContractChange() {
        val failure = SerializationException("Unexpected JSON token").providerFailureOrNull("qqmusic")

        assertEquals(ProviderFailureKind.UpstreamContractChanged, failure?.kind)
    }

    @Test
    fun preservesStructuredProviderFailureThroughCauseChain() {
        val structured = providerContractException("netease", "dailySongs field missing")
        val failure = IllegalStateException("wrapper", structured).providerFailureOrNull()

        assertEquals(structured.failure, failure)
    }

    @Test
    fun classifiesNetworkTransportAndTimeoutFailures() {
        val transport = ProviderNetworkException.Transport(IllegalStateException("offline"))
        val timeout = ProviderNetworkException.Timeout(IllegalStateException("slow"))

        assertEquals(ProviderFailureKind.Network, transport.providerFailureOrNull()?.kind)
        assertEquals(ProviderFailureKind.Network, timeout.providerFailureOrNull()?.kind)
    }

    @Test
    fun classifiesCoroutineTimeoutAsNetworkFailure() = runTest {
        val exception = assertFailsWith<TimeoutCancellationException> {
            withTimeout(1) { awaitCancellation() }
        }
        val failure = exception.providerFailureOrNull()

        assertEquals(ProviderFailureKind.Network, failure?.kind)
    }

    @Test
    fun leavesUnrelatedApplicationFailureUnclassified() {
        assertNull(IllegalArgumentException("playlist name is empty").providerFailureOrNull())
    }

    @Test
    fun unknownProviderBusinessCodeBecomesContractFailure() {
        val exception = providerBusinessException("netease", 599, "unknown business response")

        assertIs<ProviderOperationException>(exception)
        assertEquals(ProviderFailureKind.UpstreamContractChanged, exception.failure.kind)
        assertEquals("netease", exception.failure.providerId)
    }

    @Test
    fun providerControllerStateStoresStructuredFailure() {
        val state = ProviderControllerState()

        val message = state.userMessage(
            ProviderNetworkException.Http(451, "blocked by region"),
            fallback = "加载失败",
        )

        assertEquals("当前地区暂不支持此内容", message)
        assertEquals(ProviderFailureKind.RegionRestricted, state.lastFailure?.kind)
    }

    @Test
    fun providerControllerStateKeepsFallbackForUnclassifiedFailure() {
        val state = ProviderControllerState()

        val message = state.userMessage(IllegalStateException(), fallback = "加载失败")

        assertEquals("加载失败", message)
        assertNull(state.lastFailure)
    }
}
