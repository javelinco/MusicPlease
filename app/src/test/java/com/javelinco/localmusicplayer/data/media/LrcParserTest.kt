package com.javelinco.localmusicplayer.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesMultipleTimestampsOffsetAndCentiseconds() {
        val lyrics = LrcParser.parse(
            "[offset:+250]\n[00:01.50][00:03.000]Hello\n[00:04]World".encodeToByteArray(),
        )!!

        assertTrue(lyrics.synchronized)
        assertEquals(listOf(1_750L, 3_250L, 4_250L), lyrics.lines.map { it.timeMs })
        assertEquals(listOf("Hello", "Hello", "World"), lyrics.lines.map { it.text })
    }

    @Test
    fun lrcWithoutUsableTimestampsFallsBackToPlainLyrics() {
        val lyrics = LrcParser.parse("First line\n\nSecond line".encodeToByteArray())!!

        assertFalse(lyrics.synchronized)
        assertEquals(listOf("First line", "Second line"), lyrics.lines.map { it.text })
        assertEquals(listOf(null, null), lyrics.lines.map { it.timeMs })
    }

    @Test
    fun ignoresMetadataAndClampsNegativeOffsetAtStart() {
        val lyrics = LrcParser.parse(
            "\uFEFF[ar:Artist]\n[ti:Title]\n[offset:-2000]\n[00:01.00]Opening".encodeToByteArray(),
        )!!

        assertEquals(0L, lyrics.lines.single().timeMs)
        assertEquals("Opening", lyrics.lines.single().text)
    }

    @Test
    fun emptyAndOversizedInputReturnsNoLyrics() {
        assertNull(LrcParser.parse(" \n\t".encodeToByteArray()))
        assertNull(LrcParser.parse(ByteArray(2 * 1024 * 1024 + 1) { 'x'.code.toByte() }))
    }
}
