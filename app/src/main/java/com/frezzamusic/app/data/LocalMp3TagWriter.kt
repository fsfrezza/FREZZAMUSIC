package com.frezzamusic.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.mpatric.mp3agic.ID3v2
import com.mpatric.mp3agic.ID3v24Tag
import com.mpatric.mp3agic.Mp3File
import java.io.File

/**
 * Edits MP3 tags using a temporary copy. Never writes to remote media.
 * For other audio formats, fails explicitly rather than modifying bytes.
 */
class LocalMp3TagWriter(private val context: Context) {
    fun save(uri: Uri, changes: Map<String, String>) {
        val name = uri.lastPathSegment.orEmpty().lowercase()
        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty()
        if (!name.endsWith(".mp3") && mime != "audio/mpeg") {
            throw IllegalArgumentException("Gravação de tags disponível apenas para MP3 nesta versão.")
        }
        val source = File.createTempFile("frezzamusic-source-", ".mp3", context.cacheDir)
        val edited = File.createTempFile("frezzamusic-edited-", ".mp3", context.cacheDir)
        val backup = File.createTempFile("frezzamusic-backup-", ".mp3", context.cacheDir)
        try {
            resolver.openInputStream(uri)?.use { input -> source.outputStream().use(input::copyTo) }
                ?: throw IllegalStateException("Não foi possível ler o arquivo original.")
            val mp3 = Mp3File(source.absolutePath)
            val tag: ID3v2 = mp3.id3v2Tag ?: ID3v24Tag()
            changes["title"]?.let { tag.title = it }
            changes["artist"]?.let { tag.artist = it }
            changes["album"]?.let { tag.album = it }
            changes["genre"]?.let { tag.genreDescription = it }
            changes["trackNumber"]?.let { tag.track = it }
            mp3.id3v2Tag = tag
            edited.delete()
            mp3.save(edited.absolutePath)
            // Verify the staged file before touching the original.
            val verified = Mp3File(edited.absolutePath)
            val resultTag = verified.id3v2Tag ?: throw IllegalStateException("Tag ID3 não foi gravada.")
            if (changes["title"] != null && resultTag.title != changes["title"])
                throw IllegalStateException("Falha na verificação do título.")
            source.inputStream().use { input -> backup.outputStream().use(input::copyTo) }
            try {
                resolver.openFileDescriptor(uri, "rwt")?.use { descriptor ->
                    File(edited.absolutePath).inputStream().use { input ->
                        java.io.FileOutputStream(descriptor.fileDescriptor).use { output ->
                            input.copyTo(output)
                            output.flush()
                            output.fd.sync()
                        }
                    }
                } ?: throw IllegalStateException("Arquivo sem autorização de escrita.")
            } catch (error: Exception) {
                try {
                    resolver.openFileDescriptor(uri, "rwt")?.use { descriptor ->
                        backup.inputStream().use { input ->
                            java.io.FileOutputStream(descriptor.fileDescriptor).use { output ->
                                input.copyTo(output)
                                output.flush()
                            }
                        }
                    }
                } catch (rollback: Exception) {
                    throw IllegalStateException("Falha na gravação e na recuperação. Preserve o arquivo de origem e verifique o armazenamento.", rollback)
                }
                throw error
            }
        } finally {
            source.delete()
            edited.delete()
            backup.delete()
        }
    }
}
