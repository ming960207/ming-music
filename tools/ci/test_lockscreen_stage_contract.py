"""Prevent the 0.1.6 blank lockscreen activity regression."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]


def source(relative):
    return (ROOT / relative).read_text(encoding="utf-8")


class LockscreenStageContractTest(unittest.TestCase):
    def test_keyguard_reuses_existing_activity_not_duplicate_player(self):
        launcher = source(
            "shared/src/androidMain/kotlin/org/feeluown/mobile/LockscreenStageLauncher.android.kt"
        )
        self.assertIn("activity?.setShowWhenLocked(true)", launcher)
        self.assertIn("activity?.setShowWhenLocked(false)", launcher)
        self.assertIn("Lifecycle.Event.ON_PAUSE", launcher)
        self.assertNotIn("startActivity(", launcher)
        manifest = source("androidApp/src/main/AndroidManifest.xml")
        self.assertNotIn('.LockscreenFoliaActivity', manifest)
        self.assertFalse((ROOT / "androidApp/src/main/kotlin/org/feeluown/mobile/LockscreenFoliaActivity.kt").exists())
        self.assertFalse((ROOT / "shared/src/commonMain/kotlin/org/feeluown/mobile/feature/playback/LockscreenFoliaPlayer.kt").exists())

    def test_media_card_returns_original_player(self):
        service = source("androidApp/src/main/kotlin/org/feeluown/mobile/FuoPlaybackService.kt")
        self.assertIn("Intent(this, MainActivity::class.java)", service)
        self.assertNotIn("Intent(this, LockscreenFoliaActivity::class.java)", service)

    def test_folia_renderer_keeps_associated_lyrics_and_alignment(self):
        immersive = source(
            "shared/src/commonMain/kotlin/org/feeluown/mobile/feature/playback/ImmersivePlayerScreen.kt"
        )
        self.assertIn("OpenLockscreenStageWhenEnabled(", immersive)
        self.assertIn("LocalPlaybackLyricsPort.current.associationState", immersive)
        self.assertIn("ImmersiveGlyphStage(lyrics, alignedPosition", immersive)
        self.assertIn("rememberKaraokePositionMs(", immersive)
        self.assertIn("onBlankTap = { chromeVisible = !chromeVisible }", immersive)


if __name__ == "__main__":
    unittest.main()
