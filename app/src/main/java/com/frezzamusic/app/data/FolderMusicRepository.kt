package com.frezzamusic.app.data

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import androidx.documentfile.provider.DocumentFile
import com.frezzamusic.app.model.Track

class FolderMusicRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("library_folders", Context.MODE_PRIVATE)
    fun folders(): List<Uri> = prefs.getStringSet("uris", emptySet())!!.map(Uri::parse)
    fun add(uri: Uri) { val s=prefs.getStringSet("uris", emptySet())!!.toMutableSet(); s+=uri.toString(); prefs.edit().putStringSet("uris",s).apply() }
    fun remove(uri: Uri) { val s=prefs.getStringSet("uris", emptySet())!!.toMutableSet(); s-=uri.toString(); prefs.edit().putStringSet("uris",s).apply() }
    fun scan(): List<Track> = folders().flatMap { scanTree(DocumentFile.fromTreeUri(context,it)) }.distinctBy { it.uri }
    private fun scanTree(root: DocumentFile?): List<Track> {
        if(root==null || !root.exists()) return emptyList()
        val out= mutableListOf<Track>()
        fun walk(f:DocumentFile){ if(f.isDirectory) f.listFiles().forEach(::walk) else if(f.isFile && isAudio(f)){ val name=f.name?.substringBeforeLast('.')?:"Faixa"; val m=metadata(f.uri,name,f.lastModified()); out += m } }
        walk(root); return out
    }
    private fun metadata(uri:Uri,fallback:String,dateMs:Long):Track {
        val r=MediaMetadataRetriever()
        return try {
            r.setDataSource(context,uri)
            val title=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf{it.isNotBlank()} ?: fallback
            val artist=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf{it.isNotBlank()} ?: "Artista desconhecido"
            val album=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf{it.isNotBlank()} ?: "Álbum desconhecido"
            val n=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.toIntOrNull()
            val d=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val genre=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            Track("folder:$uri",title,artist,album,uri.toString(),trackNumber=n,durationMs=d,dateMs=dateMs.takeIf{it>0},genre=genre)
        } catch(_:Exception){ Track("folder:$uri",fallback,"Artista desconhecido","Álbum desconhecido",uri.toString(),dateMs=dateMs.takeIf{it>0}) } finally { try{r.release()}catch(_:Exception){} }
    }
    private fun isAudio(f:DocumentFile):Boolean = f.type?.startsWith("audio/")==true || listOf("mp3","flac","m4a","aac","ogg","opus","wav").any { f.name?.endsWith(".$it",true)==true }
}
