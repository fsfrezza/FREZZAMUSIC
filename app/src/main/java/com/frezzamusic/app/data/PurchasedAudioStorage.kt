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
    private val identity: PurchaseIdentity,
    private val client: OkHttpClient = OkHttpClient()
) {
    private val prefs = context.getSharedPreferences("purchased_audio", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "purchased_audio_v2")
    private fun ownerDirectory(): File {
        val uid = identity.userId()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Entre na sua conta para acessar downloads comprados")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, digest)
    }
    private val maxDownloadBytes = 200L * 1024 * 1024

    private fun safeName(trackId: String): String {
        require(trackId.isNotBlank()) { "Faixa inválida" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(trackId.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) } + ".audio"
    }

    fun localFile(trackId: String): File? {
        val name = safeName(trackId)
        val owner = try { ownerDirectory() } catch (_: IllegalStateException) { return null }
        if (!prefs.getBoolean("saved:${owner.name}:$name", false)) return null
        return File(owner, name).takeIf { it.isFile && it.length() > 0L }
    }

    suspend fun downloadPurchasedTrack(trackId: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val owner = ownerDirectory()
            val startingUid = identity.userId()
            val allowed = api.purchasedTrackIds()
            require(trackId in allowed) { "Esta faixa não consta em suas compras" }
            val url = api.authorizeDownload(trackId)
            check(owner.exists() || owner.mkdirs()) { "Não foi possível preparar o armazenamento" }
            val destination = File(owner, safeName(trackId))
            val temp = File.createTempFile("purchase-", ".part", owner)
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
                check(startingUid != null && identity.userId() == startingUid) {
                    "A conta mudou durante o download"
                }
                java.nio.file.Files.move(temp.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                prefs.edit().putBoolean("saved:${owner.name}:${destination.name}", true).apply()
                destination
            } finally {
                if (temp.exists()) temp.delete()
            }
        }
    }

    fun remove(trackId: String) {
        val name = safeName(trackId)
        val owner = try { ownerDirectory() } catch (_: IllegalStateException) { return }
        File(owner, name).delete()
        prefs.edit().remove("saved:${owner.name}:$name").apply()
    }
}
