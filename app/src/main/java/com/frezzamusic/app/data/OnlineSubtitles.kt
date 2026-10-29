package com.frezzamusic.app.data

import com.frezzamusic.app.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

internal data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)
internal data class OnlineSubtitle(val id: String, val language: String) {
    val label: String get() = Locale.forLanguageTag(language).getDisplayName(Locale("pt", "BR")).replaceFirstChar { it.titlecase() }
}
internal object SubtitleFormat {
    private val stamp = Regex("(\\d{1,3}):(\\d{2}):(\\d{2})[,.](\\d{1,3})")
    fun parse(srt: String): List<SubtitleCue> = srt.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        .split(Regex("\n[ \\t]*\n")).mapNotNull { block ->
            val lines = block.lines()
            val index = lines.indexOfFirst { "-->" in it }
            if (index < 0) return@mapNotNull null
            val times = stamp.findAll(lines[index]).toList()
            if (times.size != 2) return@mapNotNull null
            fun ms(i: Int): Long = times[i].groupValues.let { (it[1].toLong()*3600 + it[2].toLong()*60 + it[3].toLong())*1000 + it[4].padEnd(3, '0').toLong() }
            val text = lines.drop(index + 1).joinToString("\n").replace(Regex("<[^>]+>"), "").trim()
            val start = ms(0); val end = ms(1)
            if (end <= start || text.isEmpty()) null else SubtitleCue(start, end, text)
        }.sortedBy { it.startMs }
    fun current(cues: List<SubtitleCue>, position: Long) = cues.indexOfLast { position >= it.startMs && position < it.endMs }
    fun key(name: String): String = Normalizer.normalize(DriveFolderParser.displayTitle(name), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
    fun match(name: String, title: String): String? {
        val m = Regex("(?i)^(.+)[_. -]([a-z]{2,3}(?:[-_][a-z]{2})?)\\.srt$").matchEntire(name) ?: return null
        return m.groupValues[2].replace('_', '-').lowercase(Locale.ROOT).takeIf { key(m.groupValues[1]) == key(title) }
    }
}

/** Resolve on each song opening so newly added languages and albums appear without an APK update. */
internal object OnlineSubtitles {
    private val roots = mapOf("thefrezza" to "1XnhjEIDYNWk0iyWL1_NysSXfYNapz_7t", "solasias" to "1kyt6f3sMsG9oPCpxG-mNDVcp8V4jlNHm")
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    private fun read(url: String, limit: Long): String {
        client.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Não foi possível carregar as legendas.")
            val bytes = response.body?.byteStream()?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    if (output.size() > limit) throw IOException("Arquivo de legendas grande demais.")
                }
                output.toByteArray() } ?: throw IOException("Resposta vazia.")
            if (bytes.size > limit) throw IOException("Arquivo de legendas grande demais.")
            val text = bytes.toString(Charsets.UTF_8)
            if (text.trimStart().startsWith("<!DOCTYPE", true) || text.trimStart().startsWith("<html", true)) {
                if (!url.contains("embeddedfolderview")) throw IOException("A legenda não está disponível publicamente.")
            }
            return text
        }
    }
    private fun list(id: String) = DriveFolderParser.parse(read("https://drive.google.com/embeddedfolderview?id=$id&hl=pt-BR&_=${System.currentTimeMillis()}", 2*1024*1024L))
    suspend fun available(track: Track): List<OnlineSubtitle> = withContext(Dispatchers.IO) {
        val root = roots[track.artist.lowercase(Locale.ROOT)] ?: return@withContext emptyList()
        val folders = list(root).filter { it.folder && SubtitleFormat.key(it.name) == SubtitleFormat.key(track.album) }
        if (folders.size != 1) return@withContext emptyList()
        list(folders.single().id).filter { !it.folder }.mapNotNull { file -> SubtitleFormat.match(file.name, track.title)?.let { OnlineSubtitle(file.id, it) } }
            .sortedBy { it.label }.distinctBy { it.language }
    }
    suspend fun load(subtitle: OnlineSubtitle): List<SubtitleCue> = withContext(Dispatchers.IO) {
        SubtitleFormat.parse(read("https://drive.google.com/uc?export=download&id=${subtitle.id}", 1024*1024L)).also {
            if (it.isEmpty()) throw IOException("A legenda não contém marcações SRT válidas.")
        }
    }
}
