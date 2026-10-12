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
        val seed = FrezzaDriveCatalog().rawArtists().getValue("theFrezza").single { it.id == "frezza" }
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

    @Test(expected = IOException::class) fun rejectsLoginPage() {
        DriveFolderParser.parse("<html><body>Sign in</body></html>")
    }

    @Test(expected = IOException::class) fun rejectsPotentiallyTruncatedFolders() {
        val rows = (1..100).joinToString("") { "<div class='flip-entry' id='entry-$it'><div class='flip-entry-info'><a href='https://drive.google.com/file/d/$it/view'><div class='flip-entry-title'>$it.mp3</div></a></div></div>" }
        DriveFolderParser.parse("<body class='flip-embedded'><div id='flip-contents'><div class='flip-entries'>$rows</div></div></body>")
    }
}
