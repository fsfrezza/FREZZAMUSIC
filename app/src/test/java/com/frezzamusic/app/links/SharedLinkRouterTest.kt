package com.frezzamusic.app.links
import org.junit.Assert.*
import org.junit.Test
class SharedLinkRouterTest {
    private val song = SharedContent.Target("solasias", "track", "ABC")
    @Test fun bandForwardsToMainWhenBothInstalled() {
        val launched = mutableListOf<String>()
        assertTrue(SharedLinkRouter.dispatch(song, "com.frezzamusic.solasias", { true }, { launched.add(it); true }))
        assertEquals(listOf(SharedLinkRouter.MAIN), launched)
    }
    @Test fun existingMainHandlesLocallyWithoutLaunchingAnotherApp() {
        assertFalse(SharedLinkRouter.dispatch(song, SharedLinkRouter.MAIN, { error("Must not query") }, { error("Must not forward") }))
    }
    @Test fun bandHandlesLocallyWhenMainMissing() {
        assertFalse(SharedLinkRouter.dispatch(song, "com.frezzamusic.solasias", { false }, { error("Must not launch") }))
    }
    @Test fun launchRaceFallsBackToCorrectBand() {
        val launched = mutableListOf<String>()
        assertTrue(SharedLinkRouter.dispatch(song, "com.frezzamusic.thefrezza", { true }, { launched.add(it); it != SharedLinkRouter.MAIN }))
        assertEquals(listOf(SharedLinkRouter.MAIN, "com.frezzamusic.solasias"), launched)
    }
    @Test fun noCompatibleAppLeavesCurrentActivityToHandleTheError() {
        assertFalse(SharedLinkRouter.dispatch(song, "com.frezzamusic.thefrezza", { false }, { error("Must not launch") }))
    }
}
