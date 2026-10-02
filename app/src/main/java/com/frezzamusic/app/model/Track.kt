package com.frezzamusic.app.model

enum class TrackSource { LOCAL, ONLINE_FREE, ONLINE_HQ }
data class Track(val id:String,val title:String,val artist:String,val album:String,val uri:String,val artwork:String?=null,val source:TrackSource=TrackSource.LOCAL,val trackNumber:Int?=null,val durationMs:Long?=null,val remoteFileId:String?=null,val genre:String?=null){ val remote get()=source!=TrackSource.LOCAL }
data class Album(val id:String,val title:String,val artist:String,val artwork:String?=null,val tracks:List<Track> = emptyList())
data class Artist(val id:String,val name:String,val albums:List<Album> = emptyList())
data class MusicQueue(val id:String,val name:String,val trackIds:List<String> = emptyList())
data class UserPlaylist(val id:String,val name:String,val trackIds:List<String> = emptyList())
data class PlayHistory(val trackId:String,val playedAt:Long,val playCount:Int)
