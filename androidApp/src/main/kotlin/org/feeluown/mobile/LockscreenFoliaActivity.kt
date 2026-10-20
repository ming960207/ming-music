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
