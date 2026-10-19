package com.frezzamusic.app.links

import org.junit.Assert.*
import org.junit.Test
import okhttp3.HttpUrl.Companion.toHttpUrl

class ShareShortenerTest {
    @Test fun encodingPreservesTheExactSongOrAlbum() {
        for (code in listOf("st8YKAAhPWP1fbovij", "sa7hiKOpEQRCudVmIm")) {
            val canonical = "https://fsfrezza.github.io/FREZZAMUSIC/s/?c=$code"
            val request = ShortLinkRules.requestUrl(canonical).toHttpUrl()
            assertEquals("da.gd", request.host)
            assertEquals(canonical, request.queryParameter("url"))
        }
    }
    @Test fun onlyHttpsProviderResultsAreAccepted() {
        assertEquals("https://da.gd/EEH12", ShortLinkRules.accepted("https://da.gd/EEH12\n"))
        for (url in listOf("http://da.gd/EEH12", "https://da.gd.evil.org/a", "https://other.org/a", "https://da.gd/a?url=evil", "Error: unavailable")) assertNull(ShortLinkRules.accepted(url))
    }
    @Test(expected = IllegalArgumentException::class) fun arbitraryDestinationsCannotBeSubmitted() {
        ShortLinkRules.requestUrl("https://other.org/private")
    }
}
