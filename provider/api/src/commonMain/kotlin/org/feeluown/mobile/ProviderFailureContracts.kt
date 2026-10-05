package org.feeluown.mobile

enum class ProviderFailureKind {
    LoginExpired,
    RegionRestricted,
    CopyrightUnavailable,
    ContentUnavailable,
    AccountUnavailable,
    UnsupportedOperation,
    UpstreamContractChanged,
    Network,
    Unknown,
}

data class ProviderFailure(
    val kind: ProviderFailureKind,
    val providerId: String? = null,
    val technicalMessage: String? = null,
)

class ProviderOperationException(
    val failure: ProviderFailure,
    cause: Throwable? = null,
) : IllegalStateException(
    failure.technicalMessage ?: buildString {
        append("Provider operation failed: ")
        append(failure.kind.name)
        failure.providerId?.let { providerId ->
            append(" (provider=")
            append(providerId)
            append(')')
        }
    },
    cause,
)
