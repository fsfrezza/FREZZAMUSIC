package com.frezzamusic.app.data

import android.content.Context
import com.frezzamusic.app.model.Artist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class CatalogUpdate(val artists: List<Artist>, val message: String)

/** Queries only the two existing public music roots; no account credentials in APKs. */
class LiveDriveCatalog(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("live_drive_catalog_v1", Context.MODE_PRIVATE)
    private val seeds = FrezzaDriveCatalog().rawArtists()
    private val roots = linkedMapOf("theFrezza" to "1dg1NjJjaN050ewNOwRVMke9CK3Bc2wBa", "Solasias" to "1T0WhRmxjXus3UVdOZJmibRzZxY3kFnbO")
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    private val connections = Semaphore(4)

    fun status(): String = prefs.getLong("updated_at", 0L).let { time ->
        if (time == 0L) "Catálogo inicial. Aguardando atualização online."
        else "Última atualização: " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(time))
    }

    internal fun cached(filter: String): List<Artist> = roots.keys.filter { filter.isBlank() || it.equals(filter, true) }.map { name ->
        artist(name, cachedAlbums(name))
    }

    private fun cachedAlbums(name: String): List<RawAlbum> = try {
        val encoded = prefs.getString(name, null)
        if (encoded == null) seeds.getValue(name) else decode(JSONArray(encoded))
    } catch (_: Exception) { seeds.getValue(name) }

    internal suspend fun refresh(filter: String): CatalogUpdate = withContext(Dispatchers.IO) {
        val failures = mutableListOf<String>()
        for ((name, root) in roots.filterKeys { filter.isBlank() || it.equals(filter, true) }) {
            try {
                val previous = cachedAlbums(name).associateBy { it.folderId }
                val folders = list(root).filter { it.folder }
                // Atomic per artist: one failed album must not erase cached tracks.
                val albums = coroutineScope {
                    folders.map { folder -> async { DriveFolderParser.album(folder, list(folder.id), previous[folder.id]) } }.awaitAll()
                }.filter { it.tracks.isNotEmpty() }
                if (!prefs.edit().putString(name, encode(albums).toString()).putLong("updated_at", System.currentTimeMillis()).commit()) {
                    throw IOException("Não foi possível guardar o catálogo.")
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failures += name }
        }
        val current = cached(filter)
        val message = if (failures.isEmpty()) {
            "Catálogo atualizado: ${current.sumOf { it.albums.size }} álbuns e ${current.sumOf { a -> a.albums.sumOf { it.tracks.size } }} faixas."
        } else "Não foi possível atualizar ${failures.joinToString()}. O último catálogo disponível foi mantido. Verifique a conexão e o acesso público às pastas."
        CatalogUpdate(current, message)
    }

    private suspend fun list(id: String): List<DriveEntry> = connections.withPermit {
        val request = Request.Builder().url("https://drive.google.com/embeddedfolderview?id=$id&hl=pt-BR&_=${System.currentTimeMillis()}")
            .header("Cache-Control", "no-cache").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful || response.request.url.host != "drive.google.com") throw IOException("Pasta indisponível.")
            val body = response.body ?: throw IOException("Resposta vazia.")
            val bytes = body.byteStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    if (output.size() > 2 * 1024 * 1024) throw IOException("Listagem excedeu o limite.")
                }
                output.toByteArray()
            }
            if (bytes.size > 2 * 1024 * 1024) throw IOException("Listagem excedeu o limite.")
            DriveFolderParser.parse(bytes.toString(Charsets.UTF_8))
        }
    }

    private fun artist(name: String, albums: List<RawAlbum>) = Artist(name.lowercase(java.util.Locale.ROOT), name, albums.map { album(name, it) }, virtualProject = true, description = "Projeto musical virtual")
    private fun encode(albums: List<RawAlbum>) = JSONArray().apply {
        albums.forEach { a -> put(JSONObject().put("id", a.id).put("title", a.title).put("folder", a.folderId).put("cover", a.coverId ?: JSONObject.NULL).put("tracks", JSONArray().apply { a.tracks.forEach { (id, name) -> put(JSONObject().put("id", id).put("name", name)) } })) }
    }
    private fun decode(array: JSONArray): List<RawAlbum> = (0 until array.length()).map { i ->
        val a = array.getJSONObject(i); val tracks = a.getJSONArray("tracks")
        RawAlbum(a.getString("id"), a.getString("title"), a.getString("folder"), if (a.isNull("cover")) null else a.getString("cover"), (0 until tracks.length()).map { j -> tracks.getJSONObject(j).let { it.getString("id") to it.getString("name") } })
    }
}
