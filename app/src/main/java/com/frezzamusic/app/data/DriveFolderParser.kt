package com.frezzamusic.app.data

import org.jsoup.Jsoup
import java.io.IOException
import java.util.Locale

internal data class DriveEntry(val id: String, val name: String, val folder: Boolean)

/** Public folder listing only; never treats a login/error page as an empty catalog. */
internal object DriveFolderParser {
    fun parse(html: String): List<DriveEntry> {
        val doc = Jsoup.parse(html)
        if (doc.selectFirst("body.flip-embedded #flip-contents .flip-entries") == null) {
            throw IOException("A pasta não forneceu uma listagem pública válida.")
        }
        val rows = doc.select(".flip-entry")
        // Embedded views do not expose a documented pagination contract.
        if (rows.size >= 100) throw IOException("Pasta grande demais para confirmar a listagem completa.")
        return rows.map { row ->
            val id = row.id().removePrefix("entry-")
            val name = row.selectFirst(".flip-entry-title")?.text().orEmpty()
            val href = row.selectFirst(".flip-entry-info a")?.attr("href").orEmpty()
            if (!id.matches(Regex("[A-Za-z0-9_-]+")) || name.isBlank() ||
                !href.startsWith("https://drive.google.com/")) throw IOException("Item inválido na pasta pública.")
            DriveEntry(id, name, href.contains("/drive/folders/"))
        }.distinctBy { it.id }.sortedWith(compareBy<DriveEntry> { number(it.name) }.thenBy { it.name.lowercase(Locale.ROOT) })
    }

    private fun number(name: String) = Regex("^\\s*(\\d+)").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
    fun displayTitle(name: String) = name.replace(Regex("^\\s*\\d+\\s*(?:[-.]\\s*|\\s+)"), "").trim()

    fun album(folder: DriveEntry, files: List<DriveEntry>, previous: RawAlbum?): RawAlbum {
        val tracks = files.filter { !it.folder && it.name.endsWith(".mp3", ignoreCase = true) }
        val images = files.filter { !it.folder && it.name.substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("png", "jpg", "jpeg", "webp") }
        val cover = images.firstOrNull { it.id == previous?.coverId }
            ?: images.firstOrNull { it.name.contains("capa", true) || it.name.contains("cover", true) || it.name.contains("front", true) }
            ?: images.firstOrNull()
        return RawAlbum(previous?.id ?: "drive:${folder.id}", displayTitle(folder.name), folder.id, cover?.id, tracks.map { it.id to it.name })
    }
}
