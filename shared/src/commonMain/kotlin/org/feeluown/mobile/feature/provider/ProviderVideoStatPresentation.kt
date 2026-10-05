package org.feeluown.mobile

internal val ProviderVideoStat.label: String
    get() = when (kind) {
        ProviderVideoStatKind.View -> "播放"
        ProviderVideoStatKind.Like -> "点赞"
        ProviderVideoStatKind.Coin -> "投币"
        ProviderVideoStatKind.Favorite -> "收藏"
        ProviderVideoStatKind.Comment -> "评论"
        ProviderVideoStatKind.Danmaku -> "弹幕"
        ProviderVideoStatKind.Share -> "分享"
    }
