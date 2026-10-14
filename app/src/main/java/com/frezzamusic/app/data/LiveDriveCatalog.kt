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
import kotlinx.coroutines.sync.withLock
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
    init {
        // Android backup can restore preferences on reinstall; this marker is never backed up.
        val marker = java.io.File(context.noBackupFilesDir, "live_catalog_install_v1")
        if (!marker.exists()) {
            prefs.edit().clear().commit()
            marker.writeText("1")
        }
    }
    private val roots = linkedMapOf("theFrezza" to "1dg1NjJjaN050ewNOwRVMke9CK3Bc2wBa", "Solasias" to "1T0WhRmxjXus3UVdOZJmibRzZxY3kFnbO")
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    private val connections = Semaphore(4)
    private val releaseDates=RemoteReleaseDates(context)

    fun status(): String = prefs.getLong("updated_at", 0L).let { time ->
        if (time == 0L) "Aguardando o catálogo atual do Drive."
        else "Última atualização: " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(time))
    }

    internal fun cached(filter: String): List<Artist> = roots.keys.filter { filter.isBlank() || it.equals(filter, true) }.map { name ->
        artist(name, cachedAlbums(name))
    }

    private val mutationLock = kotlinx.coroutines.sync.Mutex()

    internal fun hasCatalog(filter: String): Boolean = targetRoots(filter).keys.all { name ->
        try { prefs.getString(name, null)?.let { decode(JSONArray(it)); true } ?: false }
        catch (_: Exception) { false }
    }

    private fun targetRoots(filter: String) = roots.filterKeys { filter.isBlank() || it.equals(filter, true) }
    private fun cachedAlbums(name: String): List<RawAlbum> = try {
        prefs.getString(name, null)?.let { decode(JSONArray(it)) } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    /** Fetches and compares metadata without saving anything or replacing the visible library. */
    internal suspend fun check(filter: String, forceTags:Boolean=false): CatalogPreview = withContext(Dispatchers.IO) {
        releaseDates.beginCheck()
        val baseline = targetRoots(filter).keys.associateWith { cachedAlbums(it) }
        val failures = mutableListOf<String>()
        val proposed = linkedMapOf<String, List<RawAlbum>>()
        for ((name, root) in targetRoots(filter)) {
            try {
                val previous = baseline.getValue(name).associateBy { it.folderId }
                val folders = list(root).filter { it.folder }
                proposed[name] = coroutineScope {
                    folders.map { folder -> async {
                        val old = previous[folder.id] ?: LegacyAlbumIds.byFolder[folder.id]?.let { id ->
                            RawAlbum(id, "", folder.id, null, emptyList())
                        }
                        releaseDates.album(DriveFolderParser.album(folder, list(folder.id), old),forceTags)
                    } }.awaitAll()
                }.filter { it.tracks.isNotEmpty() }.let { albums ->
                    val names=folders.associateBy { it.id }
                    val order=AlbumFolderOrder.detect(albums.map { it.folderId to names.getValue(it.folderId).name })
                    albums.map { it.copy(albumOrder=order[it.folderId]) }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failures += name }
        }
        CatalogPreview(baseline, proposed, failures, releaseDates.unavailableCount())
    }

    internal suspend fun apply(preview: CatalogPreview, filter: String): CatalogUpdate = withContext(Dispatchers.IO) {
        mutationLock.withLock {
            val current = targetRoots(filter).keys.associateWith { cachedAlbums(it) }
            val confirmed = preview.approved(current, accepted = true)
            if (confirmed.isNotEmpty()) {
                val edit = prefs.edit()
                confirmed.forEach { (name, albums) -> edit.putString(name, encode(albums).toString()) }
                if (!edit.putLong("updated_at", System.currentTimeMillis()).commit()) throw IOException("Não foi possível guardar o catálogo.")
            }
            val artists = cached(filter)
            val message = if (preview.failures.isEmpty()) {
                "Biblioteca sincronizada e reorganizada: ${artists.sumOf { it.albums.size }} álbuns e ${artists.sumOf { a -> a.albums.sumOf { it.tracks.size } }} faixas."
            } else "Não foi possível atualizar ${preview.failures.joinToString()}. Esses catálogos foram mantidos. Verifique a conexão e o acesso público às pastas."
            CatalogUpdate(artists, message + if(preview.unverifiedDates>0) " Não foi possível confirmar a data de ${preview.unverifiedDates} faixas; não foram reutilizadas datas antigas na atualização manual." else "")
        }
    }

    // First install and manual refresh are already authorized by their user actions.
    internal suspend fun refresh(filter: String): CatalogUpdate = apply(check(filter,forceTags=true), filter)

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
        albums.forEach { a -> put(JSONObject().put("id", a.id).put("title", a.title).put("order", a.albumOrder ?: JSONObject.NULL).put("dates",JSONObject(a.releaseDates)).put("folder", a.folderId).put("cover", a.coverId ?: JSONObject.NULL).put("tracks", JSONArray().apply { a.tracks.forEach { (id, name) -> put(JSONObject().put("id", id).put("name", name)) } })) }
    }
    private fun decode(array: JSONArray): List<RawAlbum> = (0 until array.length()).map { i ->
        val a = array.getJSONObject(i); val tracks = a.getJSONArray("tracks")
        RawAlbum(a.getString("id"), a.getString("title"), a.getString("folder"), if (a.isNull("cover")) null else a.getString("cover"), (0 until tracks.length()).map { j -> tracks.getJSONObject(j).let { it.getString("id") to it.getString("name") } }, a.optInt("order",0).takeIf { it>0 }, a.optJSONObject("dates")?.let { dates->dates.keys().asSequence().associateWith{dates.getString(it)} } ?: emptyMap())
    }
}
