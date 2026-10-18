package org.feeluown.mobile

internal enum class ImmersiveStageStyle(val title: String, val subtitle: String, val foliaStyle: FoliaStageStyle? = null) {
    Classic("经典", "随词句渐入", FoliaStageStyle.Classic),
    Light("流光", "字句与光点交织", FoliaStageStyle.Light),
    Poetry("浮名", "前后句之间的留白", FoliaStageStyle.Poetry),
    Quiet("静谧", "安静地听见每一句", FoliaStageStyle.Quiet),
    Flow("随行", "随歌词流动"),
    Focus("聚焦", "每一句都是主角"),
    Vinyl("唱片", "让旋律慢慢转动"),
}

internal fun immersiveActiveLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
    var low = 0
    var high = lines.lastIndex
    var result = -1
    while (low <= high) {
        val middle = (low + high).ushr(1)
        val time = lines[middle].timeMs
        if (time != Long.MAX_VALUE && time <= positionMs) {
            result = middle
            low = middle + 1
        } else {
            high = middle - 1
        }
    }
    return result
}

internal fun immersiveWordProgress(word: LyricWord, positionMs: Long): Float = when {
    positionMs < word.startMs -> 0f
    word.durationMs <= 0L -> 1f
    else -> ((positionMs - word.startMs).toDouble() / word.durationMs).toFloat().coerceIn(0f, 1f)
}

internal data class ImmersiveInterlude(val remainingMs: Long, val progress: Float)

/** Only a measured word ending (or the intro) can establish a silent interval. */
internal fun immersiveInterlude(lines: List<LyricLine>, positionMs: Long): ImmersiveInterlude? {
    val active = immersiveActiveLineIndex(lines, positionMs)
    val next = lines.getOrNull(active + 1)?.timeMs?.takeIf { it != Long.MAX_VALUE } ?: return null
    val start = if (active < 0) 0L else {
        lines[active].words?.maxOfOrNull { word ->
            word.startMs + word.durationMs.coerceIn(0L, Long.MAX_VALUE - word.startMs.coerceAtLeast(0))
        } ?: return null
    }
    if (next - start < 5_000L || positionMs < start || positionMs >= next) return null
    return ImmersiveInterlude(next - positionMs, ((positionMs - start).toDouble() / (next - start)).toFloat())
}

/** Untimed credits and overflowing alignment offsets must never seek to an invalid position. */
internal fun immersiveLyricSeekTarget(lineTimeMs: Long, offsetMs: Long, durationMs: Long): Long? {
    if (lineTimeMs < 0L || lineTimeMs == Long.MAX_VALUE) return null
    val time = if (offsetMs > 0 && lineTimeMs > Long.MAX_VALUE - offsetMs) Long.MAX_VALUE
        else (lineTimeMs + offsetMs).coerceAtLeast(0L)
    return if (durationMs > 0L) time.coerceAtMost(durationMs) else time
}
