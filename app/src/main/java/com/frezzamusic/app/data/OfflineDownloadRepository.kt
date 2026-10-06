package com.frezzamusic.app.data

import android.content.Context
import com.frezzamusic.app.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

enum class DownloadState { NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED, FAILED }

class OfflineDownloadRepository(
    private val context: Context,
    private val resolver: StreamResolver,
    private val client: OkHttpClient = OkHttpClient()
) {
    private val prefs = context.getSharedPreferences("offline_downloads", Context.MODE_PRIVATE)
    private fun key(track: Track) = track.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
    private fun file(track: Track) = File(context.filesDir, "offline_audio/" + key(track))

    fun state(track: Track): DownloadState {
        val path = prefs.getString("file:" + track.id, null) ?: return DownloadState.NOT_DOWNLOADED
        return if (File(path).exists()) DownloadState.DOWNLOADED else DownloadState.NOT_DOWNLOADED
    }

    fun localUri(track: Track): String? =
        prefs.getString("file:" + track.id, null)?.takeIf { File(it).exists() }?.let { File(it).toURI().toString() }

    suspend fun download(track: Track): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(track.remote) { "Somente faixas remotas podem ser baixadas" }
            require(track.canDownload) { "Download não liberado para esta faixa" }
            val target = file(track)
            target.parentFile?.mkdirs()
            val url = resolver.resolve(track).url
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful) { "Falha HTTP " + response.code }
                val body = checkNotNull(response.body)
                target.outputStream().use { output -> body.byteStream().use { it.copyTo(output) } }
            }
            prefs.edit().putString("file:" + track.id, target.absolutePath).apply()
            target.toURI().toString()
        }
    }

    fun remove(track: Track) {
        prefs.getString("file:" + track.id, null)?.let { File(it).delete() }
        prefs.edit().remove("file:" + track.id).apply()
    }
}
