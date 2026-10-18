package org.feeluown.mobile

import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.webkit.WebResourceError
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject

private const val STAGE_ASSET_PATH = "folia_stage/index.html"
private const val STAGE_BASE_URL = "https://appassets.androidplatform.net/folia_stage/"
private const val STAGE_LOG = "MingFoliaStage"
private const val STAGE_BUILD = "VISUAL-R4"

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
    var canvasInfo by remember(webView) { mutableStateOf("无首帧信息") }
    var stageMetrics by remember(webView) { mutableStateOf("等待 JS 采样") }
    var showDiagnosticDetails by remember { mutableStateOf(true) }

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
        webView.setBackgroundColor(AndroidColor.TRANSPARENT)
        // Canvas-heavy WebView content needs Chromium's accelerated compositor.
        // SOFTWARE caused "JS first frame received" with an invisible surface
        // on affected Compose/OEM WebView combinations.
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
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
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR &&
                    (message.message().contains("Uncaught") ||
                        message.message().contains("SyntaxError") ||
                        message.message().contains("ReferenceError"))
                ) {
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

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                fail("WebView 渲染进程退出; crash=" + (detail?.didCrash() ?: false))
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.i(STAGE_LOG, "HTML loaded: " + url + "; waiting for Canvas handshake; attached=" +
                    webView.isAttachedToWindow + " size=" + webView.width + "x" + webView.height)
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
                        canvasInfo = "Canvas " + width + "x" + height
                        Log.i(STAGE_LOG, "Canvas first frame: " + width + "x" + height +
                            "; WebView attached=" + webView.isAttachedToWindow +
                            " measured=" + webView.width + "x" + webView.height)
                        pageReady = true
                        errorMessage = null
                    }
                }
            }

            @JavascriptInterface
            fun stageMetrics(metrics: String) {
                webView.post {
                    stageMetrics = metrics.take(220)
                    Log.i(STAGE_LOG, "Canvas status: " + stageMetrics)
                }
            }

            @JavascriptInterface
            fun stageFailure(reason: String) = fail(reason)
        }, "MingMusicStage")
        // Load actual bytes from the installed APK. Do not rely on file://
        // resource handling or an unverified onPageFinished callback.
        // Loading on webView.post() alone does NOT ensure attachment/measurement.
        // Start only when a real Android window and non-zero viewport both exist.
        var loadStarted = false
        val loadStage = Runnable {
            if (loadStarted || !webView.isAttachedToWindow ||
                webView.width <= 0 || webView.height <= 0) return@Runnable
            loadStarted = true
            Log.i(STAGE_LOG, "Starting stage attached=" + webView.isAttachedToWindow +
                " size=" + webView.width + "x" + webView.height)
            webView.loadDataWithBaseURL(
                STAGE_BASE_URL, html.getOrThrow(), "text/html", "UTF-8", null,
            )
        }
        val attachment = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                webView.post(loadStage)
            }
            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        val layout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            webView.post(loadStage)
        }
        webView.addOnAttachStateChangeListener(attachment)
        webView.addOnLayoutChangeListener(layout)
        webView.post(loadStage)
        onDispose {
            webView.removeOnAttachStateChangeListener(attachment)
            webView.removeOnLayoutChangeListener(layout)
            webView.removeCallbacks(loadStage)
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
            AndroidView(
                factory = { activityContext ->
                    FrameLayout(activityContext).apply {
                        setBackgroundColor(AndroidColor.rgb(8, 21, 42))
                        clipChildren = true
                        clipToPadding = true
                        addView(
                            webView,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxSize().graphicsLayer { clip = true },
            )
        }
        // Native Compose overlay is intentionally independent of WebView's
        // bitmap: if this marker is missing, the installed APK isn't this build.
        Column(
            modifier = Modifier.align(Alignment.TopCenter)
                .padding(top = 108.dp)
                .background(Color(0xD9132747))
                .clickable { showDiagnosticDetails = !showDiagnosticDetails }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                STAGE_BUILD + " · " + (if (pageReady) "JS首帧已收到" else "等待JS首帧") +
                    (if (showDiagnosticDetails) " · 点按收起" else " · 点按展开详情"),
                color = Color.White,
            )
            if (showDiagnosticDetails) {
                Text(canvasInfo + " / View " + webView.width + "x" + webView.height, color = Color(0xFFB9E5FF))
                Text(stageMetrics.take(160), color = Color(0xFFB9E5FF))
            }
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
