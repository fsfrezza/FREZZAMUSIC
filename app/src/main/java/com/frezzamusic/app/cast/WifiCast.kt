package com.frezzamusic.app.cast

import android.content.Context
import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.framework.CastContext
import org.json.JSONObject

object WifiCast {
    private fun session(context: Context) =
        runCatching { CastContext.getSharedInstance(context).sessionManager.currentCastSession }.getOrNull()

    fun connected(context: Context): Boolean = session(context)?.isConnected == true

    private fun contentType(uri: String): String {
        val path = Uri.parse(uri).path.orEmpty().lowercase()
        return when {
            path.endsWith(".m4a") || path.endsWith(".mp4") -> "audio/mp4"
            path.endsWith(".aac") -> "audio/aac"
            path.endsWith(".ogg") || path.endsWith(".opus") -> "audio/ogg"
            path.endsWith(".flac") -> "audio/flac"
            path.endsWith(".wav") -> "audio/wav"
            else -> "audio/mpeg"
        }
    }

    /**
     * Submit a media load to an active Cast session.
     * [onResult] is invoked when the receiver acknowledges or rejects the load.
     * The source URL must be accessible from the television, not only the phone.
     */
    fun castTrack(
        context: Context,
        uri: String,
        title: String,
        artist: String,
        album: String,
        positionMs: Long = 0,
        lrc: String? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        if (!uri.startsWith("https://", true) && !uri.startsWith("http://", true)) {
            onResult(false, "Arquivo local ainda não disponível para TV. Selecione uma música online.")
            return
        }
        val remote = session(context)?.remoteMediaClient
        if (remote == null) {
            onResult(false, "Nenhuma TV Google Cast conectada.")
            return
        }
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            putString(MediaMetadata.KEY_ARTIST, artist)
            putString(MediaMetadata.KEY_ALBUM_TITLE, album)
        }
        val media = MediaInfo.Builder(uri)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType(uri))
            .setMetadata(metadata)
            .build()
        try {
            val request = MediaLoadRequestData.Builder()
                .setMediaInfo(media)
                .setCurrentTime(positionMs.coerceAtLeast(0))
                .setAutoplay(true)
                .setCustomData(JSONObject().put("lrc", lrc?.take(60_000).orEmpty()))
                .build()
            remote.load(request).setResultCallback { result ->
                if (result.status.isSuccess) onResult(true, "Reprodução aceita pela TV.")
                else onResult(false, "TV recusou a reprodução: ${result.status.statusMessage ?: result.status.statusCode}")
            }
        } catch (e: Exception) {
            onResult(false, "Não foi possível transmitir: ${e.message ?: "erro de conexão"}")
        }
    }
}
