package org.feeluown.mobile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class ImmersiveFoliaSceneTest {
    @Test fun originalWordTimingAndTextSurviveTheBridgeInSeconds() {
        val line = LyricLine(1_000, "你好🌙e\u0301", words = listOf(
            LyricWord(1_000, 1_200, "你好"), LyricWord(2_500, 600, "🌙e\u0301")))
        val mapped = foliaStageLines(listOf(line)).single()
        assertEquals(line.text, mapped.fullText)
        assertEquals(listOf("你好", "🌙e\u0301"), mapped.words.map { it.text })
        assertEquals(1.0, mapped.startTime)
        assertEquals(3.1, mapped.endTime)
        assertEquals(2.5, mapped.words.last().startTime)
    }
    @Test fun creditsAreExcludedAndLineOnlyLyricsHaveAUsableEndTime() {
        val lines = foliaStageLines(listOf(LyricLine(Long.MAX_VALUE, "credit"), LyricLine(-1, "metadata"),
            LyricLine(4_000, "后一句"), LyricLine(0, "前一句"), LyricLine(2_000, " ")))
        assertEquals(listOf("前一句", "后一句"), lines.map { it.fullText })
        assertEquals(4.0, lines.first().endTime)
        assertEquals(9.0, lines.last().endTime)
        assertTrue(lines.all { it.words.isEmpty() })
    }
    @Test fun lyricContentIsSerializedAsDataAndNeverInjectedIntoTheHtml() {
        val text = "\");globalThis.injected=true;//</script>\n🌙"
        val scene = FoliaStageScene(foliaStageLines(listOf(LyricLine(0, text))), "fixture", "#ffffff", 1f, true, true)
        val script = foliaStageSceneScript(scene)
        val data = Json.parseToJsonElement(script.removePrefix("window.MingFoliaStage.setScene(").removeSuffix(");"))
        assertEquals(text, data.jsonObject["lines"]!!.toString().let {
            Json.decodeFromString<List<FoliaStageLine>>(it).single().fullText
        })
        assertFalse(script.startsWith(text))
    }
    @Test fun onlyNarrowValidatedStageActionsReachThePlayer() {
        assertEquals(1_200L, parseFoliaStageAction("{\"action\":\"seek\",\"positionMs\":1200}")?.positionMs)
        assertEquals("blank", parseFoliaStageAction("{\"action\":\"blank\"}")?.action)
        assertNull(parseFoliaStageAction("{\"action\":\"seek\",\"positionMs\":-1}"))
        assertNull(parseFoliaStageAction("{\"action\":\"seek\"}"))
        assertNull(parseFoliaStageAction("{\"action\":\"openUrl\",\"url\":\"https://example.com\"}"))
        assertNull(parseFoliaStageAction("invalid"))
    }
}
