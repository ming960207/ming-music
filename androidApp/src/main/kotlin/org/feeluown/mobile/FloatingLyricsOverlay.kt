package org.feeluown.mobile

import android.content.Context
import android.graphics.Color
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
    private var locked = false
    private var colorIndex = 0
    private val lyricColors = intArrayOf(Color.WHITE, Color.YELLOW, Color.CYAN, Color.GREEN, Color.MAGENTA)
    private var lastSnapshot: Snapshot? = null
    private var lastLyrics: String? = null
    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == FloatingLyricsPermissionActivity.KEY_ENABLED) {
            lastSnapshot?.let(::render)
        }
    }
    private val prefs = appContext.getSharedPreferences(FloatingLyricsPermissionActivity.PREFS, Context.MODE_PRIVATE)

    fun start() {
        if (collectJob != null) return
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        collectJob = scope.launch {
            playbackSession.state.collect { state ->
                render(
                    Snapshot(
                        enabled = true,
                        status = state.status,
                        positionMs = state.lyricsPositionMs,
                        durationMs = state.durationMs,
                        lyrics = state.lyrics,
                    ),
                )
            }
        }
    }

    fun close() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        collectJob?.cancel()
        collectJob = null
        stopTicker()
        removeOverlay()
    }

    private fun render(snapshot: Snapshot) {
        lastSnapshot = snapshot
        overlayEnabled = prefs.getBoolean(FloatingLyricsPermissionActivity.KEY_ENABLED, false)
        if (!overlayEnabled || !snapshot.enabled || !Settings.canDrawOverlays(appContext)) {
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
        // Keep the overlay window alive across track transitions. Lyrics commonly become null
        // briefly while the next track is being resolved; removing the window here caused both
        // stale lyrics and a position reset when it was recreated.
        if (snapshot.lyrics != lastLyrics) {
            lastLyrics = snapshot.lyrics
            lastPayload = buildStatusBarLyricsPayload(snapshot.lyrics, snapshot.durationMs)
            lyricText?.text = ""
        }
        lastPositionMs = snapshot.positionMs.coerceAtLeast(0L)
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
        val lockButton = view.findViewById<ImageButton>(R.id.floatingLyricsLock)
        val colorButton = view.findViewById<ImageButton>(R.id.floatingLyricsColor)
        locked = prefs.getBoolean("locked", false)
        colorIndex = prefs.getInt("color_index", 0).coerceIn(0, lyricColors.lastIndex)
        lyricText?.setTextColor(lyricColors[colorIndex])
        lockButton.alpha = if (locked) 1f else 0.55f
        lockButton.setOnClickListener {
            locked = !locked
            prefs.edit().putBoolean("locked", locked).apply()
            lockButton.alpha = if (locked) 1f else 0.55f
        }
        colorButton.setOnClickListener {
            colorIndex = (colorIndex + 1) % lyricColors.size
            prefs.edit().putInt("color_index", colorIndex).apply()
            lyricText?.setTextColor(lyricColors[colorIndex])
        }
        view.findViewById<ImageButton>(R.id.floatingLyricsClose).setOnClickListener {
            // Direct close: hide immediately for this process session. The persistent settings
            // switch remains the authoritative way to enable it again.
            overlayEnabled = false
            prefs.edit().putBoolean(FloatingLyricsPermissionActivity.KEY_ENABLED, false).apply()
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
            x = prefs.getInt(KEY_POSITION_X, 0)
            y = prefs.getInt(KEY_POSITION_Y, DEFAULT_POSITION_Y)
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        lyricText?.setOnTouchListener { _, event ->
            if (locked) return@setOnTouchListener false
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
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    prefs.edit()
                        .putInt(KEY_POSITION_X, params.x)
                        .putInt(KEY_POSITION_Y, params.y)
                        .apply()
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
            while (prefs.getBoolean(FloatingLyricsPermissionActivity.KEY_ENABLED, false) && root != null) {
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
        const val KEY_POSITION_X = "position_x"
        const val KEY_POSITION_Y = "position_y"
        const val DEFAULT_POSITION_Y = 160
    }
}
