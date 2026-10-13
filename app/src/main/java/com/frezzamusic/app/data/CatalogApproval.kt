package com.frezzamusic.app.data

import kotlinx.coroutines.delay
import java.io.IOException

internal data class CatalogPreview(
    val baseline: Map<String, List<RawAlbum>>,
    val proposed: Map<String, List<RawAlbum>>,
    val failures: List<String>
) {
    val changedArtists: List<String> get() = proposed.keys.filter { name ->
        baseline[name].orEmpty().sortedBy { it.folderId } != proposed.getValue(name).sortedBy { it.folderId }
    }
    val hasChanges: Boolean get() = changedArtists.isNotEmpty()
    fun summary(): String {
        var added = 0; var removed = 0; var changed = 0
        for (name in changedArtists) {
            val before = baseline[name].orEmpty().associateBy { it.folderId }
            val after = proposed.getValue(name).associateBy { it.folderId }
            added += (after.keys - before.keys).size
            removed += (before.keys - after.keys).size
            changed += (before.keys intersect after.keys).count { before[it] != after[it] }
        }
        return "$added novos álbuns, $removed álbuns removidos e $changed álbuns alterados (faixas, nomes ou capas)."
    }

    /** Rejecting changes is a no-op; a stale confirmation cannot overwrite a newer refresh. */
    fun approved(current: Map<String, List<RawAlbum>>, accepted: Boolean): Map<String, List<RawAlbum>> {
        if (!accepted) return emptyMap()
        if (baseline != current) throw IOException("A biblioteca mudou desde a verificação. Verifique novamente.")
        return proposed
    }
}

internal object CatalogAutoCheck {
    const val DELAY_MS = 30_000L
    suspend fun afterOpen(check: suspend () -> Unit) {
        delay(DELAY_MS)
        check()
    }
}
