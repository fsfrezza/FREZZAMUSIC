package com.frezzamusic.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.frezzamusic.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AvailableUpdate(val versionCode: Int, val versionName: String, val downloadUrl: String)

object AppUpdates {
    private const val MANIFEST_URL = "https://raw.githubusercontent.com/fsfrezza/FREZZAMUSIC/main/updates/releases.json"
    private val modes = setOf("FREZZAMUSIC", "SOLASIAS", "THEFREZZA")
    suspend fun check(context: Context): AvailableUpdate? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("automatic_check", true)) return@withContext null
        if (BuildConfig.PROJECT_MODE !in modes) return@withContext null
        runCatching {
            val connection = URL(MANIFEST_URL).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 6000
                connection.readTimeout = 6000
                connection.setRequestProperty("Accept", "application/json")
                if (connection.responseCode != 200) return@runCatching null
                val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val channel = prefs.getString("channel", "stable") ?: "stable"
                val item = root.optJSONObject(channel)?.optJSONObject(BuildConfig.PROJECT_MODE) ?: return@runCatching null
                val code = item.optInt("versionCode", 0)
                val url = item.optString("url", "")
                val parsed = Uri.parse(url)
                if (code <= BuildConfig.VERSION_CODE || parsed.scheme != "https" ||
                    parsed.host !in setOf("github.com", "objects.githubusercontent.com")) return@runCatching null
                AvailableUpdate(code, item.optString("versionName", ""), url)
            } finally { connection.disconnect() }
        }.getOrNull()
    }
    fun openDownload(context: Context, update: AvailableUpdate) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
