package org.feeluown.mobile.provider.audius
import io.ktor.http.Parameters
import io.ktor.http.formUrlEncode
import org.feeluown.mobile.*
import org.feeluown.mobile.provider.core.*
import org.feeluown.mobile.provider.core.network.*
object AudiusProviderFactory : KotlinProviderFactory {
 override val providerId = AudiusProvider.ID; override val info = AudiusProvider.INFO
 override fun create(dependencies: ProviderRuntimeDependencies): KotlinMusicProvider = AudiusProvider(dependencies.http, dependencies.credentials)
}
class AudiusProvider(http: ProviderHttpClient, credentials: ProviderCredentialStore) : BaseKotlinProvider(http, credentials, ID, NAME, INFO, ProviderCapabilities(ID, NAME), emptyList()) {
 override suspend fun search(keyword: String): ProviderSearchResults {
  if (keyword.isBlank()) return ProviderSearchResults()
  val query = Parameters.build { append("query", keyword); append("limit", "20") }.formUrlEncode()
  val root = providerJson.parseToJsonElement(http.getText(ID, BASE + "/tracks/search?" + query, cacheKey="audius:search:" + keyword.trim().lowercase(), cachePolicy=ProviderCachePolicies.search).value).asObject()
  val tracks = root.array("data").mapNotNull { e ->
   val item=e.asObject(); if(item.boolean("is_stream_gated")) return@mapNotNull null
   val tid=item.string("id"); if(tid.isBlank()) return@mapNotNull null
   val user=item.obj("user"); val art=item.obj("artwork")
   track(tid,item.string("title"),user?.string("name").orEmpty(),item.stringOrNull("album")?:"",
    art?.stringOrNull("_480x480")?:art?.stringOrNull("_150x150"),item.long("duration")?.times(1000),
    providerUrl=item.stringOrNull("permalink")?.let { if(it.startsWith("http")) it else "https://audius.co"+it })
  }
  return ProviderSearchResults(tracks=tracks,bestMatches=tracks.firstOrNull()?.let { listOf(ProviderSearchHit.Track(it)) }.orEmpty())
 }
 override suspend fun trackDetail(identifier:String):MusicTrack? {
  val tid=identifier.substringAfterLast(':')
  val root=providerJson.parseToJsonElement(http.getText(ID,BASE+"/tracks/"+tid,cacheKey="audius:track:"+tid,cachePolicy=ProviderCachePolicies.detail).value).asObject()
  val item=root.obj("data")?:return null; if(item.boolean("is_stream_gated")) return null
  val user=item.obj("user"); val art=item.obj("artwork")
  return track(tid,item.string("title"),user?.string("name").orEmpty(),item.stringOrNull("album")?:"",
   art?.stringOrNull("_480x480")?:art?.stringOrNull("_150x150"),item.long("duration")?.times(1000))
 }
 override suspend fun resolve(track:MusicTrack,qualityPolicy:String):PlaybackPayload? {
  val tid=(track.providerId?:track.id).substringAfterLast(':'); if(tid.isBlank()) return null
  return PlaybackPayload(url=BASE+"/tracks/"+tid+"/stream",title=track.title,artists=track.artists,album=track.album,source=ID,coverUrl=track.coverUrl,durationMs=track.durationMs,audioQuality="MP3",providerName=NAME)
 }
 companion object { const val ID="audius"; const val NAME="Audius"; const val BASE="https://api.audius.co/v1"; val INFO=ProviderInfo(ID,NAME,loginConfig=null,supportedLoginModes=emptySet()) }
}