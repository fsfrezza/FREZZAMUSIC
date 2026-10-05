package com.frezzamusic.app.model

enum class TrackSource { LOCAL, FREZZAMUSIC_STREAM, EXTERNAL_STREAM }
enum class AudioQuality { UNKNOWN, AAC_128, AAC_320, FLAC, ORIGINAL }
enum class ReleaseStatus { DRAFT, ANNOUNCED, PRE_RELEASE, RELEASED, ARCHIVED }

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val uri: String,
    val artwork: String? = null,
    val source: TrackSource = TrackSource.LOCAL,
    val trackNumber: Int? = null,
    val durationMs: Long? = null,
    val dateMs: Long? = null,
    val remoteFileId: String? = null,
    val genre: String? = null,
    val quality: AudioQuality = AudioQuality.UNKNOWN,
    val canStream: Boolean = true,
    val canDownload: Boolean = source == TrackSource.LOCAL,
    val downloadPriceBrl: Double? = null,
    val lyrics: String? = null,
    val lrc: String? = null
) { val remote: Boolean get() = source != TrackSource.LOCAL }

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val artwork: String? = null,
    val tracks: List<Track> = emptyList(),
    val status: ReleaseStatus = ReleaseStatus.RELEASED,
    val releaseDate: String? = null,
    val description: String? = null,
    val albumPriceBrl: Double? = null
)
data class Artist(val id:String,val name:String,val albums:List<Album> = emptyList(), val virtualProject:Boolean = false, val description:String? = null)
data class MusicQueue(val id:String,val name:String,val trackIds:List<String> = emptyList())
data class UserPlaylist(val id:String,val name:String,val trackIds:List<String> = emptyList())
data class PlayHistory(val trackId:String,val playedAt:Long,val playCount:Int)
