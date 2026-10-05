package org.feeluown.mobile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings

/**
 * Small permission bridge opened from the multiplatform settings screen.
 * Enabling is explicit; closing the overlay persists disabled until the user opens this again.
 */
class FloatingLyricsPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Settings.canDrawOverlays(this)) {
            setEnabled(true)
            finish()
        } else {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this)) {
            setEnabled(true)
            finish()
        }
    }

    private fun setEnabled(enabled: Boolean) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    companion object {
        const val PREFS = "ming_floating_lyrics"
        const val KEY_ENABLED = "enabled"
    }
}
