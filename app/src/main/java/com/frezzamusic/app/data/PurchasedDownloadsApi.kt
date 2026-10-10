package com.frezzamusic.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Authenticated purchase API client. The token supplier must return a fresh Firebase ID token.
 * No anonymous or development identity is synthesized on the Android device.
 */
class PurchasedDownloadsApi(
    private val baseUrl: String,
    private val tokenProvider: suspend () -> String?,
    private val client: OkHttpClient = OkHttpClient()
) {
    init {
        require(baseUrl.startsWith("https://")) { "A API de compras exige HTTPS" }
    }

    private suspend fun authenticatedRequest(path: String, method: String): JSONObject = withContext(Dispatchers.IO) {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Entre na sua conta para acessar suas compras")
        val builder = Request.Builder().url(baseUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer $token")
            .header("Cache-Control", "no-store")
        if (method == "POST") builder.post(okhttp3.RequestBody.create(null, ByteArray(0)))
        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 401) throw IllegalStateException("Sessão expirada. Entre novamente")
            if (response.code == 403) throw IllegalStateException("Esta música não está liberada para download")
            if (!response.isSuccessful) throw IllegalStateException("Servidor indisponível (HTTP ${response.code})")
            JSONObject(response.body?.string() ?: throw IllegalStateException("Resposta vazia"))
        }
    }

    suspend fun purchasedTrackIds(): Set<String> {
        val response = authenticatedRequest("/v1/me/entitlements", "GET")
        val array = response.getJSONArray("trackIds")
        return (0 until array.length()).map { array.getString(it) }.toSet()
    }

    suspend fun authorizeDownload(trackId: String): String {
        require(trackId.isNotBlank()) { "Faixa inválida" }
        val response = authenticatedRequest("/v1/downloads/" +
            java.net.URLEncoder.encode(trackId, "UTF-8").replace("+", "%20") + "/authorize", "POST")
        val url = response.getString("url")
        val expiresAt = java.time.Instant.parse(response.getString("expiresAt"))
        require(java.net.URI(url).scheme.equals("https", ignoreCase = true) && expiresAt.isAfter(java.time.Instant.now())) {
            "Link de download inválido ou expirado"
        }
        return url
    }
}
