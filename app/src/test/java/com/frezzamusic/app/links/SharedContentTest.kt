package com.frezzamusic.app.links
import org.junit.Assert.*
import org.junit.Test
class SharedContentTest {
    @Test fun acceptsBothArtistsAndKinds() {
        for (artist in listOf("solasias", "thefrezza")) for (kind in listOf("track", "album")) {
            assertEquals(SharedContent.Target(artist,kind,"drive_ID-12"), SharedContent.parseUrl(
                "https://fsfrezza.github.io/FREZZAMUSIC/share/?artist=$artist&kind=$kind&id=drive_ID-12"))
        }
    }
    @Test fun explicitAppButtonUsesSameIdentity() {
        assertEquals(SharedContent.Target("solasias","track","ABC"), SharedContent.parseUrl(
            "frezzamusic://open?artist=solasias&kind=track&id=ABC"))
    }
    @Test fun rejectsUntrustedOrAmbiguousLinks() {
        val good="https://fsfrezza.github.io/FREZZAMUSIC/share/?artist=solasias&kind=track&id=ABC"
        for (bad in listOf(null, "", good.replace("https", "http"), good.replace("fsfrezza.github.io", "evil.example"),
            good.replace("/share/", "/other/"), good.replace("solasias", "unknown"), good+"&id=DEF",
            good.replace("id=ABC", "id=..%2Fprivate"),good.replace("id=ABC", "id=%ZZ"),good+"#fragment",
            good.replace("fsfrezza.github.io", "user@fsfrezza.github.io"))) assertNull(bad, SharedContent.parseUrl(bad))
    }
    @Test fun shortCodesRoundTripAndKeepIdentity() {
        val code = SharedContent.shortCode("solasias", "track", "ABC")
        assertEquals("st-bJHVEeD8uiw94qB", code)
        val target = SharedContent.parseUrl("https://fsfrezza.github.io/FREZZAMUSIC/s/?c=$code")!!
        assertTrue(target.short)
        assertTrue(SharedContent.matches(target, "ABC"))
        assertFalse(SharedContent.matches(target, "DEF"))
        assertEquals(target, SharedContent.parseUrl("frezzamusic://open?c=$code"))
        assertNull(SharedContent.parseUrl("https://fsfrezza.github.io/FREZZAMUSIC/s/?c=$code&id=ABC"))
        assertNull(SharedContent.parseUrl("https://fsfrezza.github.io/FREZZAMUSIC/s/?c=not-a-code"))
        assertNotEquals(code, SharedContent.shortCode("thefrezza", "track", "ABC"))
        assertNotEquals(code, SharedContent.shortCode("solasias", "album", "ABC"))
    }
}
