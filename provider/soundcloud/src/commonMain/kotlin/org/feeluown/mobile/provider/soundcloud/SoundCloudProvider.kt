package org.feeluown.mobile.provider.soundcloud

import kotlinx.serialization.json.JsonArray
import org.feeluown.mobile.*
import org.feeluown.mobile.provider.core.*
import org.feeluown.mobile.provider.core.network.*

object SoundCloudProviderFactory : KotlinProviderFactory {
    override val providerId = SoundCloudProvider.ID
    override val info = SoundCloudProvider.INFO
    override fun create(dependencies: ProviderRuntimeDependencies): KotlinMusicProvider =
        SoundCloudProvider(dependencies.http, dependencies.credentials)
}

/**
 * Official SoundCloud API adapter.
 *
 * Deliberately does not embed a client secret in the public application. The provider consumes an
 * OAuth access token from ProviderCredentialStore. This keeps SoundCloud isolated from existing
 * providers and lets the app fail closed when credentials are not configured.
 */
class SoundCloudProvider(
    http: ProviderHttpClient,
    credentials: ProviderCredentialStore,
) : BaseKotlinProvider(
    http,
    credentials,
    ID,
    NAME,
    INFO,
    ProviderCapabilities(ID, NAME),
    emptyList(),
) {
    override suspend fun search(keyword: String): ProviderSearchResults {
        if (keyword.isBlank()) return ProviderSearchResults()
        val token = accessToken() ?: return ProviderSearchResults(
            errorMessage = "SoundCloud 尚未配置 OAuth 凭据",
        )
        val url = queryUrl("$BASE/tracks", mapOf("q" to keyword, "limit" to "20"))
        val root = providerJson.parseToJsonElement(
            http.getText(
                ID,
                url,
                headers = oauthHeaders(token),
                cachePolicy = ProviderCachePolicies.none,
            ).value,
        )
        val items = when (root) {
            is JsonArray -> root
            else -> root.asObject().array("collection")
        }
        val tracks = items.mapNotNull { element -> parseTrack(element.asObject()) }
        return ProviderSearchResults(
            tracks = tracks,
            bestMatches = tracks.firstOrNull()?.let { listOf(ProviderSearchHit.Track(it)) }.orEmpty(),
        )
    }

    override suspend fun trackDetail(identifier: String): MusicTrack? {
        val token = accessToken() ?: return null
        val id = identifier.substringAfterLast(':')
        if (id.isBlank()) return null
        val item = providerJson.parseToJsonElement(
            http.getText(
                ID,
                "$BASE/tracks/$id",
                headers = oauthHeaders(token),
                cachePolicy = ProviderCachePolicies.none,
            ).value,
        ).asObject()
        return parseTrack(item)
    }

    override suspend fun resolve(track: MusicTrack, qualityPolicy: String): PlaybackPayload? {
        val token = accessToken() ?: return null
        val id = (track.providerId ?: track.id).substringAfterLast(':')
        if (id.isBlank()) return null
        val streams = providerJson.parseToJsonElement(
            http.getText(
                ID,
                "$BASE/tracks/soundcloud:tracks:$id/streams",
                headers = oauthHeaders(token),
                kind = ProviderRequestKind.Media,
            ).value,
        ).asObject()
        val streamUrl = streams.stringOrNull("hls_aac_160_url")
            ?: streams.stringOrNull("hls_mp3_128_url")
            ?: return null
        return PlaybackPayload(
            url = streamUrl,
            headers = oauthHeaders(token),
            title = track.title,
            artists = track.artists,
            album = track.album,
            source = ID,
            coverUrl = track.coverUrl,
            durationMs = track.durationMs,
            audioQuality = if (streams.stringOrNull("hls_aac_160_url") != null) "AAC 160k HLS" else "MP3 128k HLS",
            providerName = NAME,
        )
    }

    private suspend fun accessToken(): String? =
        currentCredentials()?.oauthAccessToken?.trim()?.takeIf { it.isNotBlank() }

    private fun oauthHeaders(token: String): Map<String, String> =
        mapOf("Authorization" to "OAuth $token")

    private fun parseTrack(item: kotlinx.serialization.json.JsonObject): MusicTrack? {
        if (item["streamable"] != null && !item.boolean("streamable")) return null
        val id = item.string("id").ifBlank {
            item.string("urn").substringAfterLast(':')
        }
        if (id.isBlank()) return null
        val user = item.obj("user")
        val artwork = item.stringOrNull("artwork_url")
            ?.replace("-large.", "-t500x500.")
        return track(
            identifier = id,
            title = item.string("title"),
            artists = user?.string("username").orEmpty(),
            album = "",
            coverUrl = artwork ?: user?.stringOrNull("avatar_url"),
            durationMs = item.long("duration"),
            providerUrl = item.stringOrNull("permalink_url"),
        )
    }

    companion object {
        const val ID = "soundcloud"
        const val NAME = "SoundCloud"
        const val BASE = "https://api.soundcloud.com"
        val INFO = ProviderInfo(
            providerId = ID,
            providerName = NAME,
            loginConfig = null,
            supportedLoginModes = setOf(ProviderLoginMode.OAuth),
        )
    }
}
