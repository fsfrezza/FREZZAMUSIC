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
        val file = File(path)
        if (!file.exists() || file.length() <= 0L) {
            prefs.edit().remove("file:" + track.id).apply()
            return DownloadState.NOT_DOWNLOADED
        }
        return DownloadState.DOWNLOADED
    }

    fun localUri(track: Track): String? {
        val path = prefs.getString("file:" + track.id, null) ?: return null
        val file = File(path)
        if (!file.exists() || file.length() <= 0L) {
            prefs.edit().remove("file:" + track.id).apply()
            if (file.exists()) file.delete()
            return null
        }
        return file.toURI().toString()
    }

    fun downloadedTrackIds(): Set<String> =
        prefs.all.keys.filter { it.startsWith("file:") }.mapNotNull { key ->
            val id = key.removePrefix("file:")
            val path = prefs.getString(key, null)
            if (path != null && File(path).exists() && File(path).length() > 0L) id
            else { prefs.edit().remove(key).apply(); null }
        }.toSet()

    fun totalBytes(): Long =
        prefs.all.keys.filter { it.startsWith("file:") }.sumOf { key ->
            prefs.getString(key, null)?.let(::File)?.takeIf { it.exists() }?.length() ?: 0L
        }

    fun clearAll() {
        prefs.all.keys.filter { it.startsWith("file:") }.forEach { key ->
            prefs.getString(key, null)?.let { File(it).delete() }
        }
        prefs.edit().clear().apply()
    }

    suspend fun download(track: Track): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(track.remote) { "Somente faixas remotas podem ser baixadas" }
            require(track.canDownload) { "Download não liberado para esta faixa" }
            // Until a trusted backend verifies purchase entitlements, paid downloads fail closed.
            require(track.downloadPriceBrl == null || track.downloadPriceBrl <= 0.0) {
                "Download pago indisponível até a confirmação da compra pelo servidor"
            }
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
