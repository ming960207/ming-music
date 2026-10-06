package org.feeluown.mobile.provider.jamendo

import io.ktor.http.Parameters
import io.ktor.http.formUrlEncode
import org.feeluown.mobile.*
import org.feeluown.mobile.provider.core.*
import org.feeluown.mobile.provider.core.network.*

object JamendoProviderFactory : KotlinProviderFactory {
    override val providerId = JamendoProvider.ID
    override val info = JamendoProvider.INFO
    override fun create(dependencies: ProviderRuntimeDependencies): KotlinMusicProvider =
        JamendoProvider(dependencies.http, dependencies.credentials)
}

class JamendoProvider(
    http: ProviderHttpClient,
    credentials: ProviderCredentialStore,
) : BaseKotlinProvider(http, credentials, ID, NAME, INFO, ProviderCapabilities(ID, NAME), emptyList()) {
    override suspend fun search(keyword: String): ProviderSearchResults {
        if (keyword.isBlank()) return ProviderSearchResults()
        val query = Parameters.build {
            append("client_id", CLIENT_ID)
            append("format", "json")
            append("limit", "20")
            append("search", keyword)
            append("audioformat", "mp32")
        }.formUrlEncode()
        val root = providerJson.parseToJsonElement(
            http.getText(
                ID,
                "$BASE/tracks/?$query",
                cacheKey = "jamendo:search:" + keyword.trim().lowercase(),
                cachePolicy = ProviderCachePolicies.search,
            ).value,
        ).asObject()
        val tracks = root.array("results").mapNotNull { element ->
            val item = element.asObject()
            val tid = item.string("id")
            val audio = item.stringOrNull("audio")
            if (tid.isBlank() || audio.isNullOrBlank()) return@mapNotNull null
            track(
                tid,
                item.string("name"),
                item.string("artist_name"),
                item.stringOrNull("album_name") ?: "",
                item.stringOrNull("image"),
                item.long("duration")?.times(1000),
                providerUrl = item.stringOrNull("shareurl"),
            )
        }
        return ProviderSearchResults(
            tracks = tracks,
            bestMatches = tracks.firstOrNull()?.let { listOf(ProviderSearchHit.Track(it)) }.orEmpty(),
        )
    }

    override suspend fun trackDetail(identifier: String): MusicTrack? {
        val tid = identifier.substringAfterLast(':')
        val query = Parameters.build {
            append("client_id", CLIENT_ID)
            append("format", "json")
            append("id", tid)
            append("audioformat", "mp32")
        }.formUrlEncode()
        val root = providerJson.parseToJsonElement(
            http.getText(ID, "$BASE/tracks/?$query", cacheKey = "jamendo:track:$tid", cachePolicy = ProviderCachePolicies.detail).value,
        ).asObject()
        val item = root.array("results").firstOrNull()?.asObject() ?: return null
        if (item.stringOrNull("audio").isNullOrBlank()) return null
        return track(
            tid,
            item.string("name"),
            item.string("artist_name"),
            item.stringOrNull("album_name") ?: "",
            item.stringOrNull("image"),
            item.long("duration")?.times(1000),
            providerUrl = item.stringOrNull("shareurl"),
        )
    }

    override suspend fun resolve(track: MusicTrack, qualityPolicy: String): PlaybackPayload? {
        val tid = (track.providerId ?: track.id).substringAfterLast(':')
        if (tid.isBlank()) return null
        val query = Parameters.build {
            append("client_id", CLIENT_ID)
            append("format", "json")
            append("id", tid)
            append("audioformat", "mp32")
        }.formUrlEncode()
        val root = providerJson.parseToJsonElement(
            http.getText(ID, "$BASE/tracks/?$query", cacheKey = "jamendo:resolve:$tid", cachePolicy = ProviderCachePolicies.detail).value,
        ).asObject()
        val item = root.array("results").firstOrNull()?.asObject() ?: return null
        val audio = item.stringOrNull("audio")?.takeIf { it.isNotBlank() } ?: return null
        return PlaybackPayload(
            url = audio,
            title = track.title,
            artists = track.artists,
            album = track.album,
            source = ID,
            coverUrl = track.coverUrl,
            durationMs = track.durationMs,
            audioQuality = "MP3",
            providerName = NAME,
        )
    }

    companion object {
        const val ID = "jamendo"
        const val NAME = "Jamendo"
        const val BASE = "https://api.jamendo.com/v3.0"
        const val CLIENT_ID = "05bed24e"
        val INFO = ProviderInfo(ID, NAME, loginConfig = null, supportedLoginModes = emptySet())
    }
}
