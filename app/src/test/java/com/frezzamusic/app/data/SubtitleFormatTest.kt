package com.frezzamusic.app.data

import org.junit.Assert.*
import org.junit.Test

class SubtitleFormatTest {
    @Test fun parsesBomCrLfMultilineAndEndTimes() {
        val cues = SubtitleFormat.parse("\uFEFF1\r\n00:00:01,250 --> 00:00:03,000\r\n<b>Olá</b>\r\nMundo\r\n\r\n2\r\n00:00:04.500 --> 00:00:06.000\r\nFim")
        assertEquals(2, cues.size)
        assertEquals("Olá\nMundo", cues[0].text)
        assertEquals(-1, SubtitleFormat.current(cues, 1249))
        assertEquals(0, SubtitleFormat.current(cues, 1250))
        assertEquals(-1, SubtitleFormat.current(cues, 3000))
        assertEquals(1, SubtitleFormat.current(cues, 5000))
        assertEquals(0, SubtitleFormat.current(cues, 2000)) // seek backwards
        assertEquals(-1, SubtitleFormat.current(cues, 6000))
    }
    @Test fun matchesNumberedTitlesWithoutConfusingBonusVersions() {
        assertEquals("en", SubtitleFormat.match("11 - Três, Três, Quatro. Vai!_en.srt", "Três, Três, Quatro. Vai!"))
        assertEquals("pt-br", SubtitleFormat.match("01 - Song_pt-BR.srt", "Song"))
        assertNull(SubtitleFormat.match("13 - Frezza (Bônus - versão brasileira)_en.srt", "Frezza"))
        assertEquals(SubtitleFormat.key("01 - Frezza"), SubtitleFormat.key("Frezza"))
        assertEquals(SubtitleFormat.key("07 - Свод небесный"), SubtitleFormat.key("Свод небесный"))
    }
    @Test fun ignoresInvalidCues() {
        assertTrue(SubtitleFormat.parse("1\n00:00:04,000 --> 00:00:02,000\nInvalid").isEmpty())
        assertTrue(SubtitleFormat.parse("<html>Sign in</html>").isEmpty())
    }
}
