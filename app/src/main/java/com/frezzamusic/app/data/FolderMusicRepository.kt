package com.frezzamusic.app.data

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.frezzamusic.app.model.Track

class FolderMusicRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("library_folders", Context.MODE_PRIVATE)
    fun folders(): List<Uri> = prefs.getStringSet("uris", emptySet())!!.map(Uri::parse)
    fun add(uri: Uri) { val s=prefs.getStringSet("uris", emptySet())!!.toMutableSet(); s+=uri.toString(); prefs.edit().putStringSet("uris",s).apply() }
    fun remove(uri: Uri) { val s=prefs.getStringSet("uris", emptySet())!!.toMutableSet(); s-=uri.toString(); prefs.edit().putStringSet("uris",s).apply() }
    fun scan(): List<Track> = folders().flatMap { scanTree(DocumentFile.fromTreeUri(context,it)) }.distinctBy { it.uri }

    fun mergeWithoutDuplicates(folderTracks: List<Track>, deviceTracks: List<Track>): List<Track> {
        val seen = HashSet<String>()
        return (folderTracks + deviceTracks).filter { track ->
            val durationBucket = track.durationMs?.div(1000L) ?: -1L
            val key = listOf(track.title, track.artist, track.album)
                .joinToString("|") { it.trim().lowercase() } + "|" + durationBucket
            seen.add(key)
        }
    }
    fun scanDevice(): List<Track> {
        val paths=mutableMapOf<String,String>()
        val out= mutableListOf<Track>(); val collection=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection=mutableListOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.DURATION,MediaStore.Audio.Media.DATE_MODIFIED,MediaStore.Audio.Media.TRACK,MediaStore.Audio.Media.GENRE,MediaStore.Audio.Media.ALBUM_ID).apply {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Audio.Media.RELATIVE_PATH)
        }.toTypedArray()
        context.contentResolver.query(collection,projection,MediaStore.Audio.Media.IS_MUSIC+" != 0",null,MediaStore.Audio.Media.TITLE+" ASC")?.use { cur ->
            val idI=cur.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); val titleI=cur.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE); val artistI=cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST); val albumI=cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM); val durI=cur.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION); val dateI=cur.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED); val trackI=cur.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK); val genreI=cur.getColumnIndex(MediaStore.Audio.Media.GENRE); val albumIdI=cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID); val pathI=if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cur.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) else -1
            while(cur.moveToNext()){
                val id=cur.getLong(idI)
                val uri=Uri.withAppendedPath(collection,id.toString())
                val albumId=cur.getLong(albumIdI)
                val artwork=albumId.takeIf{it>0}?.let{"content://media/external/audio/albumart/$it"}
                val trackNo=cur.getInt(trackI).takeIf{it>0}?.let{it % 1000}
                val genre=if(genreI>=0) cur.getString(genreI)?.takeIf{it.isNotBlank()} else null
                val folderLabel=if(pathI>=0) cur.getString(pathI)?.trimEnd('/')?.substringAfterLast('/')?.takeIf{it.isNotBlank()} else null
                if(pathI>=0)cur.getString(pathI)?.let{paths["device:$id"]=it}
                out+=Track("device:"+id,cur.getString(titleI)?:"Faixa",cur.getString(artistI)?:"Artista desconhecido",cur.getString(albumI)?:"Álbum desconhecido",uri.toString(),artwork=artwork,trackNumber=trackNo,durationMs=cur.getLong(durI),dateMs=cur.getLong(dateI).takeIf{it>0}?.times(1000),genre=genre,folder=folderLabel,releaseDate=readReleaseDate(uri))
            }
        }; return AlbumFolderOrder.device(out,paths)
    }
    private fun scanTree(root: DocumentFile?): List<Track> {
        if(root==null || !root.exists()) return emptyList()
        fun walk(f:DocumentFile):List<Track> {
            if(f.isFile)return if(isAudio(f))listOf(metadata(f.uri,f.name?.substringBeforeLast('.')?:"Faixa",f.lastModified()).copy(folder=f.parentFile?.name?:root.name)) else emptyList()
            if(!f.isDirectory)return emptyList()
            val children=f.listFiles().map{it to walk(it)}
            val albums=children.filter{it.first.isDirectory && it.second.isNotEmpty()}
            val keys=albums.map{it.second.map{t->t.artist to t.album}.distinct()}
            val order=if(keys.all{it.size==1} && keys.flatten().distinct().size==albums.size)
                AlbumFolderOrder.detect(albums.map{it.first.uri.toString() to it.first.name.orEmpty()}) else emptyMap()
            return children.flatMap { (child,tracks)->order[child.uri.toString()]?.let{n->tracks.map{it.copy(albumOrderGroup="folder:${f.uri}",albumOrder=n)}}?:tracks }
        }
        return walk(root)
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
            Track("folder:$uri",title,artist,album,uri.toString(),trackNumber=n,durationMs=d,dateMs=dateMs.takeIf{it>0},genre=genre,releaseDate=readReleaseDate(uri,r))
        } catch(_:Exception){ Track("folder:$uri",fallback,"Artista desconhecido","Álbum desconhecido",uri.toString(),dateMs=dateMs.takeIf{it>0}) } finally { try{r.release()}catch(_:Exception){} }
    }
    private fun readReleaseDate(uri:Uri,existing:MediaMetadataRetriever?=null):String? {
        val tag=runCatching{context.contentResolver.openInputStream(uri)?.use { input->val out=java.io.ByteArrayOutputStream();val b=ByteArray(4096);while(out.size()<65536){val n=input.read(b,0,minOf(b.size,65536-out.size()));if(n<0)break;out.write(b,0,n)};ReleaseDates.fromId3(out.toByteArray()) }}.getOrNull()
        if(tag!=null)return tag
        val retriever=existing?:MediaMetadataRetriever()
        return try{if(existing==null)retriever.setDataSource(context,uri);ReleaseDates.normalize(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE))?:ReleaseDates.normalize(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR))}catch(_:Exception){null}finally{if(existing==null)runCatching{retriever.release()}}
    }
    private fun isAudio(f:DocumentFile):Boolean = f.type?.startsWith("audio/")==true || listOf("mp3","flac","m4a","aac","ogg","opus","wav").any { f.name?.endsWith(".$it",true)==true }
}
