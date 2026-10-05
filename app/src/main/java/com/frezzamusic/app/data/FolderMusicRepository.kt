package com.frezzamusic.app.data

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.frezzamusic.app.model.Track

class FolderMusicRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("library_folders", Context.MODE_PRIVATE)
    fun folders(): List<Uri> = prefs.getStringSet("uris", emptySet())!!.map(Uri::parse)
    fun add(uri: Uri) { val s=prefs.getStringSet("uris", emptySet())!!.toMutableSet(); s+=uri.toString(); prefs.edit().putStringSet("uris",s).apply() }
    fun remove(uri: Uri) { val s=prefs.getStringSet("uris", emptySet())!!.toMutableSet(); s-=uri.toString(); prefs.edit().putStringSet("uris",s).apply() }
    fun scan(): List<Track> = folders().flatMap { scanTree(DocumentFile.fromTreeUri(context,it)) }.distinctBy { it.uri }
    fun scanDevice(): List<Track> {
        val out= mutableListOf<Track>(); val collection=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.DURATION,MediaStore.Audio.Media.DATE_MODIFIED)
        context.contentResolver.query(collection,projection,MediaStore.Audio.Media.IS_MUSIC+" != 0",null,MediaStore.Audio.Media.TITLE+" ASC")?.use { cur ->
            val idI=cur.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); val titleI=cur.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE); val artistI=cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST); val albumI=cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM); val durI=cur.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION); val dateI=cur.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            while(cur.moveToNext()){ val id=cur.getLong(idI); val uri=Uri.withAppendedPath(collection,id.toString()); out+=Track("device:"+id,cur.getString(titleI)?:"Faixa",cur.getString(artistI)?:"Artista desconhecido",cur.getString(albumI)?:"Álbum desconhecido",uri.toString(),durationMs=cur.getLong(durI),dateMs=cur.getLong(dateI).takeIf{it>0}?.times(1000)) }
        }; return out
    }
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
