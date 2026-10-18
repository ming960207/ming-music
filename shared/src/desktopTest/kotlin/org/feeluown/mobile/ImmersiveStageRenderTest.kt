package org.feeluown.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Native Compose renders of the actual stage components; no HTML/mock recreation. */
@OptIn(ExperimentalComposeUiApi::class)
class ImmersiveStageRenderTest {
    @Test
    fun renderAllStylesAtPhoneAndLandscapeSizes() {
        val lyrics = listOf(
            LyricLine(0, "晚风吹过城市的灯火"),
            LyricLine(8_000, "我们追着星光奔跑", translation = "We follow the stars into the night"),
            LyricLine(16_000, "把未完成的梦唱给你听", words = listOf(
                LyricWord(16_000, 1_000, "把"), LyricWord(17_000, 1_200, "未完成的"),
                LyricWord(18_200, 1_200, "梦"), LyricWord(19_400, 2_600, "唱给你听"),
            ), translation = "Let every unfinished dream become a song"),
            LyricLine(26_000, "让旋律越过山海与你相遇"),
            LyricLine(36_000, "所有美好终将如约而至"),
        )
        val accent = Color(0xFFC6DAB0)
        val position = mutableStateOf(18_800L)
        val clock = mutableStateOf(8f)
        for ((width, height) in listOf(430 to 820, 960 to 480)) {
            for (style in ImmersiveStageStyle.entries) {
                val scene = ImageComposeScene(width = width, height = height) {
                    MaterialTheme(colorScheme = darkColorScheme(primary = accent)) {
                        CompositionLocalProvider(LocalContentColor provides Color.White) {
                        Box(Modifier.fillMaxSize()) {
                            if (style.foliaStyle != null) FoliaMovingBackdrop(null, accent, style.foliaStyle, clock)
                            else ImmersiveStageBackdrop(null, accent, clock, style)
                            Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("晚风与星光 · ${style.title}", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                                if (style == ImmersiveStageStyle.Vinyl) {
                                    ImmersiveVinylCover(null, accent, clock, Modifier.size(if (height > width) 230.dp else 120.dp))
                                }
                                if (style == ImmersiveStageStyle.Glyph) ImmersiveGlyphStage(lyrics, position, clock, "fixture",
                                    accent, LyricFontSize.Medium, true, true, {}, {}, Modifier.weight(1f).fillMaxWidth())
                                else if (style.foliaStyle != null) FoliaWordStage(lyrics, position, "fixture", style.foliaStyle,
                                    accent, false, true, LyricFontSize.Medium, {}, Modifier.weight(1f).fillMaxWidth())
                                else if (style == ImmersiveStageStyle.Focus) StageFocusLyrics(lyrics, position, true, width > height,
                                    LyricFontSize.Medium, accent, false, Modifier.weight(1f), {})
                                else StageFlowLyrics(lyrics, position, true, 0, 60_000, LyricFontSize.Medium,
                                    accent, false, "fixture", {}, Modifier.weight(1f).fillMaxWidth())
                            }
                        }
                        }
                    }
                }
                try {
                    repeat(8) { scene.render(it * 200_000_000L).close() }
                    val image = scene.render(2_000_000_000L)
                    try {
                        val data = image.encodeToData()!!
                        try {
                            val file = File("build/stage-previews/${style.name}-$width.png")
                            file.parentFile.mkdirs()
                            file.writeBytes(data.bytes)
                            assertTrue(file.length() > 5_000, "Stage should render visible content at $width x $height")
                        } finally { data.close() }
                    } finally { image.close() }
                } finally {
                    scene.close()
                }
            }
        }
    }
}
