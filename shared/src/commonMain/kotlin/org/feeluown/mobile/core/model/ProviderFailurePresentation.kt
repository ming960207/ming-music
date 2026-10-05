package org.feeluown.mobile

/**
 * Application-layer presentation mapping for provider failures.
 * Stable failure kinds stay in :provider:api; localized/display text belongs above that boundary.
 */
val ProviderFailure.userMessage: String
    get() = when (kind) {
        ProviderFailureKind.LoginExpired -> "登录状态已失效，请重新登录后重试"
        ProviderFailureKind.RegionRestricted -> "当前地区暂不支持此内容"
        ProviderFailureKind.CopyrightUnavailable -> "该内容因版权或资源限制不可用"
        ProviderFailureKind.ContentUnavailable -> "当前内容暂不可用，请稍后重试"
        ProviderFailureKind.AccountUnavailable -> "无法读取账号信息，请重新登录或稍后重试"
        ProviderFailureKind.UnsupportedOperation -> "当前音源暂不支持该操作或内容"
        ProviderFailureKind.UpstreamContractChanged -> "音源接口响应已变化，请更新应用或稍后重试"
        ProviderFailureKind.Network -> "网络请求失败，请检查网络后重试"
        ProviderFailureKind.Unknown -> "音源请求失败，请稍后重试"
    }
