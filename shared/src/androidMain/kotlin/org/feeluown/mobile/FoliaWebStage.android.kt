package org.feeluown.mobile

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.webkit.WebResourceError
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay
import android.graphics.Color as AndroidColor
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject

private const val STAGE_ASSET_PATH = "folia_stage/index.html"
private const val STAGE_BASE_URL = "https://appassets.androidplatform.net/folia_stage/"
private const val STAGE_LOG = "MingFoliaStage"

private fun foliaStagePayload(state: PlaybackState, lyricOffsetMs: Long): String {
    val lines = JSONArray()
    parseLyrics(state.lyrics).asSequence()
        .filter { it.text.isNotBlank() && it.timeMs >= 0 && it.timeMs < Long.MAX_VALUE }
        .take(1800)
        .forEach { line ->
            val words = JSONArray()
            line.words?.forEach { word ->
                words.put(JSONObject()
                    .put("time", word.startMs)
                    .put("duration", word.durationMs.coerceAtLeast(1L))
                    .put("text", word.text))
            }
            lines.put(JSONObject()
                .put("time", line.timeMs)
                .put("text", line.text)
                .put("translation", line.translation.orEmpty())
                .put("romanization", line.romanization.orEmpty())
                .put("words", words))
        }
    return JSONObject()
        .put("id", state.currentTrack?.id.orEmpty())
        .put("title", state.currentTrack?.title.orEmpty())
        .put("artist", state.currentTrack?.let(::artistAlbumLabel).orEmpty())
        .put("offset", lyricOffsetMs)
        .put("lines", lines)
        .toString()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun FoliaWebStage(
    state: PlaybackState,
    lyricOffsetMs: Long,
    style: FoliaStageStyle,
    onToggleControls: () -> Unit,
    modifier: Modifier,
): Boolean {
    val context = LocalContext.current
    val currentToggle by rememberUpdatedState(onToggleControls)
    val html = remember(context) {
        runCatching {
            context.assets.open(STAGE_ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.onFailure { Log.e(STAGE_LOG, "APK missing the stage asset", it) }
    }
    var retries by remember { mutableIntStateOf(0) }
    val webView = remember(context, retries) { runCatching { WebView(context) }.getOrNull() }
    var pageReady by remember(webView) { mutableStateOf(false) }
    var errorMessage by remember(webView) { mutableStateOf<String?>(null) }

    if (webView == null || html.isFailure) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (webView == null) "Android System WebView 无法初始化"
                else "APK 未打包光影舞台 HTML 资源",
                color = Color.White,
            )
        }
        return true
    }

    DisposableEffect(webView, html) {
        webView.setBackgroundColor(AndroidColor.rgb(8, 15, 32))
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            blockNetworkLoads = true
        }
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        fun fail(reason: String) {
            webView.post {
                Log.e(STAGE_LOG, "Stage error: " + reason)
                errorMessage = reason.take(220)
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.d(STAGE_LOG, "JS " + message.messageLevel() + ": " + message.message())
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    fail("JavaScript: " + message.message())
                }
                return true
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean = true // Do not navigate outside bundled HTML.

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) fail("HTML: " + error?.description)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.i(STAGE_LOG, "HTML loaded: " + url + "; waiting for Canvas handshake")
                view?.evaluateJavascript("typeof window.mingFolia === 'object'") { result ->
                    if (result != "true") fail("HTML 已加载但舞台脚本未初始化")
                }
            }
        }
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun toggleControls() { webView.post { currentToggle() } }

            @JavascriptInterface
            fun stageReady(width: Int, height: Int) {
                webView.post {
                    if (width > 0 && height > 0) {
                        Log.i(STAGE_LOG, "Canvas first frame: " + width + "x" + height)
                        pageReady = true
                        errorMessage = null
                    }
                }
            }

            @JavascriptInterface
            fun stageFailure(reason: String) = fail(reason)
        }, "MingMusicStage")
        // Load actual bytes from the installed APK. Do not rely on file://
        // resource handling or an unverified onPageFinished callback.
        webView.loadDataWithBaseURL(
            STAGE_BASE_URL, html.getOrThrow(), "text/html", "UTF-8", null,
        )
        onDispose {
            webView.removeJavascriptInterface("MingMusicStage")
            webView.stopLoading()
            webView.destroy()
        }
    }

    LaunchedEffect(webView, pageReady, errorMessage, retries) {
        if (!pageReady && errorMessage == null) {
            delay(6_000)
            if (!pageReady && errorMessage == null) {
                if (retries < 1) {
                    Log.w(STAGE_LOG, "No Canvas first frame: recreating WebView")
                    retries++
                } else {
                    errorMessage = "Canvas 启动超时，查看 MingFoliaStage 日志"
                }
            }
        }
    }

    LaunchedEffect(webView, pageReady, state.currentTrack?.id, state.lyrics, lyricOffsetMs) {
        if (pageReady) {
            webView.evaluateJavascript("window.mingFolia?.setSong(${foliaStagePayload(state, lyricOffsetMs)})", null)
        }
    }
    LaunchedEffect(webView, pageReady, state.positionMs, state.status) {
        if (pageReady) webView.evaluateJavascript(
            "window.mingFolia?.sync(${state.positionMs.coerceAtLeast(0L)},${state.status == PlayerStatus.Playing})",
            null,
        )
    }
    LaunchedEffect(webView, pageReady, style) {
        if (pageReady) {
            val mode = when (style) {
                FoliaStageStyle.Studio -> "studio"
                FoliaStageStyle.Light -> "lumiere"
                FoliaStageStyle.Poetry -> "poetry"
                FoliaStageStyle.Quiet -> "quiet"
                FoliaStageStyle.Classic -> "classic"
            }
            webView.evaluateJavascript("window.mingFolia?.setMode(${JSONObject.quote(mode)})", null)
        }
    }
    Box(modifier.fillMaxSize()) {
        key(webView) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        }
        if (!pageReady) {
            Column(
                modifier = Modifier.fillMaxSize().background(Color(0xEC08152A)).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (errorMessage == null) "正在初始化光影歌词舞台…"
                    else "光影舞台初始化失败",
                    color = Color.White,
                )
                if (errorMessage != null) {
                    Text(errorMessage.orEmpty(), color = Color(0xFFB4DDFF))
                    Button(onClick = { retries++ }) { Text("重新初始化舞台") }
                }
            }
        }
    }
    return true
}
