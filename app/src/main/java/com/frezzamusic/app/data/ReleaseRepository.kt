package com.frezzamusic.app.data
import com.frezzamusic.app.model.ReleaseStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request

enum class NewsType { RELEASE, SINGLE, ALBUM_ANNOUNCEMENT, BLOG_POST, APP_NEWS, SPECIAL }
data class ReleaseAnnouncement(
 val id:String,val artist:String,val title:String,val releaseDate:String?,val status:ReleaseStatus,
 val message:String,val artwork:String?=null,val albumId:String?=null,val type:NewsType=NewsType.RELEASE,
 val externalUrl:String?=null
)
class ReleaseRepository {
 private val client=OkHttpClient()
 private val feedUrl="https://raw.githubusercontent.com/fsfrezza/FREZZAMUSIC/main/content/news.json"

 fun announcements(projectMode:String):List<ReleaseAnnouncement>{
  val common=listOf(ReleaseAnnouncement("app-v020","FREZZAMUSIC","FREZZAMUSIC v0.20","2026-10-05",ReleaseStatus.RELEASED,
   "Primeira versão estável da nova geração do FREZZAMUSIC.",type=NewsType.APP_NEWS))
  return common
 }

 suspend fun liveAnnouncements(projectMode:String):List<ReleaseAnnouncement> = withContext(Dispatchers.IO) {
  try {
   val body=client.newCall(Request.Builder().url(feedUrl).build()).execute().use{if(!it.isSuccessful) error("HTTP "+it.code);it.body?.string().orEmpty()}
   val root=Json.parseToJsonElement(body).jsonObject
   root["items"]?.jsonArray.orEmpty().mapNotNull { el ->
    val o=el.jsonObject
    val projects=o["projects"]?.jsonArray?.mapNotNull{it.jsonPrimitive.contentOrNull}.orEmpty()
    if(projects.isNotEmpty() && projectMode !in projects) return@mapNotNull null
    ReleaseAnnouncement(
     id=o["id"]?.jsonPrimitive?.contentOrNull?:return@mapNotNull null,
     artist=o["artist"]?.jsonPrimitive?.contentOrNull?:"FREZZAMUSIC",
     title=o["title"]?.jsonPrimitive?.contentOrNull?:return@mapNotNull null,
     releaseDate=o["releaseDate"]?.jsonPrimitive?.contentOrNull,
     status=runCatching{ReleaseStatus.valueOf(o["status"]?.jsonPrimitive?.contentOrNull?:"RELEASED")}.getOrDefault(ReleaseStatus.RELEASED),
     message=o["message"]?.jsonPrimitive?.contentOrNull.orEmpty(),
     artwork=o["artwork"]?.jsonPrimitive?.contentOrNull,
     albumId=o["albumId"]?.jsonPrimitive?.contentOrNull,
     type=runCatching{NewsType.valueOf(o["type"]?.jsonPrimitive?.contentOrNull?:"RELEASE")}.getOrDefault(NewsType.RELEASE),
     externalUrl=o["externalUrl"]?.jsonPrimitive?.contentOrNull
    )
   }.sortedByDescending{it.releaseDate?:""}
  } catch(_:Exception) { announcements(projectMode) }
 }
}
