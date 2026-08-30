package com.javelinco.localmusicplayer.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsTimelineTest {
    @Test
    fun activeLineUsesLatestTimestampAtOrBeforePlayback() {
        val lyrics = LyricsDocument(
            lines = listOf(
                LyricLine(1_000, "One"),
                LyricLine(2_500, "Two"),
                LyricLine(4_000, "Three"),
            ),
            synchronized = true,
        )

        assertNull(lyrics.activeLineIndex(999))
        assertEquals(0, lyrics.activeLineIndex(1_000))
        assertEquals(1, lyrics.activeLineIndex(3_999))
        assertEquals(2, lyrics.activeLineIndex(8_000))
    }

    @Test
    fun plainLyricsHaveNoActiveLine() {
        val lyrics = LyricsDocument(listOf(LyricLine(null, "Words")), synchronized = false)

        assertNull(lyrics.activeLineIndex(10_000))
    }
}
