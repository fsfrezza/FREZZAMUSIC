package com.frezzamusic.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal data class AppRelease(val versionCode: Int, val versionName: String, val downloadUrl: String)

/** Only this app's stable Drive download can be advertised by the public manifest. */
internal object AppUpdateManifest {
    val files = mapOf(
        "com.frezzamusic.app" to "12yj7dPXNeKuB_tYGOaA_2jg5eHIAxWzy",
        "com.frezzamusic.solasias" to "1Twe3W6NzPupDzILpC9a39x0gHn-uz8uH",
        "com.frezzamusic.thefrezza" to "1Xu0WLm_wW03LDzmRCzI47Miuvu8Er7HX"
    )
    fun newer(json: String, packageName: String, installed: Int): AppRelease? {
        val file = files[packageName] ?: return null
        val root = Json.parseToJsonElement(json).jsonObject
        require(root["schema"]?.jsonPrimitive?.int == 1)
        val entry = root["apps"]?.jsonObject?.get(packageName)?.jsonObject ?: return null
        val code = entry["versionCode"]?.jsonPrimitive?.int ?: error("Missing version")
        require(code > 0)
        val name = entry["versionName"]?.jsonPrimitive?.content ?: error("Missing name")
        require(name.length in 1..40)
        require(entry["driveFileId"]?.jsonPrimitive?.content == file)
        return if (code > installed) AppRelease(code, name, "https://drive.google.com/file/d/$file/view") else null
    }
}

internal class AppUpdateRepository {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build()
    suspend fun check(packageName: String, installed: Int): AppRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://fsfrezza.github.io/FREZZAMUSIC/updates.json")
            .header("Cache-Control", "no-cache").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Update manifest unavailable" }
            val body = response.body ?: error("Empty manifest")
            require(body.contentLength() <= 32768)
            // Bound the read even when the server does not declare a content length.
            val source = body.source()
            source.request(32769)
            require(source.buffer.size <= 32768)
            val bytes = source.readByteArray()
            AppUpdateManifest.newer(String(bytes, Charsets.UTF_8), packageName, installed)
        }
    }
}
