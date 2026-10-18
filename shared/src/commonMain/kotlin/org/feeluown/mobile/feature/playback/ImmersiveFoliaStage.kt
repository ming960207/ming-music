package org.feeluown.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import dev.nucleusframework.webview.jsbridge.IJsMessageHandler
import dev.nucleusframework.webview.jsbridge.JsMessage
import dev.nucleusframework.webview.jsbridge.rememberWebViewJsBridge
import dev.nucleusframework.webview.web.WebView
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.rememberWebViewNavigator
import dev.nucleusframework.webview.web.rememberWebViewStateWithHTMLData
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.sample
import org.feeluown.mobile.resources.Res

/** Offline, pinned Folia lyricWindow. Playback and navigation remain owned by the native player. */
@OptIn(FlowPreview::class)
@Composable
internal fun ImmersiveGlyphStage(
    lyrics: List<LyricLine>, position: State<Long>, trackId: String?,
    accent: Color, fontSize: LyricFontSize, motion: Boolean, extras: Boolean,
    onSeek: (Long) -> Unit, onBlankTap: () -> Unit, modifier: Modifier, playing: Boolean = false,
) {
    var html by remember { mutableStateOf<String?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try { html = Res.readBytes("files/folia-stage.html").decodeToString() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { loadFailed = true; AppLogger.e("FoliaStage", "Offline stage resource failed", error) }
    }
    val content = html
    if (content == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(if (loadFailed) "舞台资源加载失败，请切换其他舞台" else "舞台正在加载", color = Color.White.copy(alpha = 0.65f))
        }
        return
    }
    val webState = rememberWebViewStateWithHTMLData(content, baseUrl = "https://stage.mingmusic.local/", mimeType = "text/html")
    webState.webSettings.apply {
        isJavaScriptEnabled = true
        supportZoom = false
        backgroundColor = Color.Transparent
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false
        androidWebSettings.allowFileAccess = false
        androidWebSettings.domStorageEnabled = false
        desktopWebSettings.transparent = true
        desktopWebSettings.enableClipboard = false
        desktopWebSettings.enableNavigationGestures = false
    }
    val navigator = rememberWebViewNavigator()
    val bridge = rememberWebViewJsBridge(navigator)
    val latestSeek by rememberUpdatedState(onSeek)
    val latestBlank by rememberUpdatedState(onBlankTap)
    val uriHandler = LocalUriHandler.current
    DisposableEffect(bridge, uriHandler) {
        val handler = object : IJsMessageHandler {
            override fun methodName() = "stageAction"
            override fun handle(message: JsMessage, navigator: WebViewNavigator?, callback: (String) -> Unit) {
                when (val action = parseFoliaStageAction(message.params)) {
                    null -> Unit
                    else -> when (action.action) {
                        "blank" -> latestBlank()
                        "seek" -> action.positionMs?.let(latestSeek)
                        "source" -> uriHandler.openUri("https://github.com/ming960207/ming-music/tree/main/web/folia-stage")
                    }
                }
                callback("{}")
            }
        }
        bridge.register(handler)
        onDispose { bridge.unregister(handler) }
    }
    val lines = remember(lyrics) { foliaStageLines(lyrics) }
    val sceneScript = remember(lines, trackId, accent, fontSize, motion, extras) {
        fun channel(value: Float) = (value.coerceIn(0f, 1f) * 255).toInt().toString(16).padStart(2, '0')
        foliaStageSceneScript(FoliaStageScene(lines, trackId.orEmpty(),
            "#${channel(accent.red)}${channel(accent.green)}${channel(accent.blue)}",
            when (fontSize) { LyricFontSize.Small -> 0.88f; LyricFontSize.Medium -> 1f; LyricFontSize.Large -> 1.15f },
            motion, extras))
    }
    val ready = !webState.isLoading && webState.pageTitle == "ming-folia-ready"
    LaunchedEffect(ready, sceneScript) {
        if (ready) {
            navigator.evaluateJavaScript(sceneScript)
            navigator.evaluateJavaScript("window.MingFoliaStage.updateClock(${position.value.coerceAtLeast(0)},$playing);")
        }
    }
    LaunchedEffect(ready, position, playing) {
        if (ready) snapshotFlow { position.value }.sample(50).collect { time ->
            navigator.evaluateJavaScript("window.MingFoliaStage.updateClock(${time.coerceAtLeast(0)},$playing);")
        }
    }
    WebView(state = webState, navigator = navigator, webViewJsBridge = bridge, modifier = modifier.fillMaxSize(),
        onDispose = { navigator.evaluateJavaScript("window.MingFoliaStage?.destroy();") })
}
