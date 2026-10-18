package org.feeluown.mobile

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class FoliaStageWord(val text: String, val startTime: Double, val endTime: Double)

@Serializable
internal data class FoliaStageLine(
    val fullText: String, val startTime: Double, val endTime: Double,
    val words: List<FoliaStageWord>, val translation: String? = null, val romanization: String? = null,
)

@Serializable
internal data class FoliaStageScene(
    val lines: List<FoliaStageLine>, val seed: String, val accent: String,
    val fontScale: Float, val motion: Boolean, val extras: Boolean,
)

/** Folia consumes seconds, including the original word boundaries; never convert through HTML. */
internal fun foliaStageLines(lyrics: List<LyricLine>): List<FoliaStageLine> {
    val lines = lyrics.filter { it.timeMs >= 0 && it.timeMs != Long.MAX_VALUE && it.text.isNotBlank() }
        .sortedBy { it.timeMs }
    return lines.mapIndexed { index, line ->
        val start = line.timeMs / 1_000.0
        val next = lines.getOrNull(index + 1)?.timeMs?.div(1_000.0) ?: (start + 5)
        val words = line.words.orEmpty().filter { it.startMs >= 0 && it.startMs != Long.MAX_VALUE }.map {
            FoliaStageWord(it.text, it.startMs / 1_000.0,
                it.startMs / 1_000.0 + it.durationMs.coerceAtLeast(0) / 1_000.0)
        }
        FoliaStageLine(line.text, start, maxOf(start + 0.001, words.maxOfOrNull { it.endTime } ?: next),
            words, line.translation, line.romanization)
    }
}

internal fun foliaStageSceneScript(scene: FoliaStageScene): String =
    "window.MingFoliaStage.setScene(${Json.encodeToString(scene)});"

@Serializable
internal data class FoliaStageAction(val action: String, val positionMs: Long? = null)

internal fun parseFoliaStageAction(params: String): FoliaStageAction? = runCatching {
    Json.decodeFromString<FoliaStageAction>(params).takeIf {
        it.action == "blank" || it.action == "source" ||
            (it.action == "seek" && it.positionMs != null && it.positionMs >= 0 && it.positionMs < Long.MAX_VALUE)
    }
}.getOrNull()
