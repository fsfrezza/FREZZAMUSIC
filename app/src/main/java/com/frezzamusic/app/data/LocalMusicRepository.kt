package com.frezzamusic.app.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.frezzamusic.app.model.Track

class LocalMusicRepository(private val context: Context) {
    fun load(): List<Track> {
        val out = mutableListOf<Track>()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM)
        context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE")?.use { c ->
            val id=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); val title=c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE); val artist=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST); val album=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            while(c.moveToNext()) { val mediaId=c.getLong(id); out += Track("local:$mediaId",c.getString(title),c.getString(artist)?:"Artista desconhecido",c.getString(album)?:"Álbum desconhecido",ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,mediaId).toString()) }
        }
        return out
    }
}
