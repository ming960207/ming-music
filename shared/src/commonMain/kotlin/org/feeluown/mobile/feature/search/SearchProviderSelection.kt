package org.feeluown.mobile

/**
 * Resolves the provider subset/order used by global search directly from persisted app settings.
 * This keeps search construction independent from `FuoPlayerController` provider presentation state.
 */
fun AppSettings.searchProviderIdsForFeature(): List<String> {
    val enabledIds = enabledProviderIds.ifEmpty { DEFAULT_ENABLED_PROVIDER_IDS }
    val selectedIds = searchProviderIds.ifEmpty { enabledIds }
    return (providerOrderIds + DEFAULT_PROVIDER_ORDER_IDS + enabledIds)
        .distinct()
        .filter { providerId -> providerId in enabledIds && providerId in selectedIds }
}

fun AppSettings.hasSearchProvider(providerId: String): Boolean =
    providerId in enabledProviderIds.ifEmpty { DEFAULT_ENABLED_PROVIDER_IDS }
