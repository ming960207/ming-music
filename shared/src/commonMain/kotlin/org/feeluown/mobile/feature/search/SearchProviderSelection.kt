package org.feeluown.mobile

/**
 * Open catalogs are always queried by global search.
 *
 * They are intentionally independent from the normal provider enable/search selection:
 * finding a result in QQ Music, NetEase, etc. must not suppress Audius/Jamendo results.
 * Explicit single-provider search is still controlled by the selected provider.
 */
private val ALWAYS_ON_GLOBAL_SEARCH_PROVIDER_IDS = listOf("audius", "jamendo", "openverse")

/**
 * Resolves the provider subset/order used by global search directly from persisted app settings.
 * This keeps search construction independent from `FuoPlayerController` provider presentation state.
 *
 * Normal providers respect the user's enabled/search selection. Open fallback catalogs are appended
 * unconditionally so they participate in the same concurrent global search rather than only being
 * consulted after conventional providers return no results.
 */
fun AppSettings.searchProviderIdsForFeature(): List<String> {
    val enabledIds = enabledProviderIds
    val selectedIds = searchProviderIds.ifEmpty { enabledIds }
    val normalProviderIds = (providerOrderIds + DEFAULT_PROVIDER_ORDER_IDS + enabledIds)
        .distinct()
        .filter { providerId -> providerId in enabledIds && providerId in selectedIds }

    return (normalProviderIds + ALWAYS_ON_GLOBAL_SEARCH_PROVIDER_IDS).distinct()
}

fun AppSettings.hasSearchProvider(providerId: String): Boolean =
    providerId in enabledProviderIds || providerId in ALWAYS_ON_GLOBAL_SEARCH_PROVIDER_IDS
