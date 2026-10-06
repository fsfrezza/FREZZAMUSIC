package com.frezzamusic.app.data
import com.frezzamusic.app.model.ReleaseStatus

enum class NewsType { RELEASE, SINGLE, ALBUM_ANNOUNCEMENT, BLOG_POST, APP_NEWS, SPECIAL }
data class ReleaseAnnouncement(
 val id:String,val artist:String,val title:String,val releaseDate:String?,val status:ReleaseStatus,
 val message:String,val artwork:String?=null,val albumId:String?=null,val type:NewsType=NewsType.RELEASE,
 val externalUrl:String?=null
)
class ReleaseRepository {
 fun announcements(projectMode:String):List<ReleaseAnnouncement>{
  val common=listOf(
   ReleaseAnnouncement("app-v020","FREZZAMUSIC","FREZZAMUSIC v0.20","2026-10-03",ReleaseStatus.PRE_RELEASE,
    "Nova geração do aplicativo: player completo, streaming livre do catálogo oficial, letras sincronizadas, filas e arquitetura multi-app.",type=NewsType.APP_NEWS)
  )
  val solasias=listOf(
   ReleaseAnnouncement("solasias-editorial","Solasias","Projeto Solasias",null,ReleaseStatus.RELEASED,
    "Acompanhe a discografia e os conteúdos editoriais do projeto musical virtual.",type=NewsType.BLOG_POST,
    externalUrl=EditorialSources.SOLASIAS_BLOG_ALBUMS)
  )
  return when(projectMode){"SOLASIAS"->solasias+common;"THEFREZZA"->common;else->common+solasias}
 }
}
