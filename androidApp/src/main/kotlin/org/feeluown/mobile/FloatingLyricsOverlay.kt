package org.feeluown.mobile

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.feeluown.mobile.playback.api.PlaybackSession
import org.feeluown.mobile.playback.api.PlaybackSessionStatus

/**
 * Process-scoped Android floating lyrics overlay.
 *
 * The overlay never owns playback state. It observes PlaybackSession and delegates
 * controls to the same session used by the app/Media3 runtime.
 */
internal class FloatingLyricsOverlay(
    context: Context,
    private val playbackSession: PlaybackSession,
    private val enabled: Flow<Boolean>,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var lyricText: TextView? = null
    private var collectJob: Job? = null
    private var tickerJob: Job? = null
    private var lastPayload: StatusBarLyricsPayload = StatusBarLyricsPayload.Empty
    private var lastPositionMs: Long = 0L
    private var overlayEnabled = false

    fun start() {
        if (collectJob != null) return
        collectJob = scope.launch {
            combine(playbackSession.state, enabled.distinctUntilChanged()) { state, setting ->
                Snapshot(
                    enabled = setting,
                    status = state.status,
                    positionMs = state.lyricsPositionMs,
                    durationMs = state.durationMs,
                    lyrics = state.lyrics,
                )
            }.collect(::render)
        }
    }

    fun close() {
        collectJob?.cancel()
        collectJob = null
        stopTicker()
        removeOverlay()
    }

    private fun render(snapshot: Snapshot) {
        overlayEnabled = snapshot.enabled
        if (!snapshot.enabled || !Settings.canDrawOverlays(appContext)) {
            removeOverlay()
            return
        }
        if (snapshot.status == PlaybackSessionStatus.Idle ||
            snapshot.status == PlaybackSessionStatus.Error ||
            snapshot.status == PlaybackSessionStatus.Ended
        ) {
            removeOverlay()
            return
        }
        lastPayload = buildStatusBarLyricsPayload(snapshot.lyrics, snapshot.durationMs)
        lastPositionMs = snapshot.positionMs.coerceAtLeast(0L)
        if (lastPayload == StatusBarLyricsPayload.Empty) {
            removeOverlay()
            return
        }
        ensureOverlay()
        updateLyric(lastPositionMs)
        if (snapshot.status == PlaybackSessionStatus.Playing) startTicker() else stopTicker()
    }

    private fun ensureOverlay() {
        if (root != null) return
        val view = LayoutInflater.from(appContext).inflate(R.layout.floating_lyrics_overlay, null)
        lyricText = view.findViewById(R.id.floatingLyricsText)
        view.findViewById<ImageButton>(R.id.floatingLyricsPrevious).setOnClickListener { playbackSession.previous() }
        view.findViewById<ImageButton>(R.id.floatingLyricsToggle).setOnClickListener { playbackSession.toggle() }
        view.findViewById<ImageButton>(R.id.floatingLyricsNext).setOnClickListener { playbackSession.next() }
        view.findViewById<ImageButton>(R.id.floatingLyricsClose).setOnClickListener {
            // Direct close: hide immediately for this process session. The persistent settings
            // switch remains the authoritative way to enable it again.
            overlayEnabled = false
            removeOverlay()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 160
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - downX).toInt()
                    params.y = startY + (event.rawY - downY).toInt()
                    runCatching { windowManager.updateViewLayout(view, params) }
                    true
                }
                else -> false
            }
        }
        runCatching { windowManager.addView(view, params) }
            .onSuccess { root = view }
            .onFailure { AppLogger.w(TAG, "Unable to show floating lyrics", it) }
    }

    private fun removeOverlay() {
        val view = root ?: return
        root = null
        lyricText = null
        runCatching { windowManager.removeView(view) }
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (overlayEnabled && root != null) {
                val state = playbackSession.state.value
                updateLyric(state.lyricsPositionMs)
                delay(100L)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun updateLyric(positionMs: Long) {
        val text = when (val payload = lastPayload) {
            StatusBarLyricsPayload.Empty -> ""
            is StatusBarLyricsPayload.Text -> payload.text.lineSequence().firstOrNull().orEmpty()
            is StatusBarLyricsPayload.Timed -> {
                payload.lines.lastOrNull { positionMs >= it.beginMs }?.let { line ->
                    buildString {
                        append(line.text)
                        line.translation?.takeIf(String::isNotBlank)?.let { append("\n").append(it) }
                    }
                }.orEmpty()
            }
        }
        lyricText?.text = text
    }

    private data class Snapshot(
        val enabled: Boolean,
        val status: PlaybackSessionStatus,
        val positionMs: Long,
        val durationMs: Long,
        val lyrics: String?,
    )

    private companion object {
        const val TAG = "FloatingLyricsOverlay"
    }
}
