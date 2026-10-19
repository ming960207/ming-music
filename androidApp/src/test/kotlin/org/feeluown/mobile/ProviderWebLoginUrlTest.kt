package org.feeluown.mobile

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderWebLoginUrlTest {
    @Test
    fun allowsCurrentHttpsLoginUrls() {
        assertTrue(ProviderWebLoginActivity.isWebLoginUrlAllowed("https://music.163.com"))
        assertTrue(ProviderWebLoginActivity.isWebLoginUrlAllowed("https://y.qq.com"))
        assertTrue(
            ProviderWebLoginActivity.isWebLoginUrlAllowed(
                "https://passport.bilibili.com/h5-app/passport/login?gourl=https%3A%2F%2Fm.bilibili.com%2F",
            ),
        )
    }

    @Test
    fun rejectsMissingHostAndUnsafeSchemes() {
        listOf("", "about:blank", "javascript:alert(1)", "file:///tmp/login.html",
            "data:text/html,hello", "http://music.163.com", "https://",
            "https://user:password@example.com",
        ).forEach { url ->
            assertFalse(ProviderWebLoginActivity.isWebLoginUrlAllowed(url), url)
        }
    }
}
