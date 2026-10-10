package com.frezzamusic.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.io.IOException

/**
 * Paid downloads are written only after the backend issues a short-lived signed URL.
 * The caller must provide a fresh Firebase identity via PurchasedDownloadsApi.
 */
class PurchasedAudioStorage(
    private val context: Context,
    private val api: PurchasedDownloadsApi,
    private val client: OkHttpClient = OkHttpClient()
) {
    private val prefs = context.getSharedPreferences("purchased_audio", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "purchased_audio")
    private val maxDownloadBytes = 200L * 1024 * 1024

    private fun safeName(trackId: String): String {
        require(trackId.isNotBlank()) { "Faixa inválida" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(trackId.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) } + ".audio"
    }

    fun localFile(trackId: String): File? {
        val name = safeName(trackId)
        if (!prefs.getBoolean("saved:$name", false)) return null
        return File(directory, name).takeIf { it.isFile && it.length() > 0L }
    }

    suspend fun downloadPurchasedTrack(trackId: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val allowed = api.purchasedTrackIds()
            require(trackId in allowed) { "Esta faixa não consta em suas compras" }
            val url = api.authorizeDownload(trackId)
            check(directory.exists() || directory.mkdirs()) { "Não foi possível preparar o armazenamento" }
            val destination = File(directory, safeName(trackId))
            val temp = File.createTempFile("purchase-", ".part", directory)
            try {
                val downloadClient = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
                downloadClient.newCall(Request.Builder().url(url).header("Cache-Control", "no-store").build()).execute().use { response ->
                    check(response.isSuccessful) { "Falha no download: HTTP ${response.code}" }
                    val body = checkNotNull(response.body) { "Resposta de download vazia" }
                    check(body.contentLength() <= maxDownloadBytes) { "Arquivo maior que o limite permitido" }
                    temp.outputStream().use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var written = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                written += count
                                if (written > maxDownloadBytes) throw IOException("Download excedeu o limite de 200 MB")
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
                check(temp.length() > 0L) { "Arquivo de áudio vazio" }
                java.nio.file.Files.move(temp.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                prefs.edit().putBoolean("saved:${destination.name}", true).apply()
                destination
            } finally {
                if (temp.exists()) temp.delete()
            }
        }
    }

    fun remove(trackId: String) {
        val name = safeName(trackId)
        File(directory, name).delete()
        prefs.edit().remove("saved:$name").apply()
    }
}
