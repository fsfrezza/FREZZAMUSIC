package com.frezzamusic.app.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogApprovalTest {
    private val old = RawAlbum("old", "Antes", "folder", "cover", listOf("one" to "01 - Antes.mp3"))
    private fun preview(updated: List<RawAlbum>) = CatalogPreview(mapOf("theFrezza" to listOf(old)), mapOf("theFrezza" to updated), emptyList())

    @Test fun addedRemovedAndEditedAlbumsRequireApproval() {
        val edited = old.copy(title = "Depois", tracks = old.tracks + ("two" to "02 - Depois.mp3"))
        val added = RawAlbum("new", "Novo", "new-folder", null, listOf("new-track" to "01 - Novo.mp3"))
        val p = preview(listOf(edited, added))
        assertTrue(p.hasChanges)
        assertEquals("1 novos álbuns, 0 álbuns removidos e 1 álbuns alterados (faixas, nomes ou capas).", p.summary())
        assertTrue(p.approved(p.baseline, accepted = false).isEmpty())
        assertEquals(listOf(old), p.baseline.getValue("theFrezza"))
        assertEquals(listOf(edited, added), p.approved(p.baseline, accepted = true).getValue("theFrezza"))
        assertTrue(preview(emptyList()).hasChanges)
    }

    @Test fun unchangedCatalogDoesNotPrompt() {
        assertFalse(preview(listOf(old)).hasChanges)
        val unavailable = CatalogPreview(mapOf("theFrezza" to listOf(old)), emptyMap(), listOf("theFrezza"))
        assertFalse(unavailable.hasChanges)
        assertTrue(unavailable.approved(unavailable.baseline, true).isEmpty())
    }

    @Test fun onlySuccessfulArtistsCanBeApplied() {
        val current = mapOf("theFrezza" to listOf(old), "Solasias" to listOf(old.copy(id = "solasias")))
        val p = CatalogPreview(current, mapOf("theFrezza" to emptyList()), listOf("Solasias"))
        assertEquals(setOf("theFrezza"), p.approved(current, true).keys)
        assertEquals(1, current.getValue("Solasias").size)
    }

    @Test fun freshInstallationHasNoOldAlbumFallback() {
        val p = CatalogPreview(mapOf("theFrezza" to emptyList()), mapOf("theFrezza" to listOf(old)), emptyList())
        assertTrue(p.baseline.getValue("theFrezza").isEmpty())
        assertTrue(p.hasChanges)
    }

    @Test(expected = IOException::class) fun staleConfirmationCannotOverwriteNewCatalog() {
        preview(emptyList()).approved(mapOf("theFrezza" to listOf(old.copy(title = "Mais recente"))), true)
    }

    @Test fun checksExactlyOnceAfterThirtySeconds() = runTest {
        var checks = 0
        launch { CatalogAutoCheck.afterOpen { checks++ } }
        advanceTimeBy(29_999); runCurrent(); assertEquals(0, checks)
        advanceTimeBy(1); runCurrent(); assertEquals(1, checks)
        advanceTimeBy(60_000); runCurrent(); assertEquals(1, checks)
    }

    @Test fun disablingOrLeavingForegroundCancelsPendingCheckAndReopenRestartsTimer() = runTest {
        var checks = 0
        val firstOpening = launch { CatalogAutoCheck.afterOpen { checks++ } }
        advanceTimeBy(15_000); firstOpening.cancel(); runCurrent()
        advanceTimeBy(30_000); runCurrent(); assertEquals(0, checks)
        launch { CatalogAutoCheck.afterOpen { checks++ } }
        advanceTimeBy(29_999); runCurrent(); assertEquals(0, checks)
        advanceTimeBy(1); runCurrent(); assertEquals(1, checks)
    }
}
