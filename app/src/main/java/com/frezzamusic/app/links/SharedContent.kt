package com.frezzamusic.app.links

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.frezzamusic.app.model.Album
import com.frezzamusic.app.model.Track

/** Links carry identifiers only; display metadata is never used to select playback. */
object SharedContent {
    const val HOST = "fsfrezza.github.io"
    const val PATH = "/FREZZAMUSIC/share/"
    data class Target(val artist: String, val kind: String, val id: String)
    private fun artist(value: String) = when(value.lowercase(java.util.Locale.ROOT)) {
        "solasias" -> "solasias"
        "thefrezza" -> "thefrezza"
        else -> null
    }
    fun parse(uri: Uri?): Target? = parseUrl(uri?.toString())
    fun parseUrl(value: String?): Target? = try {
        val uri = java.net.URI(value ?: "")
        if (uri.rawUserInfo != null || uri.port != -1 || uri.fragment != null) null
        else if (!((uri.scheme == "https" && uri.host == HOST && uri.path == PATH) ||
                    (uri.scheme == "frezzamusic" && uri.host == "open" && uri.path.orEmpty().isEmpty()))) null
        else {
            val pairs = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.map {
                val pair = it.split('=', limit = 2)
                java.net.URLDecoder.decode(pair[0], "UTF-8") to java.net.URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
            }
            val params = pairs.toMap()
            val band = artist(params["artist"].orEmpty())
            val kind = params["kind"]?.takeIf { it == "track" || it == "album" }
            val id = params["id"]?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,160}")) }
            if (band == null || kind == null || id == null || pairs.size != params.size) null
            else Target(band, kind, id)
        }
    } catch (_: Exception) { null }
    private fun url(band: String, kind: String, id: String, title: String, album: String, cover: String?): String =
        Uri.Builder().scheme("https").authority(HOST).path(PATH)
            .appendQueryParameter("artist", band).appendQueryParameter("kind", kind)
            .appendQueryParameter("id", id).appendQueryParameter("title", title)
            .appendQueryParameter("album", album).apply { cover?.let { appendQueryParameter("cover", it) } }.build().toString()
    fun trackUrl(track: Track): String? {
        if (!track.remote) return null
        val band = artist(track.artist) ?: return null
        val id = track.remoteFileId ?: return null
        return url(band, "track", id, track.title, track.album, track.artwork)
    }
    fun albumUrl(album: Album): String? {
        val band = artist(album.artist) ?: return null
        val folder = album.tracks.firstOrNull()?.folder ?: return null
        return url(band, "album", folder, album.title, album.title, album.artwork)
    }
    fun share(context: Context, title: String, url: String) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
        }, "Compartilhar"))
    }
}
