package org.feeluown.mobile

import android.app.KeyguardManager
import android.content.Intent
import android.util.Log
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.feeluown.mobile.playback.api.PlaybackSessionStatus

/**
 * Dedicated foreground Keyguard Activity: its window remains eligible for display when the
 * screen is locked. Unlike the app navigation surface, it observes the process playback
 * service directly and never depends on MainActivity remaining resumed.
 */
class LockscreenFoliaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recordStageEvent("onCreate")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            finish()
            return
        }
        if (!lockscreenStageEnabled()) {
            recordStageEvent("开关未开启")
            // A notification tap without the opt-in should open the ordinary player.
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        setShowWhenLocked(true)
        recordStageEvent("setShowWhenLocked=true")
        enableEdgeToEdge()
        setContent {
            // The service only contains raw provider lyrics. The app playback session owns
            // associated/replaced lyrics and timing: use the SAME source as RuntimeFullPlayer.
            val graph = remember { (application as FuoEvolveApplication).appUiGraph }
            val sessionState by graph.playbackSession.state.collectAsStateWithLifecycle()
            val rawState by FuoPlaybackService.playbackState.collectAsStateWithLifecycle()
            val association by graph.playback.lyrics.associationState.collectAsStateWithLifecycle()
            val sessionActive = sessionState.currentTrack != null
            val playback = rawState.copy(
                currentTrack = graph.playback.presentation.currentTrack ?: rawState.currentTrack,
                lyrics = if (sessionActive) sessionState.lyrics ?: rawState.lyrics else rawState.lyrics,
                positionMs = if (sessionActive) sessionState.positionMs else rawState.positionMs,
                durationMs = if (sessionActive) sessionState.durationMs else rawState.durationMs,
                status = if (sessionActive) when (sessionState.status) {
                    PlaybackSessionStatus.Idle -> PlayerStatus.Idle
                    PlaybackSessionStatus.Loading -> PlayerStatus.Loading
                    PlaybackSessionStatus.Playing -> PlayerStatus.Playing
                    PlaybackSessionStatus.Paused -> PlayerStatus.Paused
                    PlaybackSessionStatus.Error -> PlayerStatus.Error
                    PlaybackSessionStatus.Ended -> PlayerStatus.Ended
                } else rawState.status,
            )
            val offset = association.alignmentOffsetMs
                .takeIf { association.trackId == playback.currentTrack?.id } ?: 0L
            BackHandler { finish() }
            LockscreenFoliaPlayer(
                state = playback,
                alignmentOffsetMs = offset,
                fontSize = graph.playback.presentation.lyricFontSize,
                onClose = { finish() },
                onSeek = graph.playback.presentation::seekTo,
                onPlayPause = { graph.playbackSession.toggle() },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        recordStageEvent("onResume")
        if (!lockscreenStageEnabled()) finish()
    }

    override fun onPause() {
        recordStageEvent("onPause")
        super.onPause()
    }

    override fun onStop() {
        recordStageEvent("onStop")
        super.onStop()
    }

    override fun onDestroy() {
        recordStageEvent("onDestroy")
        super.onDestroy()
    }

    private fun recordStageEvent(phase: String) {
        val locked = (getSystemService(KEYGUARD_SERVICE) as? KeyguardManager)
            ?.isKeyguardLocked == true
        getSharedPreferences("ming_immersive_lockscreen", MODE_PRIVATE).edit()
            .putString("stage_last_phase", phase)
            .putLong("stage_last_timestamp", System.currentTimeMillis())
            .putBoolean("stage_last_keyguard", locked)
            .apply()
        Log.i("MingLockscreenStage", "$phase locked=$locked")
    }

    private fun lockscreenStageEnabled(): Boolean =
        getSharedPreferences("ming_immersive_lockscreen", MODE_PRIVATE)
            .getBoolean("enabled", false)
}
