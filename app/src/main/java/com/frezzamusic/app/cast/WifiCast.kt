package com.frezzamusic.app.cast

import android.content.Context
import com.frezzamusic.app.R
import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.common.images.WebImage
import com.google.android.gms.cast.framework.CastContext
import org.json.JSONObject

object WifiCast {
    fun customReceiverConfigured(context: Context): Boolean =
        context.getString(R.string.cast_receiver_app_id) != "CC1AD845"

    private const val NAMESPACE = "urn:x-cast:com.frezzamusic.lyrics"

    fun syncScreen(context: Context, page: Int, follow: Boolean, lrc: String?, title: String, artist: String) {
        val session = runCatching { CastContext.getSharedInstance(context).sessionManager.currentCastSession }.getOrNull() ?: return
        if (!session.isConnected || !customReceiverConfigured(context)) return
        val payload = JSONObject().put("type", "display")
            .put("page", if (page == 1) "lyrics" else "now-playing")
            .put("follow", follow)
            .put("title", title).put("artist", artist)
            .put("lrc", lrc?.take(60000).orEmpty()).toString()
        runCatching { session.sendMessage(NAMESPACE, payload) }
    }

    fun connected(context: Context): Boolean = runCatching {
        CastContext.getSharedInstance(context).sessionManager.currentCastSession?.isConnected == true
    }.getOrDefault(false)

    fun send(context: Context, uri: String, title: String, artist: String, album: String, positionMs: Long, lrc: String?, artwork: String?, callback: (Boolean, String) -> Unit) {
        val scheme = Uri.parse(uri).scheme?.lowercase()
        if (scheme !in listOf("http", "https")) {
            callback(false, "Áudio local ainda não pode ser enviado à TV; tente uma música online.")
            return
        }
        val session = runCatching { CastContext.getSharedInstance(context).sessionManager.currentCastSession }.getOrNull()
        val client = session?.remoteMediaClient
        if (client == null) {
            callback(false, "Selecione primeiro uma TV compatível com Google Cast.")
            return
        }
        val path = Uri.parse(uri).path.orEmpty().lowercase()
        val mime = when {
            path.endsWith(".m4a") -> "audio/mp4"
            path.endsWith(".aac") -> "audio/aac"
            path.endsWith(".ogg") || path.endsWith(".opus") -> "audio/ogg"
            path.endsWith(".flac") -> "audio/flac"
            path.endsWith(".wav") -> "audio/wav"
            else -> "audio/mpeg"
        }
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            putString(MediaMetadata.KEY_ARTIST, artist)
            putString(MediaMetadata.KEY_ALBUM_TITLE, album)
            if (artwork?.startsWith("https://", ignoreCase = true) == true) {
                addImage(WebImage(Uri.parse(artwork)))
            }
        }
        val media = MediaInfo.Builder(uri).setContentType(mime)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setMetadata(metadata).build()
        try {
            val request = MediaLoadRequestData.Builder().setMediaInfo(media)
                .setAutoplay(true).setCurrentTime(positionMs.coerceAtLeast(0L))
                .setCustomData(JSONObject().put("lrc", lrc?.take(60000).orEmpty()))
                .build()
            client.load(request).setResultCallback { result ->
                callback(result.status.isSuccess, if (result.status.isSuccess) "Enviado à TV." else "A TV não aceitou a faixa.")
            }
        } catch (error: Exception) {
            callback(false, "Falha na transmissão: ${error.message ?: "erro desconhecido"}")
        }
    }
}
