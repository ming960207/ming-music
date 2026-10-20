"""Regression guards for the 0.1.6 accidental lockscreen takeover / empty lyrics bug."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]


def source(relative):
    return (ROOT / relative).read_text(encoding="utf-8")


class LockscreenStageContractTest(unittest.TestCase):
    def test_immersive_stage_never_launches_an_alternate_activity_automatically(self):
        launcher = source(
            "shared/src/androidMain/kotlin/org/feeluown/mobile/LockscreenStageLauncher.android.kt"
        )
        self.assertIn("activity?.setShowWhenLocked(true)", launcher)
        self.assertIn("activity?.setShowWhenLocked(false)", launcher)
        self.assertNotIn("startActivity(", launcher)
        self.assertNotIn("LockscreenFoliaActivity::class", launcher)

    def test_main_folia_stage_owns_keyguard_registration(self):
        immersive = source(
            "shared/src/commonMain/kotlin/org/feeluown/mobile/feature/playback/ImmersivePlayerScreen.kt"
        )
        self.assertIn("OpenLockscreenStageWhenEnabled(", immersive)
        self.assertIn("ImmersiveGlyphStage(lyrics, alignedPosition", immersive)

    def test_notification_lockscreen_reads_resolved_lyrics_not_just_raw_service(self):
        activity = source(
            "androidApp/src/main/kotlin/org/feeluown/mobile/LockscreenFoliaActivity.kt"
        )
        self.assertIn("graph.playbackSession.state.collectAsStateWithLifecycle()", activity)
        self.assertIn("sessionState.lyrics ?: rawState.lyrics", activity)
        self.assertIn("association.alignmentOffsetMs", activity)
        self.assertIn("graph.playback.presentation.lyricFontSize", activity)

    def test_lockscreen_folia_reuses_lyric_alignment_and_real_webgl_renderer(self):
        stage = source(
            "shared/src/commonMain/kotlin/org/feeluown/mobile/feature/playback/LockscreenFoliaPlayer.kt"
        )
        self.assertIn("position.value - alignmentOffsetMs", stage)
        self.assertIn("ImmersiveGlyphStage(", stage)
        self.assertIn("immersiveLyricSeekTarget(value, alignmentOffsetMs", stage)
        self.assertIn("FoliaMovingBackdrop(", stage)


if __name__ == "__main__":
    unittest.main()
