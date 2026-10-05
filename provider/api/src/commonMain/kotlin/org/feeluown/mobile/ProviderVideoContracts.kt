package org.feeluown.mobile

enum class ProviderVideoStatKind {
    View,
    Like,
    Coin,
    Favorite,
    Comment,
    Danmaku,
    Share,
}

data class ProviderVideoStat(
    val kind: ProviderVideoStatKind,
    val value: Long,
)

data class ProviderVideoMetadata(
    val description: String = "",
    val publishedAt: String? = null,
    val stats: List<ProviderVideoStat> = emptyList(),
    val width: Int? = null,
    val height: Int? = null,
)
