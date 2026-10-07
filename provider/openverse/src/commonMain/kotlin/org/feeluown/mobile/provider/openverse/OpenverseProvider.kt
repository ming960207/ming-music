package org.feeluown.mobile.provider.openverse

import io.ktor.http.Parameters
import io.ktor.http.formUrlEncode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.feeluown.mobile.*
import org.feeluown.mobile.provider.core.*
import org.feeluown.mobile.provider.core.network.*

object OpenverseProviderFactory : KotlinProviderFactory {
 override val providerId = OpenverseProvider.ID
 override val info = OpenverseProvider.INFO
 override fun create(dependencies: ProviderRuntimeDependencies): KotlinMusicProvider =
  OpenverseProvider(dependencies.http, dependencies.credentials)
}

class OpenverseProvider(http: ProviderHttpClient, credentials: ProviderCredentialStore) :
 BaseKotlinProvider(http, credentials, ID, NAME, INFO, ProviderCapabilities(ID, NAME), emptyList()) {

 override suspend fun search(keyword: String): ProviderSearchResults {
  if (keyword.isBlank()) return ProviderSearchResults()
  val query = Parameters.build {
   append("q", keyword)
   append("page_size", "20")
  }.formUrlEncode()
  val root = providerJson.parseToJsonElement(
   http.getText(ID, "$BASE/audio/?$query", cacheKey="openverse:search:"+keyword.trim().lowercase(), cachePolicy=ProviderCachePolicies.search).value
  ).asObject()
  val tracks = root.array("results").mapNotNull { e -> parseTrack(e.asObject()) }
  return ProviderSearchResults(tracks=tracks,bestMatches=tracks.firstOrNull()?.let { listOf(ProviderSearchHit.Track(it)) }.orEmpty())
 }

 override suspend fun trackDetail(identifier: String): MusicTrack? {
  val id=identifier.substringAfterLast(':')
  if(id.isBlank()) return null
  val item=providerJson.parseToJsonElement(
   http.getText(ID, "$BASE/audio/$id/", cacheKey="openverse:track:$id", cachePolicy=ProviderCachePolicies.detail).value
  ).asObject()
  return parseTrack(item)
 }

 override suspend fun resolve(track: MusicTrack, qualityPolicy: String): PlaybackPayload? {
  val id=(track.providerId?:track.id).substringAfterLast(':')
  if(id.isBlank()) return null
  val item=providerJson.parseToJsonElement(
   http.getText(ID, "$BASE/audio/$id/", cacheKey="openverse:track:$id", cachePolicy=ProviderCachePolicies.detail).value
  ).asObject()
  val media=selectMedia(item)?:return null
  return PlaybackPayload(url=media.first,title=track.title,artists=track.artists,album=track.album,source=ID,coverUrl=track.coverUrl,durationMs=track.durationMs,audioQuality=media.second,providerName=NAME)
 }

 private fun parseTrack(item: JsonObject): MusicTrack? {
  val id=item.string("id")
  if(id.isBlank()) return null
  val title=item.string("title")
  if(title.isBlank()) return null
  if(selectMedia(item)==null) return null
  return track(id,title,item.stringOrNull("creator").orEmpty(),item.obj("audio_set")?.stringOrNull("title")?:"",
   item.stringOrNull("thumbnail"),item.long("duration"),providerUrl=item.stringOrNull("foreign_landing_url"))
 }

 private fun selectMedia(item: JsonObject): Pair<String,String>? {
  val candidates=mutableListOf<Pair<String,String>>()
  val mainUrl=item.stringOrNull("url")
  val mainType=item.stringOrNull("filetype").orEmpty()
  if(!mainUrl.isNullOrBlank()) candidates += mainUrl to mainType
  (item["alt_files"] as? JsonArray)?.forEach { alt ->
   val obj=alt as? JsonObject ?: return@forEach
   val url=obj.stringOrNull("url")
   if(!url.isNullOrBlank()) candidates += url to obj.stringOrNull("filetype").orEmpty()
  }
  val rank=mapOf("mp3" to 0,"m4a" to 1,"aac" to 2,"ogg" to 3,"opus" to 4,"flac" to 5,"wav" to 6)
  return candidates.mapNotNull { (url,type) ->
   val ext=type.lowercase().removePrefix(".").ifBlank { url.substringBefore('?').substringAfterLast('.', "").lowercase() }
   rank[ext]?.let { Triple(it,url,ext) }
  }.minByOrNull { it.first }?.let { it.second to it.third.uppercase() }
 }

 companion object {
  const val ID="openverse"
  const val NAME="Openverse"
  const val BASE="https://api.openverse.org/v1"
  val INFO=ProviderInfo(ID,NAME,loginConfig=null,supportedLoginModes=emptySet())
 }
}