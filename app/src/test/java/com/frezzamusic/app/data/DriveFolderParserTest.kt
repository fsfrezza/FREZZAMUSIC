package com.frezzamusic.app.data

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class DriveFolderParserTest {
    private fun fixture(name: String) = javaClass.getResource("/$name.html")!!.readText()

    @Test fun discoversNewAlbumAndItsElevenTracks() {
        val root = DriveFolderParser.parse(fixture("root-frezza"))
        assertEquals(14, root.count { it.folder })
        val folder = root.single { it.name == "14 - Kaleidophrenia" }
        val album = DriveFolderParser.album(folder, DriveFolderParser.parse(fixture("newalbum")), null)
        assertEquals("Kaleidophrenia", album.title)
        assertEquals(11, album.tracks.size)
        assertEquals("01 - Out Of Focus.mp3", album.tracks.first().second)
        assertEquals("11 - Nothing Looked The Same.mp3", album.tracks.last().second)
        assertEquals("1spFTCX9-s-RaZpXlM63meAJVfd6seYeX", album.coverId)
        assertFalse(album.tracks.any { it.second.endsWith(".pdn") })
    }

    @Test fun preservesExistingAlbumAndTrackIdentity() {
        val files = DriveFolderParser.parse(fixture("drive-folder"))
        val seed = RawAlbum("frezza", "Frezza", "1xL2kza5-2I7cYn8z8FAnZsH0rkjgFckU", null, files.filter { it.name.endsWith(".mp3") }.map { it.id to it.name })
        val folder = DriveEntry(seed.folderId, "01 - Frezza", true)
        val updated = DriveFolderParser.album(folder, DriveFolderParser.parse(fixture("drive-folder")), seed)
        assertEquals(seed.id, updated.id)
        assertEquals(seed.tracks.map { it.first }, updated.tracks.map { it.first })
        assertEquals(album("theFrezza", seed).tracks.map { it.id }, album("theFrezza", updated).tracks.map { it.id })
    }

    @Test fun readsBothArtistRootsAndUnicodeTitles() {
        val root = DriveFolderParser.parse(fixture("root-solasias"))
        assertEquals(9, root.count { it.folder })
        assertEquals("Gênesis", DriveFolderParser.displayTitle(root.first().name))
        assertEquals("The Do-It-All Democrat", DriveFolderParser.displayTitle("07 - The Do-It-All Democrat"))
        assertEquals("The Frostborn Oars", DriveFolderParser.displayTitle("11. The Frostborn Oars"))
    }

    @Test fun prefersOriginalPngOverPreviouslySelectedJpeg() {
        val folder = DriveEntry("album", "01 - Album", true)
        val files = listOf(DriveEntry("jpeg", "cover.jpg", false), DriveEntry("png", "capa.PNG", false))
        val previous = RawAlbum("stable", "Album", folder.id, "jpeg", emptyList())
        val updated = DriveFolderParser.album(folder, files, previous)
        assertEquals("png", updated.coverId)
        assertEquals("stable", updated.id)
        assertEquals("https://drive.google.com/uc?export=download&id=png", album("theFrezza", updated).artwork)
        assertEquals("jpeg", DriveFolderParser.album(folder, files.take(1), previous).coverId)
    }

    @Test(expected = IOException::class) fun rejectsLoginPage() {
        DriveFolderParser.parse("<html><body>Sign in</body></html>")
    }

    @Test(expected = IOException::class) fun rejectsPotentiallyTruncatedFolders() {
        val rows = (1..100).joinToString("") { "<div class='flip-entry' id='entry-$it'><div class='flip-entry-info'><a href='https://drive.google.com/file/d/$it/view'><div class='flip-entry-title'>$it.mp3</div></a></div></div>" }
        DriveFolderParser.parse("<body class='flip-embedded'><div id='flip-contents'><div class='flip-entries'>$rows</div></div></body>")
    }
}
