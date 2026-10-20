package org.feeluown.mobile

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

/**
 * Dedicated foreground Keyguard Activity: its window remains eligible for display when the
 * screen is locked. Unlike the app navigation surface, it observes the process playback
 * service directly and never depends on MainActivity remaining resumed.
 */
class LockscreenFoliaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            finish()
            return
        }
        if (!lockscreenStageEnabled()) {
            // A notification tap without the opt-in should open the ordinary player.
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        setShowWhenLocked(true)
        enableEdgeToEdge()
        setContent {
            val playback by FuoPlaybackService.playbackState.collectAsStateWithLifecycle()
            BackHandler { finish() }
            LockscreenFoliaPlayer(
                state = playback,
                onClose = { finish() },
                onSeek = { position ->
                    (application as FuoEvolveApplication).appUiGraph.playbackSession.seekTo(position)
                },
                onPlayPause = {
                    if (playback.status == PlayerStatus.Playing) FuoPlaybackService.pause(this)
                    else FuoPlaybackService.resume(this)
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (!lockscreenStageEnabled()) finish()
    }

    private fun lockscreenStageEnabled(): Boolean =
        getSharedPreferences("ming_immersive_lockscreen", MODE_PRIVATE)
            .getBoolean("enabled", false)
}
