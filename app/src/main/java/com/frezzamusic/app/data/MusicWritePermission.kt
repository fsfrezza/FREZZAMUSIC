package com.frezzamusic.app.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

/**
 * Write access is requested lazily: never request it while selecting a music
 * source for reading. Persist granted tree access for subsequent edits.
 */
class MusicWritePermission(private val context: Context) {
    private val resolver: ContentResolver get() = context.contentResolver

    fun hasPersistedWriteAccess(treeUri: Uri): Boolean =
        resolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isWritePermission
        }

    fun rememberGrant(uri: Uri, resultFlags: Int): Boolean {
        val flags = resultFlags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0) return false
        return try {
            resolver.takePersistableUriPermission(uri, flags)
            hasPersistedWriteAccess(uri)
        } catch (_: SecurityException) {
            false
        }
    }

    fun writableFileInTree(treeUri: Uri, targetUri: Uri): DocumentFile? {
        if (!hasPersistedWriteAccess(treeUri)) return null
        return try {
            val targetId = DocumentsContract.getDocumentId(targetUri)
            val treeId = DocumentsContract.getTreeDocumentId(treeUri)
            if (targetId != treeId && !targetId.startsWith(treeId + "/")) return null
            val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, targetId)
            DocumentFile.fromSingleUri(context, fileUri)?.takeIf { it.isFile && it.canWrite() }
        } catch (_: Exception) { null }
    }
}
