package com.frezzamusic.app.cast

import android.content.Context
import org.json.JSONObject
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.framework.CastContext

object WifiCast {
    private fun session(context: Context) =
        runCatching { CastContext.getSharedInstance(context).sessionManager.currentCastSession }.getOrNull()

    fun connected(context: Context): Boolean = session(context)?.isConnected == true

    fun castTrack(
        context: Context, uri: String, title: String, artist: String, album: String,
        positionMs: Long = 0, lrc: String? = null
    ): String? {
        if (!uri.startsWith("https://", true) && !uri.startsWith("http://", true))
            return "Arquivos locais ainda exigem compartilhamento seguro na rede. Selecione uma música online."
        val remote = session(context)?.remoteMediaClient ?: return "Nenhuma TV Google Cast conectada."
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            putString(MediaMetadata.KEY_ARTIST, artist)
            putString(MediaMetadata.KEY_ALBUM_TITLE, album)
        }
        val info = MediaInfo.Builder(uri)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("audio/mpeg")
            .setMetadata(metadata)
            .build()
        return try {
            remote.load(
                MediaLoadRequestData.Builder()
                    .setMediaInfo(info)
                    .setCurrentTime(positionMs.coerceAtLeast(0))
                    .setAutoplay(true)
                    .setCustomData(JSONObject().put("lrc", lrc?.take(60_000).orEmpty()))
                    .build()
            )
            null
        } catch (e: Exception) {
            "Não foi possível enviar a música: ${e.message ?: "erro de conexão"}"
        }
    }
}
