package org.feeluown.mobile

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import java.net.URI
import android.net.http.SslError
import android.util.Log
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.SslErrorHandler
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.widget.FrameLayout
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
class ProviderWebLoginActivity : ComponentActivity() {
    private lateinit var providerId: String
    private lateinit var providerName: String
    private lateinit var loginUrl: String
    private lateinit var cookieKeyGroups: List<List<String>>
    private lateinit var webView: WebView
    private var statusMessage by mutableStateOf("")
    private var pageError by mutableStateOf<String?>(null)
    private var pageLoading by mutableStateOf(false)
    private var startedLoading = false
    private var initialLoadScheduled = false

    @SuppressLint("SetJavaScriptEnabled") // 提供方登录页完成认证需要 JavaScript。
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        providerId = intent.getStringExtra(EXTRA_PROVIDER_ID).orEmpty()
        providerName = intent.getStringExtra(EXTRA_PROVIDER_NAME).orEmpty().ifBlank { providerId }
        loginUrl = intent.getStringExtra(EXTRA_LOGIN_URL).orEmpty()
        cookieKeyGroups = parseCookieKeyGroups(intent.getStringExtra(EXTRA_COOKIE_KEY_GROUPS).orEmpty())
        if (providerId.isBlank() || loginUrl.isBlank()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        statusMessage = getString(R.string.provider_login_cookie_hint)

        cookieManager().setAcceptCookie(true)
        if (!isWebLoginUrlAllowed(loginUrl)) {
            statusMessage = "无效的登录网址：只允许 HTTPS 网页"
            pageError = statusMessage
        }
        webView = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.WHITE)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            // Authentication endpoints compare UA, Client Hints and Chromium version.
            // Use the current Android System WebView identity rather than a spoofed
            // old Mac/Chrome 124 UA (and never inject a second User-Agent header).
            // Keep the platform-provided UA unchanged.
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            settings.loadsImagesAutomatically = true
            cookieManager().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        Log.w(LOG_TAG, "Web login JS (" + providerId + "): " + message.message())
                    }
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    pageLoading = true
                    pageError = null
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    pageLoading = false
                    cookieManager().flush()
                    finishWithCookies(url, auto = true)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) {
                        pageLoading = false
                        pageError = "无法打开网页登录页：" + error.description.toString() +
                            "（" + error.errorCode + "）"
                        Log.e(LOG_TAG, "Web login load failure provider=" + providerId +
                            " code=" + error.errorCode + " host=" + request.url.host)
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                        pageLoading = false
                        pageError = "音源网站返回 HTTP " + errorResponse.statusCode +
                            "。可能限制了内置浏览器登录。"
                        Log.w(LOG_TAG, "Web login HTTP error provider=" + providerId +
                            " code=" + errorResponse.statusCode + " host=" + request.url.host)
                    }
                }

                override fun onReceivedSslError(
                    view: WebView,
                    handler: SslErrorHandler,
                    error: SslError,
                ) {
                    handler.cancel() // Never bypass certificate validation for accounts.
                    pageLoading = false
                    pageError = "网页安全证书验证失败，请检查系统时间、网络或 VPN 设置。"
                    Log.e(LOG_TAG, "Web login SSL error provider=" + providerId +
                        " primaryError=" + error.primaryError)
                }
            }
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = scheduleFirstLoad()
                override fun onViewDetachedFromWindow(v: View) = Unit
            })
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> scheduleFirstLoad() }
        }

        setContent {
            FuoTheme(
                themeMode = webLoginThemeMode(),
                themeColorScheme = webLoginThemeColorScheme(),
            ) {
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text(getString(R.string.provider_browser_login_title, providerName)) },
                            navigationIcon = {
                                IconButton(
                                    onClick = {
                                        setResult(RESULT_CANCELED)
                                        finish()
                                    },
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = getString(R.string.close))
                                }
                            },
                            actions = {
                                TextButton(onClick = { finishWithCookies(webView.url, auto = false) }) {
                                    Text(getString(R.string.done))
                                }
                            },
                        )
                    },
                ) { paddingValues ->
                    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            Text(
                                text = statusMessage,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (pageLoading) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                                Text("正在加载 " + providerName + " 登录网页…")
                            }
                        }
                        pageError?.let { error ->
                            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                                    TextButton(onClick = { retryLoginPage() }) {
                                        Text("重试网页登录")
                                    }
                                }
                            }
                        }
                        AndroidView(
                            factory = { viewContext ->
                                FrameLayout(viewContext).apply {
                                    setBackgroundColor(android.graphics.Color.WHITE)
                                    addView(webView, FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    ))
                                }
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
        }
        configureSystemBars(webLoginThemeMode())
    }

    private fun scheduleFirstLoad() {
        if (initialLoadScheduled || startedLoading || pageError != null ||
            !::webView.isInitialized || !webView.isAttachedToWindow ||
            webView.width <= 0 || webView.height <= 0) return
        initialLoadScheduled = true
        webView.post {
            initialLoadScheduled = false
            if (!startedLoading && webView.isAttachedToWindow &&
                webView.width > 0 && webView.height > 0) {
                startedLoading = true
                pageLoading = true
                Log.i(LOG_TAG, "Web login starts: provider=" + providerId +
                    " host=" + Uri.parse(loginUrl).host)
                webView.loadUrl(loginUrl)
            }
        }
    }

    private fun retryLoginPage() {
        if (!isWebLoginUrlAllowed(loginUrl)) return
        pageError = null
        pageLoading = true
        startedLoading = true
        webView.loadUrl(loginUrl)
    }

    private fun webLoginThemeMode(): ThemeMode {
        val preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return enumValue(
            preferences.getString(KEY_THEME_MODE, null),
            ThemeMode.System,
        )
    }

    private fun webLoginThemeColorScheme(): ThemeColorScheme {
        val preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return enumValue(
            preferences.getString(KEY_THEME_COLOR_SCHEME, null),
            ThemeColorScheme.Dynamic,
        )
    }

    private fun configureSystemBars(themeMode: ThemeMode) {
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val darkTheme = when (themeMode) {
            ThemeMode.System -> systemDark
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T {
        return runCatching { enumValueOf<T>(raw.orEmpty()) }.getOrDefault(fallback)
    }

    override fun onDestroy() {
        cookieManager().flush()
        if (::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun finishWithCookies(currentUrl: String?, auto: Boolean) {
        if (!auto && ::webView.isInitialized) {
            webView.stopLoading()
        }
        cookieManager().flush()
        val cookies = collectCookies(currentUrl)
        if (auto && !hasRequiredCookies(cookies)) return
        if (!auto && cookies.isEmpty()) {
            Toast.makeText(this, "未获取到 Cookie", Toast.LENGTH_SHORT).show()
            return
        }
        cookieManager().flush()
        val data = Intent()
            .putExtra(EXTRA_PROVIDER_ID, providerId)
            .putExtra(EXTRA_COOKIES_JSON, cookiesToJson(cookies))
        setResult(RESULT_OK, data)
        finish()
    }

    private fun collectCookies(currentUrl: String?): Map<String, String> {
        val result = linkedMapOf<String, String>()
        cookieLookupUrls(currentUrl).forEach { url ->
            cookieManager().getCookie(url)
                ?.split(";")
                ?.forEach { part ->
                    val pieces = part.split("=", limit = 2)
                    if (pieces.size == 2) {
                        val key = pieces[0].trim()
                        val value = pieces[1].trim()
                        if (key.isNotBlank() && value.isNotBlank()) {
                            result[key] = value
                        }
                    }
                }
        }
        statusMessage = if (result.isEmpty()) {
            "等待登录 Cookie"
        } else {
            "已获取 ${result.size} 个 Cookie"
        }
        return result
    }

    private fun cookieLookupUrls(currentUrl: String?): List<String> {
        val urls = linkedSetOf<String>()
        listOf(loginUrl, currentUrl, webView.url, webView.originalUrl)
            .filterNotNull()
            .filter { it.isNotBlank() }
            .forEach { urls.add(it) }
        providerCookieHosts(providerId).forEach { host ->
            urls.add("https://$host")
            urls.add("http://$host")
        }
        return urls.toList()
    }

    private fun providerCookieHosts(providerId: String): List<String> {
        return when (providerId) {
            "netease" -> listOf(
                "music.163.com",
                "m.music.163.com",
                "interface.music.163.com",
                "interface3.music.163.com",
            )
            "qqmusic" -> listOf(
                "y.qq.com",
                "u.y.qq.com",
                "i.y.qq.com",
                "c.y.qq.com",
                "graph.qq.com",
                "ptlogin2.qq.com",
                "qq.com",
            )
            "bilibili" -> listOf(
                "www.bilibili.com",
                "api.bilibili.com",
                "passport.bilibili.com",
                "bilibili.com",
            )
            "ytmusic" -> listOf(
                "music.youtube.com",
                "youtube.com",
                "www.youtube.com",
                "accounts.google.com",
                "google.com",
            )
            else -> emptyList()
        }
    }

    private fun cookieManager(): CookieManager = CookieManager.getInstance()

    private fun hasRequiredCookies(cookies: Map<String, String>): Boolean {
        if (cookieKeyGroups.isEmpty()) return cookies.isNotEmpty()
        return cookieKeyGroups.any { group ->
            group.all { key -> cookies[key].isNullOrBlank().not() }
        }
    }

    private fun cookiesToJson(cookies: Map<String, String>): String {
        val json = JSONObject()
        cookies.forEach { (key, value) -> json.put(key, value) }
        return json.toString()
    }

    private fun parseCookieKeyGroups(raw: String): List<List<String>> {
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        return List(array.length()) { index ->
            val group = array.getJSONArray(index)
            List(group.length()) { keyIndex -> group.getString(keyIndex) }
        }
    }

    companion object {
        const val EXTRA_PROVIDER_ID = "provider_id"
        const val EXTRA_COOKIES_JSON = "cookies_json"

        private const val EXTRA_PROVIDER_NAME = "provider_name"
        private const val EXTRA_LOGIN_URL = "login_url"
        private const val EXTRA_COOKIE_KEY_GROUPS = "cookie_key_groups"
        private const val PREFS_NAME = "fuo_settings"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_THEME_COLOR_SCHEME = "theme_color_scheme"
        private const val LOG_TAG = "MingProviderWebLogin"
        // Reject malformed URLs early; credential pages must use HTTPS.
        internal fun isWebLoginUrlAllowed(url: String): Boolean {
            val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
            return uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null
        }

        fun createIntent(context: Context, provider: ProviderInfo): Intent {
            val loginConfig = requireNotNull(provider.loginConfig)
            val keyGroups = JSONArray()
            loginConfig.cookieKeyGroups.forEach { group ->
                val groupArray = JSONArray()
                group.forEach { groupArray.put(it) }
                keyGroups.put(groupArray)
            }
            return Intent(context, ProviderWebLoginActivity::class.java)
                .putExtra(EXTRA_PROVIDER_ID, provider.providerId)
                .putExtra(EXTRA_PROVIDER_NAME, provider.providerName)
                .putExtra(EXTRA_LOGIN_URL, loginConfig.loginUrl)
                .putExtra(EXTRA_COOKIE_KEY_GROUPS, keyGroups.toString())
        }

        fun clearWebLoginState() {
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }
            WebStorage.getInstance().deleteAllData()
        }
    }

}
