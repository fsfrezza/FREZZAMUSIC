package com.frezzamusic.app.data

import com.frezzamusic.app.model.*

interface RemoteCatalog { suspend fun artists():List<Artist>; suspend fun tracks()=artists().flatMap{it.albums}.flatMap{it.tracks} }

internal data class RawAlbum(val id:String,val title:String,val folderId:String,val coverId:String?,val tracks:List<Pair<String,String>>,val albumOrder:Int?=null)
internal fun album(artist:String,r:RawAlbum):Album {
 val cover=r.coverId?.let{"https://drive.google.com/uc?export=download&id=$it"}
 val genre=if(artist.equals("Solasias",true)) "Metal Cristão" else if(artist.equals("theFrezza",true)) "Metal" else null
 return Album(r.id,r.title,artist,cover,r.tracks.mapIndexed{i,(fid,name)->
  Track("remote:$artist:${r.id}:$fid",DriveFolderParser.displayTitle(name).replace(Regex("(?i)\\.mp3$"), ""),artist,r.title,"",artwork=cover,source=TrackSource.FREZZAMUSIC_STREAM,trackNumber=i+1,remoteFileId=fid,folder=r.folderId,genre=genre,albumOrder=r.albumOrder,albumOrderGroup=if(r.albumOrder!=null)"drive:$artist" else null,canDownload=false,downloadPriceBrl=1.99)
 },albumOrder=r.albumOrder,albumOrderGroup=if(r.albumOrder!=null)"drive:$artist" else null)
}
