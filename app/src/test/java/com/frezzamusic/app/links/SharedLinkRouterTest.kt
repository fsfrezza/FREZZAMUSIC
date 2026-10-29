package com.frezzamusic.app.links
import org.junit.Assert.*
import org.junit.Test
class SharedLinkRouterTest {
    private val song = SharedContent.Target("solasias", "track", "ABC")
    @Test fun artistAppHandlesLocallyWhenBothInstalled() {
        assertFalse(SharedLinkRouter.dispatch(song, "com.frezzamusic.solasias", { error("Must not query") }, { error("Must not launch") }))
    }
    @Test fun mainForwardsToArtistWhenBothInstalled() {
        val launched = mutableListOf<String>()
        assertTrue(SharedLinkRouter.dispatch(song, SharedLinkRouter.MAIN, { true }, { launched.add(it); true }))
        assertEquals(listOf("com.frezzamusic.solasias"), launched)
    }
    @Test fun mainHandlesLocallyWhenArtistMissing() {
        assertFalse(SharedLinkRouter.dispatch(song, SharedLinkRouter.MAIN, { false }, { error("Must not launch") }))
    }
    @Test fun launchRaceFallsBackToMain() {
        val launched = mutableListOf<String>()
        assertTrue(SharedLinkRouter.dispatch(song, "com.frezzamusic.thefrezza", { true }, { launched.add(it); it == SharedLinkRouter.MAIN }))
        assertEquals(listOf("com.frezzamusic.solasias", SharedLinkRouter.MAIN), launched)
    }
    @Test fun theFrezzaHasTheSamePriorityForSongsAndAlbums() {
        for (kind in listOf("album", "track")) assertEquals(listOf("com.frezzamusic.thefrezza", SharedLinkRouter.MAIN), SharedLinkRouter.packages(SharedContent.Target("thefrezza",kind,"ABC")))
    }
    @Test fun noCompatibleAppLeavesCurrentActivityToHandleTheError() {
        assertFalse(SharedLinkRouter.dispatch(song, "com.frezzamusic.thefrezza", { false }, { error("Must not launch") }))
    }
}
