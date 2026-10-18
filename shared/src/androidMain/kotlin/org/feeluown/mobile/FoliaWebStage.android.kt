package org.feeluown.mobile

import android.annotation.SuppressLint
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

private const val STAGE_ASSET_URL = "file:///android_asset/folia_stage/index.html"

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
    val webView = remember(context) { runCatching { WebView(context) }.getOrNull() }
        ?: return false // No Android WebView provider: fall back to the original Compose stage.
    var pageReady by remember(webView) { mutableStateOf(false) }

    DisposableEffect(webView) {
        webView.setBackgroundColor(AndroidColor.rgb(8, 15, 32))
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = true
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
        }
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                request?.url?.toString()?.startsWith("file:///android_asset/folia_stage/") != true

            override fun onPageFinished(view: WebView?, url: String?) {
                pageReady = url == STAGE_ASSET_URL
            }
        }
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun toggleControls() { webView.post { currentToggle() } }
        }, "MingMusicStage")
        webView.loadUrl(STAGE_ASSET_URL)
        onDispose {
            pageReady = false
            webView.removeJavascriptInterface("MingMusicStage")
            webView.stopLoading()
            webView.destroy()
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
    AndroidView(factory = { webView }, modifier = modifier)
    return true
}
